package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.production.vo.ProductionStartReqVO;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.comfyui.WorkflowProfileService;
import com.stonewu.fusion.service.generation.GenerationModelCapabilityService;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import com.stonewu.fusion.service.generation.video.consumer.VideoGenerationConsumer;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import com.stonewu.fusion.service.storyboard.VideoComposeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ProductionRunServiceTests {

    @Mock
    private ProductionRunMapper runMapper;

    @Mock
    private ProductionStepMapper stepMapper;

    @Mock
    private ProductionTakeMapper takeMapper;

    @Mock
    private QcResultMapper qcResultMapper;

    @Mock
    private ProductionTechnicalQcService technicalQcService;

    @Mock
    private ProductionRepairAttemptMapper repairAttemptMapper;

    @Mock
    private ProductionRepairRouter repairRouter;

    @Mock
    private ProductionRepairExecutor repairExecutor;

    @Mock
    private StoryboardItemMapper storyboardItemMapper;

    @Mock
    private StoryboardService storyboardService;

    @Mock
    private VideoGenerationConsumer videoGenerationConsumer;

    @Mock
    private VideoGenerationService videoGenerationService;

    @Mock
    private VideoComposeService videoComposeService;

    @Mock
    private AiModelService aiModelService;

    @Mock
    private GenerationModelCapabilityService generationModelCapabilityService;

    @Mock
    private WorkflowProfileService workflowProfileService;

    @Mock
    private ShotReadinessService shotReadinessService;

    @Test
    void startRequiresIdempotencyKey() {
        ProductionRunService service = service();
        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setStoryboardItemId(11L);

        assertThatThrownBy(() -> service.start(request, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等键不能为空");
    }

    @Test
    void repeatedStartReturnsExistingRunWithoutSubmittingAnotherVideoTask() {
        ProductionRun existing = ProductionRun.builder()
                .id(7L)
                .userId(99L)
                .storyboardItemId(11L)
                .idempotencyKey("same-request")
                .status(ProductionRunService.RUN_WAITING_GENERATION)
                .build();
        when(runMapper.selectOne(any())).thenReturn(existing);
        when(runMapper.selectById(7L)).thenReturn(existing);
        when(stepMapper.selectOne(any())).thenReturn(null);
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of());

        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setStoryboardItemId(11L);
        request.setIdempotencyKey(" same-request ");

        ProductionRunDetail detail = service().start(request, 99L);

        org.assertj.core.api.Assertions.assertThat(detail.getRun().getId()).isEqualTo(7L);
        verifyNoInteractions(storyboardService, videoGenerationConsumer, videoGenerationService);
    }

    @Test
    void startRejectsShotThatIsNotReady() {
        when(runMapper.selectOne(any())).thenReturn(null);
        StoryboardItem item = StoryboardItem.builder().id(11L).storyboardId(2L).build();
        when(storyboardService.getItemById(11L)).thenReturn(item);
        org.mockito.Mockito.doThrow(new BusinessException(400, "镜头未就绪：缺少锁定首帧"))
                .when(shotReadinessService).requireReady(eq(item), any());

        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setStoryboardItemId(11L);
        request.setIdempotencyKey("shot-11");

        assertThatThrownBy(() -> service().start(request, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("镜头未就绪：缺少锁定首帧");
        verifyNoInteractions(videoGenerationConsumer, videoGenerationService);
    }

    @Test
    void selectRejectsTakeWithoutQcPass() {
        ProductionRunService service = service();
        when(runMapper.selectById(1L)).thenReturn(ProductionRun.builder()
                .id(1L)
                .userId(99L)
                .storyboardItemId(11L)
                .build());
        when(takeMapper.selectOne(any())).thenReturn(ProductionTake.builder()
                .id(3L)
                .runId(1L)
                .storyboardItemId(11L)
                .qcStatus("FAIL")
                .build());

        assertThatThrownBy(() -> service.selectTake(1L, 3L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有 QC PASS 的候选视频才能被选中");
    }

    @Test
    void independentQcResultOverridesLegacyTakeStatus() {
        ProductionRunService service = service();
        when(runMapper.selectById(1L)).thenReturn(ProductionRun.builder()
                .id(1L)
                .userId(99L)
                .storyboardItemId(11L)
                .build());
        when(takeMapper.selectOne(any())).thenReturn(ProductionTake.builder()
                .id(3L)
                .runId(1L)
                .storyboardItemId(11L)
                .qcStatus("PASS")
                .build());
        when(qcResultMapper.selectOne(any())).thenReturn(QcResult.builder()
                .id(8L)
                .takeId(3L)
                .status(QcResult.FAIL)
                .build());

        assertThatThrownBy(() -> service.selectTake(1L, 3L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有 QC PASS 的候选视频才能被选中");
    }

    @Test
    void selectWritesStoryboardSelectedTakeIdAfterQcPass() {
        ProductionRunService service = service();
        when(runMapper.selectById(1L)).thenReturn(ProductionRun.builder()
                .id(1L)
                .userId(99L)
                .storyboardItemId(11L)
                .build());
        when(takeMapper.selectOne(any())).thenReturn(ProductionTake.builder()
                .id(3L)
                .runId(1L)
                .storyboardItemId(11L)
                .qcStatus("PASS")
                .build());
        when(storyboardService.getItemById(11L)).thenReturn(StoryboardItem.builder().id(11L).build());
        when(stepMapper.selectOne(any())).thenReturn(null);
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of());

        service.selectTake(1L, 3L, 99L);

        verify(storyboardItemMapper).update(any(), any());
        verify(runMapper).updateById(any(ProductionRun.class));
    }

    @Test
    void startSnapshotsProfileAndPinnedVersionBeforeSubmittingTask() {
        ProductionRunService service = service();
        StoryboardItem item = StoryboardItem.builder()
                .id(11L).storyboardId(21L).content("profile prompt").build();
        when(runMapper.selectOne(any())).thenReturn(null);
        when(storyboardService.getItemById(11L)).thenReturn(item);
        when(storyboardService.getById(21L)).thenReturn(
                com.stonewu.fusion.entity.storyboard.Storyboard.builder().id(21L).projectId(7L).build());
        when(aiModelService.getById(101L)).thenReturn(com.stonewu.fusion.entity.ai.AiModel.builder()
                .id(101L).modelType(3).status(1).build());
        when(workflowProfileService.resolveVideoExecution(eq(31L), any()))
                .thenReturn(new WorkflowProfileService.VideoExecutionResolution(
                        com.stonewu.fusion.entity.ai.WorkflowProfile.builder()
                                .id(31L).code("WAN_I2V_STANDARD").build(),
                        com.stonewu.fusion.entity.ai.ComfyUiWorkflow.builder().id(41L).build(),
                        com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion.builder()
                                .id(51L).workflowId(41L).published(true).build()));
        when(generationModelCapabilityService.resolveVideoCapability(any()))
                .thenReturn(new GenerationModelCapabilityService.VideoModelCapability(
                        false, false, false, false, false, java.util.List.of(),
                        false, false, 0, null, null, null, null));
        doAnswer(invocation -> {
            ProductionRun run = invocation.getArgument(0);
            run.setId(301L);
            return 1;
        }).when(runMapper).insert(any(ProductionRun.class));
        doAnswer(invocation -> {
            ProductionStep step = invocation.getArgument(0);
            step.setId(401L);
            return 1;
        }).when(stepMapper).insert(any(ProductionStep.class));
        when(videoGenerationConsumer.submitTask(any(VideoTask.class))).thenReturn("task-301");
        when(videoGenerationService.getByTaskId("task-301"))
                .thenReturn(VideoTask.builder().id(501L).taskId("task-301").build());
        when(runMapper.selectById(301L)).thenAnswer(invocation -> {
            ProductionRun run = ProductionRun.builder().id(301L).userId(99L)
                    .storyboardItemId(11L).status(ProductionRunService.RUN_WAITING_GENERATION).build();
            return run;
        });
        when(stepMapper.selectOne(any())).thenReturn(ProductionStep.builder()
                .id(401L).runId(301L).videoTaskId(501L).build());
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of());
        when(videoGenerationService.getById(501L)).thenReturn(VideoTask.builder().id(501L).build());

        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setStoryboardItemId(11L);
        request.setIdempotencyKey("profile-run");
        request.setModelId(101L);
        request.setWorkflowProfileId(31L);

        service.start(request, 99L);

        ArgumentCaptor<ProductionStep> stepCaptor = ArgumentCaptor.forClass(ProductionStep.class);
        verify(stepMapper).insert(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getWorkflowProfileId()).isEqualTo(31L);
        assertThat(stepCaptor.getValue().getWorkflowVersionId()).isEqualTo(51L);
        assertThat(stepCaptor.getValue().getInputSnapshot()).contains("WAN_I2V_STANDARD", "51");

        ArgumentCaptor<VideoTask> taskCaptor = ArgumentCaptor.forClass(VideoTask.class);
        verify(videoGenerationConsumer).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getWorkflowVersionId()).isEqualTo(51L);
    }

    @Test
    void repairIsIdempotentAfterThePlanHasBeenSubmitted() {
        ProductionRun run = ProductionRun.builder()
                .id(701L).userId(99L).status(ProductionRunService.RUN_FAILED)
                .failureCode("VIDEO_TASK_FAILED").failureMessage("ComfyUI offline").build();
        ProductionStep step = ProductionStep.builder()
                .id(702L).runId(701L).videoTaskId(703L).status("FAILED").attempt(1).build();
        ProductionRepairAttempt attempt = ProductionRepairAttempt.builder()
                .id(704L).runId(701L).sourceStepId(702L).attemptNo(1)
                .status(ProductionRepairRouter.PLANNED).route(ProductionRepairRouter.RETRY_SAME_WORKFLOW)
                .build();
        VideoTask replacement = VideoTask.builder().id(705L).taskId("replacement-705").build();

        when(runMapper.selectById(701L)).thenReturn(run);
        when(stepMapper.selectOne(any())).thenReturn(step);
        when(repairAttemptMapper.selectList(any())).thenReturn(java.util.List.of(attempt));
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of());
        when(qcResultMapper.selectList(any())).thenReturn(java.util.List.of());
        when(videoGenerationService.getById(any())).thenReturn(replacement);
        doAnswer(invocation -> {
            attempt.setStatus(ProductionRepairExecutor.SUBMITTED);
            step.setVideoTaskId(705L);
            step.setExecutionRef("replacement-705");
            step.setStatus("SUBMITTED");
            return new ProductionRepairExecutor.ExecutionResult(
                    VideoTask.builder().id(703L).build(), replacement, "replacement-705");
        }).when(repairExecutor).execute(eq(run), eq(step), eq(attempt));

        ProductionRunDetail first = service().repair(701L, 99L);
        ProductionRunDetail second = service().repair(701L, 99L);

        assertThat(first.getRun().getStatus()).isEqualTo(ProductionRunService.RUN_WAITING_GENERATION);
        assertThat(first.getRun().getFailureCode()).isNull();
        assertThat(second.getRun().getStatus()).isEqualTo(ProductionRunService.RUN_WAITING_GENERATION);
        verify(repairExecutor).execute(eq(run), eq(step), eq(attempt));
    }

    @Test
    void startFallsBackToGeneratedImageForFirstFrameLikeReadinessGate() {
        ProductionRunService service = service();
        StoryboardItem item = StoryboardItem.builder()
                .id(11L).storyboardId(21L).content("fallback prompt")
                .generatedImageUrl("https://cdn.example.com/generated.png")
                .build();
        when(runMapper.selectOne(any())).thenReturn(null);
        when(storyboardService.getItemById(11L)).thenReturn(item);
        when(storyboardService.getById(21L)).thenReturn(
                com.stonewu.fusion.entity.storyboard.Storyboard.builder().id(21L).projectId(7L).build());
        when(aiModelService.getById(101L)).thenReturn(com.stonewu.fusion.entity.ai.AiModel.builder()
                .id(101L).modelType(3).status(1).build());
        when(generationModelCapabilityService.resolveVideoCapability(any()))
                .thenReturn(new GenerationModelCapabilityService.VideoModelCapability(
                        true, false, false, false, false, java.util.List.of(),
                        false, false, 1, null, null, null, null));
        doAnswer(invocation -> {
            ProductionRun run = invocation.getArgument(0);
            run.setId(301L);
            return 1;
        }).when(runMapper).insert(any(ProductionRun.class));
        doAnswer(invocation -> {
            ProductionStep step = invocation.getArgument(0);
            step.setId(401L);
            return 1;
        }).when(stepMapper).insert(any(ProductionStep.class));
        when(videoGenerationConsumer.submitTask(any(VideoTask.class))).thenReturn("task-301");
        when(videoGenerationService.getByTaskId("task-301"))
                .thenReturn(VideoTask.builder().id(501L).taskId("task-301").build());
        when(runMapper.selectById(301L)).thenReturn(ProductionRun.builder()
                .id(301L).userId(99L).storyboardItemId(11L)
                .status(ProductionRunService.RUN_WAITING_GENERATION).build());
        when(stepMapper.selectOne(any())).thenReturn(ProductionStep.builder()
                .id(401L).runId(301L).videoTaskId(501L).build());
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of());
        when(videoGenerationService.getById(501L)).thenReturn(VideoTask.builder().id(501L).build());

        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setStoryboardItemId(11L);
        request.setIdempotencyKey("fallback-run");
        request.setModelId(101L);

        service.start(request, 99L);

        ArgumentCaptor<VideoTask> taskCaptor = ArgumentCaptor.forClass(VideoTask.class);
        verify(videoGenerationConsumer).submitTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getFirstFrameImageUrl())
                .isEqualTo("https://cdn.example.com/generated.png");
    }

    private ProductionRunService service() {
        return new ProductionRunService(
                runMapper,
                stepMapper,
                takeMapper,
                qcResultMapper,
                technicalQcService,
                repairAttemptMapper,
                repairRouter,
                repairExecutor,
                storyboardItemMapper,
                storyboardService,
                videoGenerationConsumer,
                videoGenerationService,
                videoComposeService,
                aiModelService,
                generationModelCapabilityService,
                workflowProfileService,
                shotReadinessService
        );
    }

    @org.junit.jupiter.api.Test
    void detailAttachesVideoItemPreviewFieldsToTakes() {
        ProductionRun run = ProductionRun.builder()
                .id(5L).storyboardItemId(31L).userId(99L).projectId(1L)
                .idempotencyKey("k").status("SELECTED").selectedTakeId(41L)
                .build();
        when(runMapper.selectById(5L)).thenReturn(run);
        when(stepMapper.selectOne(any())).thenReturn(ProductionStep.builder()
                .id(6L).runId(5L).stepType("GENERATE_VIDEO").status("DONE").attempt(1).videoTaskId(8L)
                .build());
        when(videoGenerationService.getById(8L)).thenReturn(VideoTask.builder().id(8L).build());
        when(takeMapper.selectList(any())).thenReturn(java.util.List.of(
                ProductionTake.builder().id(41L).runId(5L).videoItemId(301L).takeIndex(1).qcStatus("PASS").build()));
        when(videoGenerationService.listItems(8L)).thenReturn(java.util.List.of(
                com.stonewu.fusion.entity.generation.VideoItem.builder()
                        .id(301L).videoUrl("/media/videos/a.mp4").coverUrl("/media/c.jpg").status(1)
                        .build()));

        ProductionRunDetail detail = service().detail(5L, 99L);

        assertThat(detail.getTakes()).hasSize(1);
        assertThat(detail.getTakes().get(0).getVideoUrl()).isEqualTo("/media/videos/a.mp4");
        assertThat(detail.getTakes().get(0).getCoverUrl()).isEqualTo("/media/c.jpg");
    }
}

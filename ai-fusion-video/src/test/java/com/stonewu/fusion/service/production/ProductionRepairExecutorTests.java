package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import com.stonewu.fusion.service.generation.video.consumer.VideoGenerationConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRepairExecutorTests {

    @Mock
    private VideoGenerationService videoGenerationService;
    @Mock
    private VideoGenerationConsumer videoGenerationConsumer;
    @Mock
    private ProductionRepairAttemptMapper attemptMapper;
    @Mock
    private ProductionStepMapper stepMapper;

    @Test
    void submitsCopiedTaskThroughExistingConsumerAndAdvancesLineage() {
        VideoTask source = VideoTask.builder()
                .id(501L).taskId("original-task").userId(99L).projectId(7L)
                .prompt("same prompt").generateMode("image2video")
                .firstFrameImageUrl("/media/first.png").referenceImageUrls("[\"/media/ref.png\"]")
                .referenceVideoUrls("[]").referenceAudioUrls("[]")
                .ratio("16:9").resolution("1280x720").duration(5)
                .watermark(false).generateAudio(true).seed(123L).cameraFixed(false)
                .count(3).modelId(13L).workflowVersionId(15L)
                .ownerType(1).ownerId(99L).build();
        ProductionStep step = ProductionStep.builder()
                .id(801L).runId(1L).videoTaskId(501L).status("FAILED").attempt(1)
                .errorCode("VIDEO_TASK_FAILED").errorMessage("ComfyUI offline").build();
        ProductionRepairAttempt attempt = ProductionRepairAttempt.builder()
                .id(901L).runId(1L).sourceStepId(801L).attemptNo(1)
                .status(ProductionRepairRouter.PLANNED).route(ProductionRepairRouter.RETRY_SAME_WORKFLOW)
                .reason("retry").build();
        when(videoGenerationService.getById(501L)).thenReturn(source);
        when(videoGenerationConsumer.submitTask(any(VideoTask.class))).thenAnswer(invocation -> {
            VideoTask submitted = invocation.getArgument(0);
            submitted.setId(601L);
            return "replacement-task";
        });

        ProductionRepairExecutor.ExecutionResult result = executor().execute(
                ProductionRun.builder().id(1L).userId(99L).build(), step, attempt);

        assertThat(result.replacementTask().getId()).isEqualTo(601L);
        assertThat(result.replacementTask().getTaskId()).isNull();
        assertThat(result.replacementTask().getCategory()).isEqualTo("production-repair");
        assertThat(result.replacementTask().getPrompt()).isEqualTo("same prompt");
        assertThat(result.replacementTask().getWorkflowVersionId()).isEqualTo(15L);
        assertThat(attempt.getStatus()).isEqualTo(ProductionRepairExecutor.SUBMITTED);
        assertThat(attempt.getReplacementVideoTaskId()).isEqualTo(601L);
        assertThat(attempt.getReplacementExecutionRef()).isEqualTo("replacement-task");
        assertThat(step.getVideoTaskId()).isEqualTo(601L);
        assertThat(step.getExecutionRef()).isEqualTo("replacement-task");
        assertThat(step.getStatus()).isEqualTo("SUBMITTED");
        assertThat(step.getAttempt()).isEqualTo(2);
        assertThat(step.getErrorCode()).isNull();
        verify(videoGenerationConsumer).submitTask(any(VideoTask.class));
        verify(attemptMapper).updateById(attempt);
        verify(stepMapper).updateById(step);
    }

    @Test
    void failedSubmissionIsRecordedAndDoesNotAdvanceStep() {
        ProductionStep step = ProductionStep.builder().id(801L).runId(1L).videoTaskId(501L)
                .status("FAILED").attempt(1).build();
        ProductionRepairAttempt attempt = ProductionRepairAttempt.builder()
                .id(901L).runId(1L).sourceStepId(801L).attemptNo(1)
                .status(ProductionRepairRouter.PLANNED).reason("planned retry").build();
        when(videoGenerationService.getById(501L)).thenReturn(VideoTask.builder()
                .id(501L).userId(99L).count(3).build());
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(videoGenerationConsumer).submitTask(any(VideoTask.class));

        assertThatThrownBy(() -> executor().execute(
                ProductionRun.builder().id(1L).build(), step, attempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis unavailable");

        assertThat(attempt.getStatus()).isEqualTo(ProductionRepairExecutor.FAILED);
        assertThat(attempt.getReason()).contains("Redis unavailable");
        assertThat(step.getVideoTaskId()).isEqualTo(501L);
        assertThat(step.getStatus()).isEqualTo("FAILED");
        verify(attemptMapper).updateById(attempt);
    }

    @Test
    void alreadySubmittedPlanCannotBeSubmittedAgain() {
        ProductionRepairAttempt attempt = ProductionRepairAttempt.builder()
                .status(ProductionRepairExecutor.SUBMITTED).build();

        assertThatThrownBy(() -> executor().execute(
                ProductionRun.builder().id(1L).build(),
                ProductionStep.builder().videoTaskId(501L).build(), attempt))
                .isInstanceOf(com.stonewu.fusion.common.BusinessException.class)
                .hasMessage("当前修复计划不可执行");
    }

    private ProductionRepairExecutor executor() {
        return new ProductionRepairExecutor(
                videoGenerationService, videoGenerationConsumer, attemptMapper, stepMapper);
    }
}

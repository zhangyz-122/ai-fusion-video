package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionUsageServiceTests {

    @Mock
    private ProductionRunMapper runMapper;

    @Mock
    private ProductionStepMapper stepMapper;

    @Mock
    private ProductionTakeMapper takeMapper;

    @Mock
    private QcResultMapper qcResultMapper;

    @Mock
    private ProductionRepairAttemptMapper repairAttemptMapper;

    @Mock
    private StoryboardItemMapper storyboardItemMapper;

    @Mock
    private VideoGenerationService videoGenerationService;

    @InjectMocks
    private ProductionUsageService usageService;

    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 9, 17, 10, 0, 0);

    @Test
    void runUsageAggregatesCandidatesQcAndRepairFacts() {
        when(runMapper.selectById(7L)).thenReturn(ProductionRun.builder()
                .id(7L).userId(3L).projectId(1L).storyboardItemId(5L)
                .status("QC_PENDING").selectedTakeId(11L).build());
        when(stepMapper.selectList(any())).thenReturn(List.of(ProductionStep.builder()
                .id(21L).runId(7L).stepType("GENERATE_VIDEO").status("SUCCEEDED")
                .videoTaskId(31L).workflowProfileId(2L).workflowVersionId(10L)
                .attempt(1).build()));
        when(takeMapper.selectList(any())).thenReturn(List.of(
                take(10L, 0, "REVIEW_REQUIRED"), take(11L, 1, "PASS")));
        when(qcResultMapper.selectList(any())).thenReturn(List.of(
                qc(40L, 10L, QcResult.REVIEW_REQUIRED, QcResult.PASS, "{\"duration\":5.0625}"),
                qc(41L, 11L, QcResult.PASS, QcResult.PASS, "{}"),
                qc(42L, 12L, QcResult.FAIL, QcResult.FAIL, null)));
        when(repairAttemptMapper.selectList(any())).thenReturn(List.of(
                ProductionRepairAttempt.builder().id(50L).runId(7L).attemptNo(1).build()));
        when(videoGenerationService.getById(31L)).thenReturn(VideoTask.builder()
                .id(31L).count(3).successCount(3).status(2).build());

        ProductionRunUsage usage = usageService.runUsage(7L, 3L);

        assertThat(usage.getCandidateCount()).isEqualTo(2);
        assertThat(usage.getRequestedCandidates()).isEqualTo(3);
        assertThat(usage.getVideoTaskSuccessCount()).isEqualTo(3);
        assertThat(usage.getWorkflowProfileId()).isEqualTo(2L);
        assertThat(usage.getTechnicalStatusCounts())
                .containsEntry(QcResult.PASS, 2)
                .containsEntry(QcResult.FAIL, 1);
        assertThat(usage.getQcStatusCounts())
                .containsEntry(QcResult.PASS, 1)
                .containsEntry(QcResult.REVIEW_REQUIRED, 1);
        assertThat(usage.getStructuredMetricsCount()).isEqualTo(2);
        assertThat(usage.getRepairAttemptCount()).isEqualTo(1);
        assertThat(usage.getSelectedTakeQcStatus()).isEqualTo(QcResult.PASS);
        assertThat(usage.getSelectedTakeTechnicalStatus()).isEqualTo(QcResult.PASS);
    }

    @Test
    void runUsageLeavesGenerationDurationUnknownWithoutTimestamps() {
        when(runMapper.selectById(7L)).thenReturn(ProductionRun.builder()
                .id(7L).userId(3L).status("CREATED").build());
        when(stepMapper.selectList(any())).thenReturn(List.of(ProductionStep.builder()
                .id(21L).runId(7L).attempt(0).build()));
        when(takeMapper.selectList(any())).thenReturn(List.of(take(10L, 0, QcResult.REVIEW_REQUIRED)));
        when(qcResultMapper.selectList(any())).thenReturn(List.of());
        when(repairAttemptMapper.selectList(any())).thenReturn(List.of());

        ProductionRunUsage usage = usageService.runUsage(7L, 3L);

        assertThat(usage.getGenerationMillis()).isNull();
        assertThat(usage.getSelectedTakeQcStatus()).isNull();
        assertThat(usage.getTechnicalStatusCounts()).isEmpty();
    }

    @Test
    void runUsageReportsElapsedMillisBetweenStepSubmitAndLastTake() {
        when(runMapper.selectById(7L)).thenReturn(ProductionRun.builder()
                .id(7L).userId(3L).status("QC_PENDING").build());
        ProductionStep step = ProductionStep.builder().id(21L).runId(7L).attempt(0).build();
        step.setCreateTime(SUBMITTED);
        when(stepMapper.selectList(any())).thenReturn(List.of(step));
        ProductionTake first = take(10L, 0, QcResult.PASS);
        ProductionTake last = take(11L, 1, QcResult.PASS);
        first.setCreateTime(SUBMITTED.plusSeconds(30));
        last.setCreateTime(SUBMITTED.plusSeconds(120));
        when(takeMapper.selectList(any())).thenReturn(List.of(first, last));
        when(qcResultMapper.selectList(any())).thenReturn(List.of());
        when(repairAttemptMapper.selectList(any())).thenReturn(List.of());

        assertThat(usageService.runUsage(7L, 3L).getGenerationMillis()).isEqualTo(120_000L);
    }

    @Test
    void runUsageRejectsRunsOwnedByAnotherUser() {
        when(runMapper.selectById(7L)).thenReturn(ProductionRun.builder()
                .id(7L).userId(99L).build());

        assertThatThrownBy(() -> usageService.runUsage(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产运行不存在");
    }

    @Test
    void shotUsageReturnsZerosWithoutRunningAnyQueryOnChildTables() {
        when(storyboardItemMapper.selectById(5L)).thenReturn(StoryboardItem.builder()
                .id(5L).selectedTakeId(11L).build());
        when(runMapper.selectList(any())).thenReturn(List.of());

        ProductionShotUsage usage = usageService.shotUsage(5L, 3L);

        assertThat(usage.getRunCount()).isZero();
        assertThat(usage.getCandidateCount()).isZero();
        assertThat(usage.getSelectedTakeId()).isEqualTo(11L);
        verifyNoInteractions(takeMapper, qcResultMapper, repairAttemptMapper);
    }

    @Test
    void shotUsageAggregatesAcrossRunsOfTheSameShot() {
        when(storyboardItemMapper.selectById(5L)).thenReturn(StoryboardItem.builder().id(5L).build());
        when(runMapper.selectList(any())).thenReturn(List.of(ProductionRun.builder()
                .id(7L).userId(3L).projectId(1L).storyboardItemId(5L).build()));
        when(takeMapper.selectList(any())).thenReturn(List.of(
                take(10L, 0, QcResult.PASS), take(11L, 1, QcResult.PASS), take(12L, 2, QcResult.PASS)));
        when(qcResultMapper.selectList(any())).thenReturn(List.of(
                qc(40L, 10L, QcResult.PASS, QcResult.PASS, "{\"width\":960}"),
                qc(41L, 11L, QcResult.REVIEW_REQUIRED, QcResult.PASS, null)));
        when(repairAttemptMapper.selectList(any())).thenReturn(List.of(
                ProductionRepairAttempt.builder().id(50L).runId(7L).attemptNo(1).build(),
                ProductionRepairAttempt.builder().id(51L).runId(7L).attemptNo(2).build()));

        ProductionShotUsage usage = usageService.shotUsage(5L, 3L);

        assertThat(usage.getRunCount()).isEqualTo(1);
        assertThat(usage.getCandidateCount()).isEqualTo(3);
        assertThat(usage.getTechnicalStatusCounts()).containsEntry(QcResult.PASS, 2);
        assertThat(usage.getStructuredMetricsCount()).isEqualTo(1);
        assertThat(usage.getRepairAttemptCount()).isEqualTo(2);
    }

    private static ProductionTake take(Long id, Integer index, String qcStatus) {
        return ProductionTake.builder()
                .id(id).runId(7L).storyboardItemId(5L).takeIndex(index).qcStatus(qcStatus).build();
    }

    private static QcResult qc(Long id, Long takeId, String status, String technicalStatus, String metricsJson) {
        return QcResult.builder()
                .id(id).runId(7L).takeId(takeId).storyboardItemId(5L)
                .status(status).technicalStatus(technicalStatus).technicalMetricsJson(metricsJson).build();
    }
}

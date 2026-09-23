package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * PR-029：生产用量观测。
 *
 * <p>只读侧聚合，不写入任何表，也不参与 Run/Step 状态迁移；生产事实仍以
 * {@code afv_production_*} 与现有 VideoTask/VideoItem 为唯一真相来源。
 * 状态按实际取值分组返回，指标口径（通过率、成本等）待定义后由调用方计算。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductionUsageService {

    private final ProductionRunMapper runMapper;
    private final ProductionStepMapper stepMapper;
    private final ProductionTakeMapper takeMapper;
    private final QcResultMapper qcResultMapper;
    private final ProductionRepairAttemptMapper repairAttemptMapper;
    private final StoryboardItemMapper storyboardItemMapper;
    private final VideoGenerationService videoGenerationService;

    public ProductionRunUsage runUsage(Long runId, Long userId) {
        ProductionRun run = requireOwnedRun(runId, userId);
        List<ProductionStep> steps = stepMapper.selectList(new LambdaQueryWrapper<ProductionStep>()
                .eq(ProductionStep::getRunId, runId));
        List<ProductionTake> takes = takeMapper.selectList(new LambdaQueryWrapper<ProductionTake>()
                .eq(ProductionTake::getRunId, runId)
                .orderByAsc(ProductionTake::getTakeIndex));
        List<QcResult> qcResults = qcResultMapper.selectList(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getRunId, runId));
        List<ProductionRepairAttempt> repairs = repairAttemptMapper.selectList(
                new LambdaQueryWrapper<ProductionRepairAttempt>()
                        .eq(ProductionRepairAttempt::getRunId, runId));

        ProductionStep latestStep = steps.stream()
                .max(Comparator.comparing(ProductionStep::getId))
                .orElse(null);
        VideoTask videoTask = latestStep == null || latestStep.getVideoTaskId() == null
                ? null : videoGenerationService.getById(latestStep.getVideoTaskId());
        QcResult selectedResult = run.getSelectedTakeId() == null ? null : qcResults.stream()
                .filter(result -> run.getSelectedTakeId().equals(result.getTakeId()))
                .max(Comparator.comparing(QcResult::getId))
                .orElse(null);

        return ProductionRunUsage.builder()
                .runId(run.getId())
                .userId(run.getUserId())
                .projectId(run.getProjectId())
                .storyboardItemId(run.getStoryboardItemId())
                .runStatus(run.getStatus())
                .failureCode(run.getFailureCode())
                .workflowProfileId(latestStep == null ? null : latestStep.getWorkflowProfileId())
                .workflowVersionId(latestStep == null ? null : latestStep.getWorkflowVersionId())
                .videoTaskId(latestStep == null ? null : latestStep.getVideoTaskId())
                .videoTaskStatus(videoTask == null ? null : videoTask.getStatus())
                .requestedCandidates(videoTask == null ? null : videoTask.getCount())
                .videoTaskSuccessCount(videoTask == null ? null : videoTask.getSuccessCount())
                .stepCount(steps.size())
                .maxStepAttempt(steps.stream()
                        .map(ProductionStep::getAttempt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null))
                .candidateCount(takes.size())
                .technicalStatusCounts(countBy(qcResults, QcResult::getTechnicalStatus))
                .qcStatusCounts(countBy(qcResults, QcResult::getStatus))
                .structuredMetricsCount((int) qcResults.stream()
                        .filter(result -> StringUtils.hasText(result.getTechnicalMetricsJson()))
                        .count())
                .repairAttemptCount(repairs.size())
                .selectedTakeId(run.getSelectedTakeId())
                .selectedTakeQcStatus(takes.stream()
                        .filter(take -> take.getId().equals(run.getSelectedTakeId()))
                        .map(ProductionTake::getQcStatus)
                        .findFirst()
                        .orElse(null))
                .selectedTakeTechnicalStatus(selectedResult == null ? null : selectedResult.getTechnicalStatus())
                .generationMillis(generationMillis(steps, takes))
                .build();
    }

    public ProductionShotUsage shotUsage(Long storyboardItemId, Long userId) {
        StoryboardItem item = storyboardItemMapper.selectById(storyboardItemId);
        if (item == null) {
            throw new BusinessException(404, "分镜条目不存在: " + storyboardItemId);
        }
        List<ProductionRun> runs = runMapper.selectList(new LambdaQueryWrapper<ProductionRun>()
                .eq(ProductionRun::getStoryboardItemId, storyboardItemId)
                .eq(ProductionRun::getUserId, userId)
                .orderByAsc(ProductionRun::getId));
        List<Long> runIds = runs.stream().map(ProductionRun::getId).toList();
        if (runIds.isEmpty()) {
            return ProductionShotUsage.builder()
                    .storyboardItemId(storyboardItemId)
                    .projectId(null)
                    .selectedTakeId(item.getSelectedTakeId())
                    .runCount(0)
                    .runIds(List.of())
                    .candidateCount(0)
                    .technicalStatusCounts(new TreeMap<>())
                    .qcStatusCounts(new TreeMap<>())
                    .structuredMetricsCount(0)
                    .repairAttemptCount(0)
                    .build();
        }

        List<ProductionTake> takes = takeMapper.selectList(new LambdaQueryWrapper<ProductionTake>()
                .in(ProductionTake::getRunId, runIds));
        List<QcResult> qcResults = qcResultMapper.selectList(new LambdaQueryWrapper<QcResult>()
                .in(QcResult::getRunId, runIds));
        List<ProductionRepairAttempt> repairs = repairAttemptMapper.selectList(
                new LambdaQueryWrapper<ProductionRepairAttempt>()
                        .in(ProductionRepairAttempt::getRunId, runIds));

        return ProductionShotUsage.builder()
                .storyboardItemId(storyboardItemId)
                .projectId(runs.get(0).getProjectId())
                .selectedTakeId(item.getSelectedTakeId())
                .runCount(runs.size())
                .runIds(runIds)
                .candidateCount(takes.size())
                .technicalStatusCounts(countBy(qcResults, QcResult::getTechnicalStatus))
                .qcStatusCounts(countBy(qcResults, QcResult::getStatus))
                .structuredMetricsCount((int) qcResults.stream()
                        .filter(result -> StringUtils.hasText(result.getTechnicalMetricsJson()))
                        .count())
                .repairAttemptCount(repairs.size())
                .build();
    }

    private ProductionRun requireOwnedRun(Long runId, Long userId) {
        ProductionRun run = runMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId())) {
            throw new BusinessException(404, "生产运行不存在: " + runId);
        }
        return run;
    }

    private static <T> TreeMap<String, Integer> countBy(List<T> rows, java.util.function.Function<T, String> classifier) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (T row : rows) {
            String key = classifier.apply(row);
            counts.merge(key == null ? "UNKNOWN" : key, 1, Integer::sum);
        }
        return counts;
    }

    /** 最早步骤提交到最后一个候选入库的间隔；缺少任一端时间时返回 null，不猜测耗时。 */
    private static Long generationMillis(List<ProductionStep> steps, List<ProductionTake> takes) {
        if (steps.isEmpty() || takes.isEmpty()) {
            return null;
        }
        LocalDateTime from = steps.stream()
                .map(ProductionStep::getCreateTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime to = takes.stream()
                .map(ProductionTake::getCreateTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (from == null || to == null || to.isBefore(from)) {
            return null;
        }
        return Duration.between(from, to).toMillis();
    }
}

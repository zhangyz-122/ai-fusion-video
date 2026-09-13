package com.stonewu.fusion.service.generation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.generation.ImageTaskMapper;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.service.production.ProductionRepairRouter;
import com.stonewu.fusion.service.production.ProductionRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 滞留任务回收：排队/执行中超过阈值的图片与视频任务标记为失败，
 * 避免仪表盘"进行中"列表被永久卡死的任务污染。
 * 被回收的视频任务若仍被进行中的 ProductionRun 等待，同步把运行标记为失败，避免运行永久悬挂。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationTaskReaper {

    /** 滞留阈值：超过该时长仍处于排队/执行中的任务视为卡死。 */
    private static final int STALE_HOURS = 2;

    private static final int FAILURE_STATUS = 3;

    private static final String TIMEOUT_MESSAGE = "任务滞留超过 " + STALE_HOURS + " 小时，已自动标记失败";

    /** 生产运行侧的超时失败码，与 reconcile 失败路径的码风格保持一致。 */
    private static final String RUN_FAILURE_CODE = "VIDEO_TASK_TIMEOUT";

    private final ImageTaskMapper imageTaskMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final ProductionRunMapper productionRunMapper;
    private final ProductionStepMapper productionStepMapper;
    private final ProductionRepairRouter repairRouter;

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reapStaleTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(STALE_HOURS);

        List<Long> staleImageIds = imageTaskMapper.selectList(new LambdaQueryWrapper<ImageTask>()
                .select(ImageTask::getId)
                .in(ImageTask::getStatus, 0, 1)
                .lt(ImageTask::getCreateTime, threshold)).stream().map(ImageTask::getId).toList();
        if (!staleImageIds.isEmpty()) {
            int updated = imageTaskMapper.update(null, new LambdaUpdateWrapper<ImageTask>()
                    .in(ImageTask::getId, staleImageIds)
                    .set(ImageTask::getStatus, FAILURE_STATUS)
                    .set(ImageTask::getErrorMsg, TIMEOUT_MESSAGE));
            log.warn("[TaskReaper] 滞留图片任务已标记失败: count={}", updated);
        }

        List<Long> staleVideoIds = videoTaskMapper.selectList(new LambdaQueryWrapper<VideoTask>()
                .select(VideoTask::getId)
                .in(VideoTask::getStatus, 0, 1)
                .lt(VideoTask::getCreateTime, threshold)).stream().map(VideoTask::getId).toList();
        if (!staleVideoIds.isEmpty()) {
            int updated = videoTaskMapper.update(null, new LambdaUpdateWrapper<VideoTask>()
                    .in(VideoTask::getId, staleVideoIds)
                    .set(VideoTask::getStatus, FAILURE_STATUS)
                    .set(VideoTask::getErrorMsg, TIMEOUT_MESSAGE));
            log.warn("[TaskReaper] 滞留视频任务已标记失败: count={}", updated);
            failWaitingProductionRuns(staleVideoIds);
        }
    }

    /**
     * 被回收视频任务若仍被 WAITING_GENERATION 的生产运行等待，
     * 记录修复谱系并把步骤与运行标记为失败；条件更新带状态守卫，天然幂等。
     */
    private void failWaitingProductionRuns(List<Long> staleVideoIds) {
        List<ProductionStep> steps = productionStepMapper.selectList(new LambdaQueryWrapper<ProductionStep>()
                .in(ProductionStep::getVideoTaskId, staleVideoIds)
                .eq(ProductionStep::getStepType, ProductionRunService.STEP_GENERATE_VIDEO));
        if (steps.isEmpty()) {
            return;
        }
        List<ProductionRun> waitingRuns = productionRunMapper.selectList(new LambdaQueryWrapper<ProductionRun>()
                .in(ProductionRun::getId, steps.stream().map(ProductionStep::getRunId).distinct().toList())
                .eq(ProductionRun::getStatus, ProductionRunService.RUN_WAITING_GENERATION));
        if (waitingRuns.isEmpty()) {
            return;
        }

        Map<Long, ProductionStep> stepByRunId = steps.stream()
                .collect(Collectors.toMap(ProductionStep::getRunId, Function.identity(),
                        (first, second) -> first));
        for (ProductionRun run : waitingRuns) {
            // 与 reconcile 的 markRunFailed 同构：先留修复谱系（幂等键防重），再落失败状态。
            repairRouter.recordFailure(run, stepByRunId.get(run.getId()), RUN_FAILURE_CODE, TIMEOUT_MESSAGE);
        }

        productionStepMapper.update(null, new LambdaUpdateWrapper<ProductionStep>()
                .in(ProductionStep::getId, waitingRuns.stream()
                        .map(run -> stepByRunId.get(run.getId()).getId()).toList())
                .set(ProductionStep::getStatus, ProductionRunService.STEP_FAILED)
                .set(ProductionStep::getErrorCode, RUN_FAILURE_CODE)
                .set(ProductionStep::getErrorMessage, TIMEOUT_MESSAGE));

        int updated = productionRunMapper.update(null, new LambdaUpdateWrapper<ProductionRun>()
                .in(ProductionRun::getId, waitingRuns.stream().map(ProductionRun::getId).toList())
                .eq(ProductionRun::getStatus, ProductionRunService.RUN_WAITING_GENERATION)
                .set(ProductionRun::getStatus, ProductionRunService.RUN_FAILED)
                .set(ProductionRun::getFailureCode, RUN_FAILURE_CODE)
                .set(ProductionRun::getFailureMessage, TIMEOUT_MESSAGE));
        log.warn("[TaskReaper] 滞留视频任务关联的生产运行已标记失败: count={}", updated);
    }
}

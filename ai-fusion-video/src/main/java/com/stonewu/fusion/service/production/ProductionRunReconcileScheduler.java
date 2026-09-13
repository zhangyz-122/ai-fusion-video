package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 生产运行自动同步调度器。
 *
 * <p>作为兜底机制定期扫描滞留在 WAITING_GENERATION 超过阈值的运行：
 * 仅当底层 VideoTask 已进入终态（成功/失败）时才复用
 * {@link ProductionRunService#reconcile(Long, Long)} 同步结果，
 * 避免运行因同步缺失而永久悬挂；任务仍在执行时跳过，不做无效查询。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductionRunReconcileScheduler {

    /** 滞留阈值：WAITING_GENERATION 超过该时长的运行才进入自动同步扫描。 */
    static final int STALE_MINUTES = 30;

    private static final Integer TASK_STATUS_SUCCEEDED = 2;
    private static final Integer TASK_STATUS_FAILED = 3;

    private final ProductionRunMapper runMapper;
    private final ProductionStepMapper stepMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final ProductionRunService productionRunService;

    @Scheduled(fixedDelay = 60 * 1000L)
    public void reconcileStaleWaitingRuns() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_MINUTES);
            List<ProductionRun> staleRuns = runMapper.selectList(new LambdaQueryWrapper<ProductionRun>()
                    .eq(ProductionRun::getStatus, ProductionRunService.RUN_WAITING_GENERATION)
                    .lt(ProductionRun::getUpdateTime, threshold));
            if (staleRuns.isEmpty()) {
                return;
            }
            Map<Long, Long> videoTaskIdsByRunId = loadVideoTaskIds(staleRuns);
            if (videoTaskIdsByRunId.isEmpty()) {
                return;
            }
            Map<Long, Integer> statusByTaskId = loadTaskStatuses(videoTaskIdsByRunId.values());

            for (ProductionRun run : staleRuns) {
                Long videoTaskId = videoTaskIdsByRunId.get(run.getId());
                Integer taskStatus = statusByTaskId.get(videoTaskId);
                if (!isTerminal(taskStatus)) {
                    continue;
                }
                try {
                    // reconcile 自带幂等（已有 take 与 QC 结果不会重复创建），按 run 所属用户校验归属。
                    productionRunService.reconcile(run.getId(), run.getUserId());
                    log.info("[ProductionScheduler] 滞留运行已自动同步: runId={}, videoTaskId={}, taskStatus={}",
                            run.getId(), videoTaskId, taskStatus);
                } catch (Exception exception) {
                    log.warn("[ProductionScheduler] 滞留运行自动同步失败: runId={}, error={}",
                            run.getId(), exception.getMessage());
                }
            }
        } catch (Exception exception) {
            log.warn("[ProductionScheduler] 滞留运行扫描失败: error={}", exception.getMessage());
        }
    }

    private Map<Long, Long> loadVideoTaskIds(List<ProductionRun> staleRuns) {
        return stepMapper.selectList(new LambdaQueryWrapper<ProductionStep>()
                        .in(ProductionStep::getRunId, staleRuns.stream().map(ProductionRun::getId).toList())
                        .eq(ProductionStep::getStepType, ProductionRunService.STEP_GENERATE_VIDEO))
                .stream()
                .filter(step -> step.getVideoTaskId() != null)
                .collect(Collectors.toMap(ProductionStep::getRunId, ProductionStep::getVideoTaskId,
                        (first, second) -> first));
    }

    /** 直接查表取任务状态，绕过 videoTask 缓存，避免回收/竞态场景读到过期状态。 */
    private Map<Long, Integer> loadTaskStatuses(Iterable<Long> videoTaskIds) {
        List<Long> ids = new ArrayList<>();
        videoTaskIds.forEach(ids::add);
        return videoTaskMapper.selectList(new LambdaQueryWrapper<VideoTask>()
                        .select(VideoTask::getId, VideoTask::getStatus)
                        .in(VideoTask::getId, ids))
                .stream()
                .collect(Collectors.toMap(VideoTask::getId, VideoTask::getStatus));
    }

    private boolean isTerminal(Integer taskStatus) {
        return TASK_STATUS_SUCCEEDED.equals(taskStatus) || TASK_STATUS_FAILED.equals(taskStatus);
    }
}

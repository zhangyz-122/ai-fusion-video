package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Production 失败路由器。
 *
 * <p>只做确定性的策略判断和修复谱系记录，不直接创建第二条视频队列。</p>
 *
 * <p>{@code SWITCH_WORKFLOW} 只标记意图、状态为 BLOCKED：Executor 只会复制原任务，
 * 若把它当作可自动重试就等于换了个名字重跑同一个不可用工作流；而契约禁止在模型与工作流
 * 之间静默回退。真正的自动切换需要 WorkflowProfile 路由先给出满足能力约束的候选。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductionRepairRouter {

    public static final String RETRY_SAME_WORKFLOW = "RETRY_SAME_WORKFLOW";
    public static final String SWITCH_WORKFLOW = "SWITCH_WORKFLOW";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String TERMINATE = "TERMINATE";

    public static final String PLANNED = "PLANNED";
    public static final String BLOCKED = "BLOCKED";
    private static final int DEFAULT_RETRY_BUDGET = 2;
    private static final int MAX_RETRY_BUDGET = 3;

    private final ProductionRepairAttemptMapper attemptMapper;

    public Decision decide(String failureCode, int retryCount, int retryBudget) {
        String normalizedCode = StringUtils.hasText(failureCode)
                ? failureCode.trim().toUpperCase(Locale.ROOT) : "UNKNOWN_FAILURE";
        String route = routeFor(normalizedCode);
        int safeCount = Math.max(0, retryCount);
        int safeBudget = Math.min(MAX_RETRY_BUDGET,
                retryBudget <= 0 ? DEFAULT_RETRY_BUDGET : retryBudget);
        boolean retryRoute = RETRY_SAME_WORKFLOW.equals(route);
        boolean allowed = retryRoute && safeCount < safeBudget;
        String status = allowed ? PLANNED : BLOCKED;
        String reason = explain(route, normalizedCode, safeCount, safeBudget, allowed);
        return new Decision(normalizedCode, route, status, safeCount + 1, safeBudget, allowed, reason);
    }

    @Transactional
    public ProductionRepairAttempt recordFailure(ProductionRun run, ProductionStep step,
                                                  String failureCode, String failureMessage) {
        int retryCount = countAttempts(run.getId(), step.getId());
        Decision decision = decide(failureCode, retryCount, DEFAULT_RETRY_BUDGET);
        String key = step.getId() + ":" + decision.attemptNo() + ":" + decision.failureCode();
        ProductionRepairAttempt existing = attemptMapper.selectOne(new LambdaQueryWrapper<ProductionRepairAttempt>()
                .eq(ProductionRepairAttempt::getRunId, run.getId())
                .eq(ProductionRepairAttempt::getIdempotencyKey, key));
        if (existing != null) {
            return existing;
        }

        ProductionRepairAttempt attempt = ProductionRepairAttempt.builder()
                .runId(run.getId())
                .sourceStepId(step.getId())
                .attemptNo(decision.attemptNo())
                .route(decision.route())
                .status(decision.status())
                .failureCode(decision.failureCode())
                .reason(StringUtils.hasText(failureMessage) ? failureMessage.trim() : decision.reason())
                .retryBudget(decision.retryBudget())
                .idempotencyKey(key)
                .build();
        attemptMapper.insert(attempt);
        return attempt;
    }

    private int countAttempts(Long runId, Long stepId) {
        return Math.toIntExact(attemptMapper.selectCount(new LambdaQueryWrapper<ProductionRepairAttempt>()
                .eq(ProductionRepairAttempt::getRunId, runId)
                .eq(ProductionRepairAttempt::getSourceStepId, stepId)));
    }

    private String routeFor(String code) {
        return switch (code) {
            case "VIDEO_TASK_SUBMIT_FAILED", "VIDEO_TASK_FAILED", "VIDEO_PROBE_FAILED", "VIDEO_QC_EXCEPTION" ->
                    RETRY_SAME_WORKFLOW;
            case "WORKFLOW_NODE_MISSING", "WORKFLOW_PRELIGHT_FAILED", "MODEL_LOAD_FAILED", "WORKFLOW_VERSION_MISMATCH" ->
                    SWITCH_WORKFLOW;
            case "TAKE_COUNT_MISMATCH", "STORYBOARD_NOT_FOUND" -> TERMINATE;
            default -> MANUAL_REVIEW;
        };
    }

    private String explain(String route, String code, int count, int budget, boolean allowed) {
        if (SWITCH_WORKFLOW.equals(route)) {
            return "失败 " + code + " 表明当前工作流不可用；生产契约禁止在模型与工作流之间静默回退，"
                    + "自动切换需要先由 WorkflowProfile 路由给出满足能力约束的候选，因此需人工指定替代工作流。";
        }
        if (!allowed) {
            return "失败 " + code + " 不允许继续自动重试，或已达到预算 " + budget + "。";
        }
        if (RETRY_SAME_WORKFLOW.equals(route)) {
            return "可在同一工作流上进行第 " + (count + 1) + " 次受限重试。";
        }
        return "需要人工复核后决定是否继续。";
    }

    public record Decision(String failureCode, String route, String status, int attemptNo,
                           int retryBudget, boolean retryAllowed, String reason) {
    }
}

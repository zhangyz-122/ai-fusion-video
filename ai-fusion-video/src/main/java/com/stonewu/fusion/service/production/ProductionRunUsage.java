package com.stonewu.fusion.service.production;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * PR-029：单次生产运行的用量观测视图。
 *
 * <p>全部字段由既有 Run/Step/Take/QcResult/RepairAttempt/VideoTask 派生，不新增落库、
 * 不记录第二份媒体事实。状态一律按实际取值分组，避免在此处固化尚未定义好的指标口径。</p>
 */
@Data
@Builder
public class ProductionRunUsage {

    private Long runId;

    private Long userId;

    private Long projectId;

    private Long storyboardItemId;

    private String runStatus;

    private String failureCode;

    /** 以下四项取自该 Run 最新的步骤及其关联视频任务，修复谱系会推进到新的步骤 */
    private Long workflowProfileId;

    private Long workflowVersionId;

    private Long videoTaskId;

    private Integer videoTaskStatus;

    /** VideoTask 申请的候选数，契约固定为 3 */
    private Integer requestedCandidates;

    private Integer videoTaskSuccessCount;

    private Integer stepCount;

    /** 该 Run 下步骤的最大尝试序号，用于观察重试深度 */
    private Integer maxStepAttempt;

    /** 已落库的候选（Take）数量 */
    private Integer candidateCount;

    /** 按 QcResult.technicalStatus 取值分组 */
    private Map<String, Integer> technicalStatusCounts;

    /** 按 QcResult.status 取值分组（含人工复核后的状态） */
    private Map<String, Integer> qcStatusCounts;

    /** 带结构化技术指标的质检记录数 */
    private Integer structuredMetricsCount;

    private Integer repairAttemptCount;

    private Long selectedTakeId;

    private String selectedTakeQcStatus;

    private String selectedTakeTechnicalStatus;

    /** 提交生成步骤到最后一个候选入库的墙钟耗时，任一缺失时为 null */
    private Long generationMillis;
}

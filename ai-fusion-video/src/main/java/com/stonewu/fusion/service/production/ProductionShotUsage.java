package com.stonewu.fusion.service.production;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * PR-029：单个分镜条目的跨 Run 用量汇总，用于按镜头粒度核对覆盖与候选下限。
 */
@Data
@Builder
public class ProductionShotUsage {

    private Long storyboardItemId;

    private Long projectId;

    /** StoryboardItem 上由 Production 维护的选定候选 */
    private Long selectedTakeId;

    /** 该镜头进入过的生产运行次数 */
    private Integer runCount;

    private List<Long> runIds;

    /** 该镜头所有运行累计落库的候选数量 */
    private Integer candidateCount;

    private Map<String, Integer> technicalStatusCounts;

    private Map<String, Integer> qcStatusCounts;

    private Integer structuredMetricsCount;

    private Integer repairAttemptCount;
}

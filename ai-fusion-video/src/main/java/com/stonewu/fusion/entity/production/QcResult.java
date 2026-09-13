package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Production 候选视频的独立质检记录。
 *
 * <p>ProductionTake 上的 qcStatus/qcNote 仍保留用于兼容旧数据，
 * 新链路以本表作为可审计的质检事实记录。</p>
 */
@TableName("afv_qc_result")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QcResult extends BaseEntity {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long takeId;

    private Long storyboardItemId;

    @Builder.Default
    private String status = REVIEW_REQUIRED;

    private String technicalStatus;

    private String technicalFailureCode;

    private String technicalMetricsJson;

    private LocalDateTime technicalEvaluatedAt;

    @Builder.Default
    private String evaluatorType = "SYSTEM";

    private BigDecimal score;

    private String failureCode;

    private String metricsJson;

    private String note;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;
}

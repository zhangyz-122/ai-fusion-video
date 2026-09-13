package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

/**
 * Production Run 内的可恢复步骤。
 */
@TableName("afv_production_step")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionStep extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String stepType;

    private String status;

    private Long videoTaskId;

    /** Logical workflow profile selected for this run. */
    private Long workflowProfileId;

    /** Immutable workflow version selected at submission time. */
    private Long workflowVersionId;

    private String executionRef;

    @Builder.Default
    private Integer attempt = 0;

    private String inputSnapshot;

    private String outputSnapshot;

    private String errorCode;

    private String errorMessage;
}

package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

/** 一次可审计的 Production 修复路由决策。 */
@TableName("afv_production_repair_attempt")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionRepairAttempt extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long sourceStepId;

    private Long parentAttemptId;

    private Integer attemptNo;

    private String route;

    private String status;

    private String failureCode;

    private String reason;

    private Integer retryBudget;

    private String idempotencyKey;

    /** 实际重新提交后创建的替代 VideoTask。 */
    private Long replacementVideoTaskId;

    /** 现有 VideoGenerationConsumer 返回的替代任务引用。 */
    private String replacementExecutionRef;
}

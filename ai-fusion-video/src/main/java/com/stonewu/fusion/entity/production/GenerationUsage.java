package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_generation_usage", autoResultMap = true)
public class GenerationUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long stepId;
    private Long takeId;
    private Long workflowProfileId;
    private String modelProvider;
    private String modelId;
    private Long wallMs;
    private Long gpuMs;
    private BigDecimal providerCost;
    private Integer retryCount;
    private Integer repairCount;
    private Boolean qcPass;
    private BigDecimal approvedSeconds;
    private BigDecimal humanReviewMinutes;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}

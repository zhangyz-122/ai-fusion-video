package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_production_take", autoResultMap = true)
public class ProductionTake {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long storyboardItemId;
    private String sourceType;
    private Long sourceItemId;
    private Long workflowProfileId;
    private Long workflowVersionId;
    private String modelId;
    private Long seed;
    private String qcStatus;
    private String metadataJson;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}

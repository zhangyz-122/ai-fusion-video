package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_production_step", autoResultMap = true)
public class ProductionStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long storyboardItemId;
    private String stepType;
    private String status;
    private String dependsOnJson;
    private String executionType;
    private Long executionRefId;
    private Integer attempt;
    private Long parentStepId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

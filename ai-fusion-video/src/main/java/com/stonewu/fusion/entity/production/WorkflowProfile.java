package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_workflow_profile", autoResultMap = true)
public class WorkflowProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileCode;
    private String mediaType;
    private String renderMode;
    private String capabilityTags;
    private Long workflowId;
    private String workflowVersionPolicy;
    private Long pinnedWorkflowVersionId;
    private String defaultModelId;
    private String qualityTier;
    private String costTier;
    private Integer maxDurationSeconds;
    private String referenceRequirements;
    private String cameraCapabilities;
    private String audioCapabilities;
    private String retryPolicy;
    private Boolean enabled;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

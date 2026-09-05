package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_production_run", autoResultMap = true)
public class ProductionRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long storyboardId;
    private Long storyboardEpisodeId;
    private String runType;
    private String status;
    private String idempotencyKey;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String metadataJson;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.stonewu.fusion.entity.storyboard;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_storyboard_item", autoResultMap = true)
public class StoryboardItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storyboardId;
    private Long storyboardEpisodeId;
    private Long storyboardSceneId;
    private Integer sortOrder = 0;
    private String shotNumber;
    private String autoShotNumber;
    private String imageUrl;
    private String referenceImageUrl;
    private String videoUrl;
    private String generatedImageUrl;
    private String firstFrameImageUrl;
    private String lastFrameImageUrl;
    private String firstFramePrompt;
    private String lastFramePrompt;
    private String generatedVideoUrl;
    private String videoPrompt;
    private String shotType;
    private BigDecimal duration;
    private String content;
    private String sceneExpectation;
    private String sound;
    private String dialogue;
    private String soundEffect;
    private String music;
    private String cameraMovement;
    private String cameraAngle;
    private String cameraEquipment;
    private String characterIds;
    private Long sceneAssetItemId;
    private String propIds;
    private String customData;

    // ==== AI Drama OS Production extension (PR-005) ====
    private String productionStatus;
    private Long selectedTakeId;
    private Long workflowProfileId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

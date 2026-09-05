package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_story_event", autoResultMap = true)
public class StoryEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long episodeId;
    private String eventType;
    private String characterName;
    private String propName;
    private String locationName;
    private String oldValue;
    private String newValue;
    private String description;
    private Long sourceShotId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}

package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_story_state_snapshot", autoResultMap = true)
public class StoryStateSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long episodeId;
    private Long afterEventId;
    private String stateJson;
    private String hashVersion;
    private LocalDateTime createdAt;
}

package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_episode_contract", autoResultMap = true)
public class EpisodeContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long episodeId;
    private String inputStateJson;
    private String requiredBeatsJson;
    private String knowledgeBoundaryJson;
    private String openLoopsInJson;
    private String mustResolveJson;
    private String openLoopsOutJson;
    private String outputStateJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

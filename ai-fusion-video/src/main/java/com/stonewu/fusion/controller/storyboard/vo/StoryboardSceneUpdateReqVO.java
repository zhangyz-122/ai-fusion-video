package com.stonewu.fusion.controller.storyboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "更新分镜场次请求")
@Data
public class StoryboardSceneUpdateReqVO {

    private Long id;

    /** 目标分镜集ID，传值且与当前不同时移动场次（必须与场次属于同一分镜） */
    private Long episodeId;

    private String sceneNumber;

    private String sceneHeading;

    private String location;

    private String timeOfDay;

    private String intExt;

    private Integer sortOrder;
}

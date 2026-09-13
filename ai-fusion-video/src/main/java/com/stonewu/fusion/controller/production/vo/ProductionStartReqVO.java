package com.stonewu.fusion.controller.production.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启动分镜条目生产请求。
 */
@Schema(description = "启动分镜条目生产请求")
@Data
public class ProductionStartReqVO {

    @NotNull(message = "分镜条目不能为空")
    private Long storyboardItemId;

    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;

    private String prompt;

    private Long modelId;

    /** Optional logical WorkflowProfile; omitted preserves existing model-active-version behavior. */
    private Long workflowProfileId;

    private String generateMode;

    private String firstFrameImageUrl;

    private String lastFrameImageUrl;

    private String ratio;

    private String resolution;

    private Integer duration;

    private Long seed;
}

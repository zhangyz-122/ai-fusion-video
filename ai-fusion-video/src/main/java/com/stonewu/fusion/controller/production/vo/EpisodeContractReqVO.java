package com.stonewu.fusion.controller.production.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 定义分集剧情契约请求。
 *
 * <p>状态字段是 JSON 对象，键写作 {@code CHARACTER:林川}；节拍与待闭合悬念是字符串数组。</p>
 */
@Schema(description = "定义分集剧情契约请求")
@Data
public class EpisodeContractReqVO {

    @NotNull(message = "项目不能为空")
    private Long projectId;

    @NotNull(message = "分集不能为空")
    private Long episodeId;

    @Schema(description = "本集结束时应成立的剧情状态")
    private String outputStateJson;

    @Schema(description = "本集必须出现的剧情节拍")
    private String requiredBeatsJson;

    @Schema(description = "本集必须闭合的悬念主体名")
    private String mustResolveJson;
}

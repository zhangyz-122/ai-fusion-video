package com.stonewu.fusion.controller.script.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 自动分块解析状态。
 */
@Data
@Schema(description = "自动分块解析状态")
public class AutoSplitStatusVO {

    @Schema(description = "解析状态：0 未解析，1 进行中，2 完成，3 失败")
    private Integer parsingStatus;

    @Schema(description = "解析进度说明")
    private String parsingProgress;

    @Schema(description = "已生成的分集数")
    private Integer totalEpisodes;
}

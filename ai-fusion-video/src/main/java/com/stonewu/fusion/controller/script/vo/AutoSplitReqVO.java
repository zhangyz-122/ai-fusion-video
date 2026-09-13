package com.stonewu.fusion.controller.script.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 长文本自动分块解析请求。
 */
@Data
@Schema(description = "自动分块解析请求")
public class AutoSplitReqVO {

    @Schema(description = "文本模型 ID（缺省使用默认对话模型）")
    private Long modelId;

    @Schema(description = "每块目标字符数，范围 2000-12000，越界自动钳制，缺省 6000")
    private Integer chunkChars;
}

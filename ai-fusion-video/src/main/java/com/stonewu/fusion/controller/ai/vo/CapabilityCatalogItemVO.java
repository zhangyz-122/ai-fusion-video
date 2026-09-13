package com.stonewu.fusion.controller.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能力目录条目（公共只读，已脱敏）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "能力目录条目")
public class CapabilityCatalogItemVO {

    public static final String SOURCE_MODEL = "MODEL";
    public static final String SOURCE_WORKFLOW = "WORKFLOW";

    @Schema(description = "条目ID（模型ID或工作流ID）")
    private Long id;

    @Schema(description = "能力名称")
    private String name;

    @Schema(description = "能力说明")
    private String description;

    @Schema(description = "来源类型：MODEL=已配置模型，WORKFLOW=工作流能力")
    private String source;

    @Schema(description = "是否已启用可用")
    private Boolean enabled;

    @Schema(description = "是否默认能力（仅模型条目有意义）")
    private Boolean defaultCapability;

    @Schema(description = "不可用原因（仅待接入条目有值）")
    private String pendingReason;
}

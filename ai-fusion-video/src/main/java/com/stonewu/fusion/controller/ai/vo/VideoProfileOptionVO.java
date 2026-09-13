package com.stonewu.fusion.controller.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频工作流 Profile 选项（公共只读，已脱敏）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视频工作流 Profile 选项")
public class VideoProfileOptionVO {

    @Schema(description = "Profile ID")
    private Long id;

    @Schema(description = "Profile 代码")
    private String code;

    @Schema(description = "Profile 名称")
    private String name;

    @Schema(description = "用途（I2V / FLF / INFINITETALK 等）")
    private String purpose;
}

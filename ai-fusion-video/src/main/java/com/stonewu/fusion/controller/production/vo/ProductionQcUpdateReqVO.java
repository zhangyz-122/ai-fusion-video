package com.stonewu.fusion.controller.production.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新候选视频质检结果请求。
 */
@Schema(description = "更新候选视频质检结果请求")
@Data
public class ProductionQcUpdateReqVO {

    @NotBlank(message = "质检状态不能为空")
    private String qcStatus;

    private String qcNote;
}

package com.stonewu.fusion.controller.ai;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.ai.vo.CapabilityCatalogItemVO;
import com.stonewu.fusion.controller.ai.vo.VideoProfileOptionVO;
import com.stonewu.fusion.service.ai.CapabilityCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公共能力目录：任一登录用户可读，字段已脱敏。
 */
@Tag(name = "能力目录")
@RestController
@RequestMapping("/api/ai/capability")
@RequiredArgsConstructor
public class CapabilityCatalogController {

    private final CapabilityCatalogService capabilityCatalogService;

    @Operation(summary = "按模型类型查询能力目录（已启用与待接入）")
    @GetMapping("/catalog")
    public CommonResult<List<CapabilityCatalogItemVO>> catalog(@RequestParam("modelType") Integer modelType) {
        return CommonResult.success(capabilityCatalogService.getCatalog(modelType));
    }

    @Operation(summary = "查询启用的视频工作流 Profile 选项")
    @GetMapping("/video-profiles")
    public CommonResult<List<VideoProfileOptionVO>> videoProfiles() {
        return CommonResult.success(capabilityCatalogService.getVideoProfiles());
    }
}

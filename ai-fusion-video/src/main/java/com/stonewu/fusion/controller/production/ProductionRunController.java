package com.stonewu.fusion.controller.production;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.service.production.ProductionRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "生产运行批次")
@RestController
@RequestMapping("/api/production/runs")
@RequiredArgsConstructor
public class ProductionRunController {

    private final ProductionRunService runService;

    @Operation(summary = "获取项目的生产批次列表")
    @GetMapping
    public CommonResult<List<ProductionRun>> listByProject(@RequestParam Long projectId) {
        return CommonResult.success(runService.listByProject(projectId));
    }
}

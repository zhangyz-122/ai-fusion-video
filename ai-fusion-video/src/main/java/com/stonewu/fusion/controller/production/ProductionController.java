package com.stonewu.fusion.controller.production;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.production.vo.ProductionQcUpdateReqVO;
import com.stonewu.fusion.controller.production.vo.ProductionStartReqVO;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.service.production.ProductionRunDetail;
import com.stonewu.fusion.service.production.ProductionRunService;
import com.stonewu.fusion.service.production.ShotReadiness;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;

/**
 * 分镜生产编排接口。
 */
@Tag(name = "分镜生产")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionRunService productionRunService;
    private final ProjectAccessGuard accessGuard;

    @Operation(summary = "当前用户的生产运行分页列表")
    @GetMapping("/runs")
    public CommonResult<PageResult<ProductionRun>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return CommonResult.success(productionRunService.list(
                SecurityUtils.requireCurrentUserId(), status, (int) pageNo, (int) pageSize));
    }

    @Operation(summary = "查询镜头是否允许进入生产")
    @GetMapping("/shots/{storyboardItemId}/readiness")
    public CommonResult<ShotReadiness> readiness(@PathVariable Long storyboardItemId) {
        accessGuard.assertStoryboardItem(storyboardItemId);
        return CommonResult.success(productionRunService.readiness(storyboardItemId));
    }

    @Operation(summary = "启动分镜条目生产（固定生成3个候选）")
    @PostMapping("/runs")
    public CommonResult<ProductionRunDetail> start(@Valid @RequestBody ProductionStartReqVO request) {
        accessGuard.assertStoryboardItem(request.getStoryboardItemId());
        return CommonResult.success(productionRunService.start(request, requireCurrentUserId()));
    }

    @Operation(summary = "查询生产运行")
    @GetMapping("/runs/{runId}")
    public CommonResult<ProductionRunDetail> detail(@PathVariable Long runId) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.detail(runId, requireCurrentUserId()));
    }

    @Operation(summary = "同步现有视频任务结果为候选视频")
    @PostMapping("/runs/{runId}/reconcile")
    public CommonResult<ProductionRunDetail> reconcile(@PathVariable Long runId) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.reconcile(runId, requireCurrentUserId()));
    }

    @Operation(summary = "执行一次受限生产修复")
    @PostMapping("/runs/{runId}/repair")
    public CommonResult<ProductionRunDetail> repair(@PathVariable Long runId) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.repair(runId, requireCurrentUserId()));
    }

    @Operation(summary = "更新候选视频质检结果")
    @PutMapping("/runs/{runId}/takes/{takeId}/qc")
    public CommonResult<ProductionRunDetail> updateQc(
            @PathVariable Long runId,
            @PathVariable Long takeId,
            @Valid @RequestBody ProductionQcUpdateReqVO request) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.updateQc(
                runId, takeId, requireCurrentUserId(), request.getQcStatus(), request.getQcNote()));
    }

    @Operation(summary = "选择候选视频")
    @PostMapping("/runs/{runId}/takes/{takeId}/select")
    public CommonResult<ProductionRunDetail> selectTake(
            @PathVariable Long runId,
            @PathVariable Long takeId) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.selectTake(
                runId, takeId, requireCurrentUserId()));
    }

    @Operation(summary = "使用现有合成服务合成本集视频")
    @PostMapping("/runs/{runId}/compose")
    public CommonResult<String> compose(@PathVariable Long runId) {
        accessGuard.assertProductionRun(runId);
        return CommonResult.success(productionRunService.compose(runId, requireCurrentUserId()));
    }
}

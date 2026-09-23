package com.stonewu.fusion.controller.production;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.service.production.ProductionTakeService;
import com.stonewu.fusion.service.production.QcResultService;
import com.stonewu.fusion.service.production.TakeSelectionService;
import com.stonewu.fusion.service.production.qc.QcEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "生产镜头管理")
@RestController
@RequestMapping("/api/production/takes")
@RequiredArgsConstructor
public class ProductionTakeController {

    private final ProductionTakeService takeService;
    private final TakeSelectionService selectionService;
    private final QcEvaluationService qcEvaluationService;
    private final QcResultService qcResultService;

    @Operation(summary = "获取镜头的候选 Take 列表")
    @GetMapping("/item/{itemId}")
    public CommonResult<List<ProductionTake>> listByItem(@PathVariable Long itemId) {
        return CommonResult.success(takeService.listByStoryboardItem(itemId));
    }

    @Operation(summary = "选定镜头的 Take")
    @PostMapping("/select")
    public CommonResult<ProductionTake> selectTake(@RequestBody SelectTakeReq req) {
        return CommonResult.success(selectionService.selectTake(req.storyboardItemId(), req.takeId()));
    }

    @Operation(summary = "取消镜头的 Take 选定")
    @DeleteMapping("/deselect/{itemId}")
    public CommonResult<Void> deselect(@PathVariable Long itemId) {
        selectionService.deselectTake(itemId);
        return CommonResult.success(null);
    }

    @Operation(summary = "对 Take 执行质检")
    @PostMapping("/{takeId}/qc")
    public CommonResult<ProductionTake> evaluateQc(@PathVariable Long takeId) {
        return CommonResult.success(qcEvaluationService.evaluate(takeId));
    }

    @Operation(summary = "获取 Take 的质检明细")
    @GetMapping("/{takeId}/qc")
    public CommonResult<List<QcResult>> listQcResults(@PathVariable Long takeId) {
        return CommonResult.success(qcResultService.listByTake(takeId));
    }

    public record SelectTakeReq(Long storyboardItemId, Long takeId) {}
}

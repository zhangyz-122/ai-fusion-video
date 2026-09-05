package com.stonewu.fusion.controller.production;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.service.production.ProductionTakeService;
import com.stonewu.fusion.service.production.TakeSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/production/takes")
@RequiredArgsConstructor
public class ProductionTakeController {

    private final ProductionTakeService takeService;
    private final TakeSelectionService selectionService;

    @GetMapping("/item/{itemId}")
    public CommonResult<List<ProductionTake>> listByItem(@PathVariable Long itemId) {
        return CommonResult.success(takeService.listByStoryboardItem(itemId));
    }

    @PostMapping("/select")
    public CommonResult<ProductionTake> selectTake(@RequestBody SelectTakeReq req) {
        return CommonResult.success(selectionService.selectTake(req.storyboardItemId(), req.takeId()));
    }

    @DeleteMapping("/deselect/{itemId}")
    public CommonResult<Void> deselect(@PathVariable Long itemId) {
        selectionService.deselectTake(itemId);
        return CommonResult.success(null);
    }

    public record SelectTakeReq(Long storyboardItemId, Long takeId) {}
}

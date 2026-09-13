package com.stonewu.fusion.controller.dashboard;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.dashboard.vo.DashboardActivityVO;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.service.dashboard.DashboardActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘聚合接口。
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardActivityService dashboardActivityService;

    @Operation(summary = "当前用户的进行中任务与待处理事项")
    @GetMapping("/activity")
    public CommonResult<DashboardActivityVO> activity() {
        return CommonResult.success(
                dashboardActivityService.getActivity(SecurityUtils.requireCurrentUserId()));
    }
}

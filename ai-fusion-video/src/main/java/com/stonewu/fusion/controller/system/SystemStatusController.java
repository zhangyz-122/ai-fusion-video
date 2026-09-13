package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.system.vo.MediaStorageStatsRespVO;
import com.stonewu.fusion.controller.system.vo.SystemHealthRespVO;
import com.stonewu.fusion.controller.system.vo.VideoQueueStatusRespVO;
import com.stonewu.fusion.service.system.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统状态 Controller（只读，仅管理员）
 */
@Tag(name = "系统状态")
@RestController
@RequestMapping("/api/system/status")
@RequiredArgsConstructor
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    @Operation(summary = "健康检查（后端与数据库连通状态）")
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<SystemHealthRespVO> health() {
        return CommonResult.success(systemStatusService.getHealth());
    }

    @Operation(summary = "媒体目录存储统计")
    @GetMapping("/storage")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<MediaStorageStatsRespVO> storage() {
        return CommonResult.success(systemStatusService.getMediaStorageStats());
    }

    @Operation(summary = "Redis 视频队列深度")
    @GetMapping("/video-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<VideoQueueStatusRespVO> videoQueue() {
        return CommonResult.success(systemStatusService.getVideoQueueStatus());
    }
}

package com.stonewu.fusion.controller.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘活动聚合：当前用户的进行中任务与待处理事项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivityVO {

    private List<ActivityItem> running;

    private List<ActivityItem> pending;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {

        @Schema(description = "条目种类", example = "IMAGE_TASK")
        private String kind;

        /** 数字主键（视频任务/生产运行） */
        private Long refId;

        /** 字符串主键（图片任务 taskId） */
        private String refKey;

        @Schema(description = "标题（任务提示词截断）")
        private String title;

        @Schema(description = "状态或待办说明")
        private String detail;

        private Long projectId;

        private String createTime;
    }
}

package com.stonewu.fusion.controller.system.vo;

import lombok.Data;

import java.util.List;

/**
 * Redis 视频队列状态响应
 */
@Data
public class VideoQueueStatusRespVO {

    /** Redis 是否可用 */
    private Boolean available;

    /** 不可用时的通用说明（不含内部细节） */
    private String error;

    /** 所有视频队列待处理任务总数 */
    private Integer totalPending;

    /** 所有视频队列正在执行任务总数 */
    private Integer totalRunning;

    /** 各队列明细 */
    private List<QueueStat> queues;

    @Data
    public static class QueueStat {

        /** 队列名 */
        private String name;

        /** 待处理任务数 */
        private Integer pending;

        /** 正在执行任务数 */
        private Integer running;

        /** 队列最大并发数 */
        private Integer maxConcurrent;
    }
}

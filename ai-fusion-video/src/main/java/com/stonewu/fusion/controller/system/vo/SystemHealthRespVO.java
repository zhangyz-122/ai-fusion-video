package com.stonewu.fusion.controller.system.vo;

import lombok.Data;

/**
 * 系统健康状态响应
 */
@Data
public class SystemHealthRespVO {

    /** 整体状态：UP / DOWN */
    private String status;

    /** 检查时间（ISO-8601） */
    private String checkedAt;

    /** 各组件状态 */
    private Components components;

    @Data
    public static class Components {

        /** 后端服务（能响应本请求即为 UP） */
        private ComponentStatus backend;

        /** 数据库连通性探测结果 */
        private ComponentStatus database;
    }

    @Data
    public static class ComponentStatus {

        /** 组件状态：UP / DOWN */
        private String status;
    }
}

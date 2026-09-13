package com.stonewu.fusion.controller.system.vo;

import lombok.Data;

/**
 * 媒体目录存储统计响应
 */
@Data
public class MediaStorageStatsRespVO {

    /** 当前默认存储类型（local / s3 等） */
    private String storageType;

    /** 实际扫描的本地媒体根目录 */
    private String basePath;

    /** 媒体根目录是否存在 */
    private Boolean exists;

    /** 文件总数 */
    private Long totalFiles;

    /** 字节总量 */
    private Long totalBytes;

    /** 分类统计 */
    private Categories categories;

    @Data
    public static class Categories {

        /** 图片目录（images，含子目录） */
        private DirStats images;

        /** 视频目录（videos，不含 composed） */
        private DirStats videos;

        /** 合成视频目录（videos/composed） */
        private DirStats composed;
    }

    @Data
    public static class DirStats {

        /** 文件数 */
        private Long fileCount;

        /** 字节总量 */
        private Long totalBytes;
    }
}

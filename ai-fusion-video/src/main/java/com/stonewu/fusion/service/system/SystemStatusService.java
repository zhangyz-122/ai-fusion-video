package com.stonewu.fusion.service.system;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.controller.system.vo.MediaStorageStatsRespVO;
import com.stonewu.fusion.controller.system.vo.SystemHealthRespVO;
import com.stonewu.fusion.controller.system.vo.VideoQueueStatusRespVO;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.infrastructure.queue.RedisTaskQueue;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.storage.StorageTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 系统状态只读服务：健康探测、媒体目录统计、视频队列深度。
 * <p>
 * 状态页需要每次返回最新探测结果，因此本服务不使用缓存。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemStatusService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    /** 视频生成队列名前缀，与 VideoGenerationConsumer 保持一致 */
    private static final String VIDEO_QUEUE_PREFIX = "video_generation";
    private static final String VIDEO_QUEUE_MODEL_PREFIX = VIDEO_QUEUE_PREFIX + ":model:";

    private static final int DB_PROBE_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final StorageConfigService storageConfigService;
    private final RedisTaskQueue taskQueue;

    @Value("${app.storage.local-base-path:./data/media}")
    private String localBasePath;

    /** 数据库连通探测专用模板，限定查询超时避免状态页被慢库拖住 */
    private volatile JdbcTemplate dbProbeTemplate;

    /**
     * 健康检查：后端本地状态 + 数据库连通探测
     */
    public SystemHealthRespVO getHealth() {
        SystemHealthRespVO resp = new SystemHealthRespVO();
        String databaseStatus = probeDatabase() ? STATUS_UP : STATUS_DOWN;

        SystemHealthRespVO.Components components = new SystemHealthRespVO.Components();
        components.setBackend(component(STATUS_UP));
        components.setDatabase(component(databaseStatus));
        resp.setComponents(components);
        resp.setStatus(STATUS_UP.equals(databaseStatus) ? STATUS_UP : STATUS_DOWN);
        resp.setCheckedAt(Instant.now().toString());
        return resp;
    }

    /**
     * 媒体目录统计：按 images / videos / composed 子目录返回文件数与字节总量
     */
    public MediaStorageStatsRespVO getMediaStorageStats() {
        Path root = resolveMediaRoot();
        MediaStorageStatsRespVO resp = new MediaStorageStatsRespVO();
        resp.setStorageType(resolveStorageType());
        resp.setBasePath(root.toString());
        resp.setExists(Files.isDirectory(root));

        MediaStorageStatsRespVO.DirStats images =
                scanDirectory(root.resolve("images"));
        MediaStorageStatsRespVO.DirStats composed =
                scanDirectory(root.resolve("videos").resolve("composed"));
        MediaStorageStatsRespVO.DirStats videos =
                scanDirectory(root.resolve("videos"), root.resolve("videos").resolve("composed"));

        MediaStorageStatsRespVO.Categories categories = new MediaStorageStatsRespVO.Categories();
        categories.setImages(images);
        categories.setVideos(videos);
        categories.setComposed(composed);
        resp.setCategories(categories);

        resp.setTotalFiles(images.getFileCount() + videos.getFileCount() + composed.getFileCount());
        resp.setTotalBytes(images.getTotalBytes() + videos.getTotalBytes() + composed.getTotalBytes());
        return resp;
    }

    /**
     * Redis 视频队列深度（只读，不修改任何队列状态）
     */
    public VideoQueueStatusRespVO getVideoQueueStatus() {
        VideoQueueStatusRespVO resp = new VideoQueueStatusRespVO();
        List<VideoQueueStatusRespVO.QueueStat> queues = new ArrayList<>();
        resp.setAvailable(true);
        resp.setError(null);
        try {
            for (String queueName : collectVideoQueueNames()) {
                VideoQueueStatusRespVO.QueueStat stat = new VideoQueueStatusRespVO.QueueStat();
                stat.setName(queueName);
                stat.setPending(taskQueue.getQueueLength(queueName));
                stat.setRunning(taskQueue.getConcurrentCount(queueName));
                stat.setMaxConcurrent(taskQueue.getMaxConcurrent(queueName));
                queues.add(stat);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("[SystemStatus] 读取视频队列失败: Redis 不可用");
            resp.setAvailable(false);
            resp.setError("Redis 暂不可用，无法读取队列状态");
        } catch (Exception e) {
            log.warn("[SystemStatus] 读取视频队列失败", e);
            resp.setAvailable(false);
            resp.setError("读取队列状态失败，请稍后重试");
        }
        int totalPending = queues.stream().mapToInt(VideoQueueStatusRespVO.QueueStat::getPending).sum();
        int totalRunning = queues.stream().mapToInt(VideoQueueStatusRespVO.QueueStat::getRunning).sum();
        resp.setQueues(queues);
        resp.setTotalPending(totalPending);
        resp.setTotalRunning(totalRunning);
        return resp;
    }

    private List<String> collectVideoQueueNames() {
        List<String> queueNames = new ArrayList<>(taskQueue.listRegisteredQueuesByPrefix(VIDEO_QUEUE_PREFIX));
        queueNames.sort(String::compareTo);
        return queueNames;
    }

    private boolean probeDatabase() {
        try {
            JdbcTemplate template = getDbProbeTemplate();
            Integer result = template.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("[SystemStatus] 数据库连通探测失败: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private JdbcTemplate getDbProbeTemplate() {
        JdbcTemplate template = this.dbProbeTemplate;
        if (template == null) {
            synchronized (this) {
                if (this.dbProbeTemplate == null) {
                    JdbcTemplate created = new JdbcTemplate(dataSource);
                    created.setQueryTimeout(DB_PROBE_TIMEOUT_SECONDS);
                    this.dbProbeTemplate = created;
                }
                template = this.dbProbeTemplate;
            }
        }
        return template;
    }

    /**
     * 与 WebMvcConfig 的 /media/** 映射保持一致的媒体根目录解析逻辑
     */
    private Path resolveMediaRoot() {
        String basePath = localBasePath;
        try {
            StorageConfig config = storageConfigService.getDefaultConfig();
            if (config != null && StorageTypes.LOCAL.equals(StorageTypes.normalizeType(config.getType()))
                    && StrUtil.isNotBlank(config.getBasePath())) {
                basePath = config.getBasePath();
            }
        } catch (Exception e) {
            log.debug("[SystemStatus] 存储配置未就绪，使用默认路径: {}", basePath);
        }
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    private String resolveStorageType() {
        try {
            StorageConfig config = storageConfigService.getDefaultConfig();
            if (config != null && StrUtil.isNotBlank(config.getType())) {
                return StorageTypes.normalizeType(config.getType());
            }
        } catch (Exception e) {
            log.debug("[SystemStatus] 存储配置未就绪，按本地存储统计");
        }
        return StorageTypes.LOCAL;
    }

    private MediaStorageStatsRespVO.DirStats scanDirectory(Path dir) {
        return scanDirectory(dir, null);
    }

    /**
     * 统计目录下的常规文件；skipChild 为空时不跳过任何子目录
     */
    private MediaStorageStatsRespVO.DirStats scanDirectory(Path dir, Path skipChild) {
        MediaStorageStatsRespVO.DirStats stats = new MediaStorageStatsRespVO.DirStats();
        stats.setFileCount(0L);
        stats.setTotalBytes(0L);
        if (!Files.isDirectory(dir)) {
            return stats;
        }
        long fileCount = 0;
        long totalBytes = 0;
        try (Stream<Path> paths = Files.walk(dir)) {
            var it = paths.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                Path file = it.next();
                if (skipChild != null && file.startsWith(skipChild)) {
                    continue;
                }
                try {
                    totalBytes += Files.size(file);
                    fileCount++;
                } catch (IOException e) {
                    log.debug("[SystemStatus] 读取文件大小失败: {}", file);
                }
            }
        } catch (IOException e) {
            log.warn("[SystemStatus] 遍历目录失败: {}", dir);
        }
        stats.setFileCount(fileCount);
        stats.setTotalBytes(totalBytes);
        return stats;
    }

    private SystemHealthRespVO.ComponentStatus component(String status) {
        SystemHealthRespVO.ComponentStatus componentStatus = new SystemHealthRespVO.ComponentStatus();
        componentStatus.setStatus(status);
        return componentStatus;
    }
}

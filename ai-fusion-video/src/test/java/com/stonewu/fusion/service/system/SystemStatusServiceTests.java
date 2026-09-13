package com.stonewu.fusion.service.system;

import com.stonewu.fusion.controller.system.vo.MediaStorageStatsRespVO;
import com.stonewu.fusion.controller.system.vo.SystemHealthRespVO;
import com.stonewu.fusion.controller.system.vo.VideoQueueStatusRespVO;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.infrastructure.queue.RedisTaskQueue;
import com.stonewu.fusion.service.storage.StorageConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusServiceTests {

    private final StorageConfigService storageConfigService = mock(StorageConfigService.class);
    private final RedisTaskQueue taskQueue = mock(RedisTaskQueue.class);

    /** dataSource 传 null：健康探测通过注入的 dbProbeTemplate 完成，不触达真实数据源 */
    private SystemStatusService newService(String localBasePath, JdbcTemplate probeTemplate) {
        SystemStatusService service = new SystemStatusService(
                null, storageConfigService, taskQueue);
        ReflectionTestUtils.setField(service, "localBasePath", localBasePath);
        if (probeTemplate != null) {
            ReflectionTestUtils.setField(service, "dbProbeTemplate", probeTemplate);
        }
        return service;
    }

    @Test
    void healthReportsUpWhenDatabaseReachable() {
        JdbcTemplate probe = mock(JdbcTemplate.class);
        when(probe.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        SystemStatusService service = newService("./data/media", probe);

        SystemHealthRespVO resp = service.getHealth();

        assertThat(resp.getStatus()).isEqualTo("UP");
        assertThat(resp.getComponents().getBackend().getStatus()).isEqualTo("UP");
        assertThat(resp.getComponents().getDatabase().getStatus()).isEqualTo("UP");
        assertThat(resp.getCheckedAt()).isNotBlank();
    }

    @Test
    void healthReportsDownWhenDatabaseProbeFails() {
        JdbcTemplate probe = mock(JdbcTemplate.class);
        when(probe.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new RuntimeException("connection refused"));
        SystemStatusService service = newService("./data/media", probe);

        SystemHealthRespVO resp = service.getHealth();

        assertThat(resp.getStatus()).isEqualTo("DOWN");
        assertThat(resp.getComponents().getBackend().getStatus()).isEqualTo("UP");
        assertThat(resp.getComponents().getDatabase().getStatus()).isEqualTo("DOWN");
    }

    @Test
    void storageStatsScanSubDirectoriesWithoutDoubleCounting(@TempDir Path tempDir) throws Exception {
        write(tempDir, "images/a.png", 10);
        write(tempDir, "images/video-frames/f.jpg", 20);
        write(tempDir, "videos/b.mp4", 30);
        write(tempDir, "videos/composed/c.mp4", 40);
        when(storageConfigService.getDefaultConfig()).thenReturn(null);

        SystemStatusService service = newService(tempDir.toString(), null);
        MediaStorageStatsRespVO resp = service.getMediaStorageStats();

        assertThat(resp.getStorageType()).isEqualTo("local");
        assertThat(resp.getExists()).isTrue();
        assertThat(resp.getCategories().getImages().getFileCount()).isEqualTo(2);
        assertThat(resp.getCategories().getImages().getTotalBytes()).isEqualTo(30);
        assertThat(resp.getCategories().getVideos().getFileCount()).isEqualTo(1);
        assertThat(resp.getCategories().getVideos().getTotalBytes()).isEqualTo(30);
        assertThat(resp.getCategories().getComposed().getFileCount()).isEqualTo(1);
        assertThat(resp.getCategories().getComposed().getTotalBytes()).isEqualTo(40);
        assertThat(resp.getTotalFiles()).isEqualTo(4);
        assertThat(resp.getTotalBytes()).isEqualTo(100);
    }

    @Test
    void storageStatsTolerateMissingDirectories(@TempDir Path tempDir) {
        when(storageConfigService.getDefaultConfig()).thenReturn(null);

        SystemStatusService service = newService(tempDir.resolve("missing").toString(), null);
        MediaStorageStatsRespVO resp = service.getMediaStorageStats();

        assertThat(resp.getExists()).isFalse();
        assertThat(resp.getTotalFiles()).isEqualTo(0);
        assertThat(resp.getTotalBytes()).isEqualTo(0);
    }

    @Test
    void storageStatsExposeS3StorageTypeWhileScanningLocalPath(@TempDir Path tempDir) throws Exception {
        write(tempDir, "images/a.png", 10);
        when(storageConfigService.getDefaultConfig()).thenReturn(
                StorageConfig.builder().type("aliyun_oss").build());

        SystemStatusService service = newService(tempDir.toString(), null);
        MediaStorageStatsRespVO resp = service.getMediaStorageStats();

        assertThat(resp.getStorageType()).isEqualTo("s3");
        assertThat(resp.getCategories().getImages().getFileCount()).isEqualTo(1);
    }

    @Test
    void videoQueueAggregatesRegisteredQueues() {
        when(taskQueue.listRegisteredQueuesByPrefix("video_generation"))
                .thenReturn(Set.of("video_generation:model:2", "video_generation"));
        when(taskQueue.getQueueLength("video_generation")).thenReturn(5);
        when(taskQueue.getConcurrentCount("video_generation")).thenReturn(1);
        when(taskQueue.getMaxConcurrent("video_generation")).thenReturn(2);
        when(taskQueue.getQueueLength("video_generation:model:2")).thenReturn(3);
        when(taskQueue.getConcurrentCount("video_generation:model:2")).thenReturn(0);
        when(taskQueue.getMaxConcurrent("video_generation:model:2")).thenReturn(1);

        SystemStatusService service = newService("./data/media", null);
        VideoQueueStatusRespVO resp = service.getVideoQueueStatus();

        assertThat(resp.getAvailable()).isTrue();
        assertThat(resp.getQueues()).hasSize(2);
        assertThat(resp.getTotalPending()).isEqualTo(8);
        assertThat(resp.getTotalRunning()).isEqualTo(1);
    }

    @Test
    void videoQueueDegradesGracefullyWhenRedisUnavailable() {
        when(taskQueue.listRegisteredQueuesByPrefix(anyString()))
                .thenThrow(new RedisConnectionFailureException("cannot connect"));

        SystemStatusService service = newService("./data/media", null);
        VideoQueueStatusRespVO resp = service.getVideoQueueStatus();

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getError()).isNotBlank();
        assertThat(resp.getTotalPending()).isEqualTo(0);
        assertThat(resp.getTotalRunning()).isEqualTo(0);
        assertThat(resp.getQueues()).isEmpty();
    }

    private void write(Path root, String relative, int bytes) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
    }
}

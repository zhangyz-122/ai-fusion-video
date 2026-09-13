package com.stonewu.fusion.service.storage.strategy;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.storage.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SW-T18(P0):LocalStorageStrategy.store 服务端取数旁路的 SSRF 防护用例（红队 S-3 旁路 B）。
 * <p>
 * 该路径由 MediaStorageService.downloadAndStore 调用，下载模型 API 返回的
 * imageUrl/videoUrl；恶意或被劫持的模型端点可让后端 GET 任意内网地址。
 */
class LocalStorageStrategyTests {

    @TempDir
    Path tempDir;

    private final LocalStorageStrategy strategy = new LocalStorageStrategy();

    @Test
    void storeRejectsLoopbackInternalAndMetadataUrlsWithoutTouchingDisk() {
        StorageConfig config = StorageConfig.builder().basePath(tempDir.toString()).build();

        for (String url : List.of(
                "http://127.0.0.1:9200/_cat/indices",
                "http://169.254.169.254/latest/meta-data/",
                "http://100.100.100.200/latest/meta-data/",
                "http://192.168.1.9/model-output.png",
                "http://localhost/model-output.png")) {
            assertThatThrownBy(() -> strategy.store(url, "images", config))
                    .as("远程地址 %s 必须被 SSRF 防护拒绝", url)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SSRF 防护拒绝");
        }

        // 拒绝动作发生在任何磁盘写入之前
        try (Stream<Path> entries = Files.list(tempDir)) {
            assertThat(entries).isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

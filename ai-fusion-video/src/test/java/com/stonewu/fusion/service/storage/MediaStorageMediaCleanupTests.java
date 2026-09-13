package com.stonewu.fusion.service.storage;

import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.service.storage.strategy.LocalStorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * /media 文件清理单元测试：本地策略的 URL→路径解析与越界防护，
 * 以及门面的空白过滤、失败隔离（单个文件失败不阻断其余清理）。
 */
class MediaStorageMediaCleanupTests {

    @TempDir
    Path tempDir;

    private LocalStorageStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LocalStorageStrategy();
    }

    private StorageConfig configWithTempBasePath() {
        StorageConfig config = new StorageConfig();
        config.setBasePath(tempDir.toString());
        return config;
    }

    private void writeFile(String relative, String content) throws IOException {
        Path target = tempDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    // ========== LocalStorageStrategy.deleteByMediaUrl ==========

    @Test
    void deletesFileResolvedUnderConfiguredBasePath() throws IOException {
        writeFile("images/a.png", "media");
        boolean removed = strategy.deleteByMediaUrl("/media/images/a.png", configWithTempBasePath());

        assertThat(removed).isTrue();
        assertThat(tempDir.resolve("images/a.png")).doesNotExist();
    }

    @Test
    void rejectsTraversalOutsideStorageRootWithoutDeleting() throws IOException {
        // ../ 逃逸存储根目录：必须拒绝，且不触碰根目录外的真实文件
        Path outside = Files.createTempFile("afv-outside-", ".png");
        try {
            String url = "/media/../" + outside.getFileName();
            boolean removed = strategy.deleteByMediaUrl(url, configWithTempBasePath());

            assertThat(removed).isFalse();
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void returnsFalseForForeignUrlsAndMissingFiles() {
        // 非 /media 前缀（外链、data:）不是本策略产物
        assertThat(strategy.deleteByMediaUrl("https://cdn.example.com/a.png", configWithTempBasePath())).isFalse();
        assertThat(strategy.deleteByMediaUrl("data:image/png;base64,xxx", configWithTempBasePath())).isFalse();
        assertThat(strategy.deleteByMediaUrl(null, configWithTempBasePath())).isFalse();
        assertThat(strategy.deleteByMediaUrl("  ", configWithTempBasePath())).isFalse();
        // /media 前缀但文件不存在：幂等返回 false（默认 basePath 下无此文件）
        assertThat(strategy.deleteByMediaUrl("/media/images/missing.png", configWithTempBasePath())).isFalse();
        assertThat(strategy.deleteByMediaUrl("/media/images/missing.png", null)).isFalse();
    }

    // ========== MediaStorageService.deleteByMediaUrls ==========

    @Test
    void facadeFiltersBlankEntriesAndDelegatesToLocalStrategy() {
        StorageConfigService configService = mock(StorageConfigService.class);
        LocalStorageStrategy local = mock(LocalStorageStrategy.class);
        MediaStorageService service = new MediaStorageService(configService, List.of(), local);
        StorageConfig config = new StorageConfig();
        when(configService.getDefaultConfig()).thenReturn(config);
        when(local.deleteByMediaUrl("/media/images/a.png", config)).thenReturn(true);

        service.deleteByMediaUrls(java.util.Arrays.asList("/media/images/a.png", "  ", null));

        // 空白与 null 元素被门面过滤，只有有效 URL 下发到策略
        verify(local).deleteByMediaUrl("/media/images/a.png", config);
        verifyNoMoreInteractions(local);
    }

    @Test
    void facadeSwallowsSingleFileFailureAndContinuesRest() {
        StorageConfigService configService = mock(StorageConfigService.class);
        LocalStorageStrategy local = mock(LocalStorageStrategy.class);
        MediaStorageService service = new MediaStorageService(configService, List.of(), local);
        when(configService.getDefaultConfig()).thenReturn(new StorageConfig());
        when(local.deleteByMediaUrl(eq("/media/images/broken.png"), any()))
                .thenThrow(new RuntimeException("磁盘只读"));
        when(local.deleteByMediaUrl(eq("/media/images/ok.png"), any())).thenReturn(true);

        // 第一个文件失败不阻断后续清理，也不向外抛出
        service.deleteByMediaUrls(List.of("/media/images/broken.png", "/media/images/ok.png"));

        verify(local).deleteByMediaUrl(eq("/media/images/ok.png"), any());
    }

    @Test
    void facadeToleratesEmptyAndNullCollections() {
        StorageConfigService configService = mock(StorageConfigService.class);
        LocalStorageStrategy local = mock(LocalStorageStrategy.class);
        MediaStorageService service = new MediaStorageService(configService, List.of(), local);

        service.deleteByMediaUrls(List.of());
        service.deleteByMediaUrls(null);

        // 空集合短路：不解析默认配置，也不触碰任何策略
        verify(configService, never()).getDefaultConfig();
        verifyNoInteractions(local);
    }
}

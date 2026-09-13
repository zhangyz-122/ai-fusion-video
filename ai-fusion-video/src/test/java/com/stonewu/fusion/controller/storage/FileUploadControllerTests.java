package com.stonewu.fusion.controller.storage;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.storage.StorageTypes;
import com.stonewu.fusion.service.system.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FileUploadControllerTests {

    private final MediaStorageService mediaStorageService = mock(MediaStorageService.class);
    private final StorageConfigService storageConfigService = mock(StorageConfigService.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final FileUploadController controller = new FileUploadController(
            mediaStorageService, storageConfigService, systemConfigService, aiModelService);

    @Test
    void uploadsSupportedUrlInputAndReturnsPublicUrl() throws Exception {
        AiModel model = enabledModel(List.of("image"), Map.of("image", List.of("url")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", "image".getBytes());
        when(aiModelService.getById(7L)).thenReturn(model);
        when(storageConfigService.getDefaultConfig()).thenReturn(
                StorageConfig.builder().type(StorageTypes.S3).build());
        when(mediaStorageService.storeBytes(file.getBytes(), "assistant/image", "png"))
                .thenReturn("stored/sample.png");
        when(systemConfigService.resolvePublicUrl("stored/sample.png"))
                .thenReturn("https://cdn.example.com/sample.png");

        var result = controller.uploadAssistantInput(file, 7L, "url");

        assertThat(result.getData()).isEqualTo("https://cdn.example.com/sample.png");
        verify(mediaStorageService).storeBytes(file.getBytes(), "assistant/image", "png");
    }

    @Test
    void rejectsUrlUploadWhenModelOnlySupportsBase64() {
        AiModel model = enabledModel(List.of("image"), Map.of("image", List.of("base64")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", "image".getBytes());
        when(aiModelService.getById(8L)).thenReturn(model);

        assertThatThrownBy(() -> controller.uploadAssistantInput(file, 8L, "url"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 image 的 url 输入");
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void persistsBase64InputForMessagePreviewWithoutPublicSiteUrl() throws Exception {
        AiModel model = enabledModel(List.of("image"), Map.of("image", List.of("base64")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", "image".getBytes());
        when(aiModelService.getById(9L)).thenReturn(model);
        when(storageConfigService.getDefaultConfig()).thenReturn(
                StorageConfig.builder().type(StorageTypes.LOCAL).build());
        when(mediaStorageService.storeBytes(file.getBytes(), "assistant/image", "png"))
                .thenReturn("/media/assistant/image/sample.png");

        var result = controller.uploadAssistantInput(file, 9L, "base64");

        assertThat(result.getData()).isEqualTo("/media/assistant/image/sample.png");
        verify(mediaStorageService).storeBytes(file.getBytes(), "assistant/image", "png");
        verifyNoInteractions(systemConfigService);
    }

    @Test
    void rejectsLocalUrlUploadWithoutExplicitPublicResourceUrl() {
        AiModel model = enabledModel(List.of("image"), Map.of("image", List.of("url")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", "image".getBytes());
        when(aiModelService.getById(10L)).thenReturn(model);
        when(storageConfigService.getDefaultConfig()).thenReturn(
                StorageConfig.builder().type(StorageTypes.LOCAL).build());
        when(systemConfigService.getPublicResourceBaseUrl()).thenReturn(null);

        assertThatThrownBy(() -> controller.uploadAssistantInput(file, 10L, "url"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("后端资源公网地址");

        verifyNoInteractions(mediaStorageService);
    }

    // ---- SW-T19(P0):/upload 上传链路加固（红队 A-3 / A-4 / A-6） ----

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    private static final byte[] GIF_MAGIC = new byte[]{
            'G', 'I', 'F', '8', '9', 'a', 0x00, 0x00};

    @Test
    void uploadRejectsSubDirectoryTraversalAttempts() {
        for (String subDir : List.of(
                "../../etc",
                "..\\..\\windows",
                "/etc",
                "C:\\Windows",
                "a/../b",
                "images/../..")) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.png", "image/png", PNG_MAGIC);
            assertThatThrownBy(() -> controller.upload(file, subDir))
                    .as("subDir %s 必须被拒绝", subDir)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("非法存储子目录");
        }
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void uploadAcceptsNormalSubDirectoryAndStreamsToStoreFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", PNG_MAGIC);
        when(mediaStorageService.storeFile(any(Path.class), eq("art-refs"), eq("png")))
                .thenReturn("/media/art-refs/abc.png");

        var result = controller.upload(file, "art-refs");

        assertThat(result.getData()).isEqualTo("/media/art-refs/abc.png");
        // 流式路径：不再调用 storeBytes（红队 P-4 内存放大修复的配套断言）
        verify(mediaStorageService).storeFile(any(Path.class), eq("art-refs"), eq("png"));
        verifyNoMoreInteractions(mediaStorageService);
    }

    @Test
    void uploadDerivesExtensionFromContentTypeInsteadOfFilename() {
        // 红队 A-4 攻击载荷：x.html + Content-Type: image/png → 落盘必须是 .png 而非 .html
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.html", "image/png", PNG_MAGIC);
        when(mediaStorageService.storeFile(any(Path.class), eq("uploads"), eq("png")))
                .thenReturn("/media/uploads/abc.png");

        var result = controller.upload(file, "uploads");

        assertThat(result.getData()).isEqualTo("/media/uploads/abc.png");
        verify(mediaStorageService).storeFile(any(Path.class), eq("uploads"), eq("png"));
    }

    @Test
    void uploadRejectsFileWhoseBytesDoNotMatchDeclaredImageType() {
        // 伪造 PNG：Content-Type 声明 image/png 但内容是 HTML
        MockMultipartFile fakePng = new MockMultipartFile(
                "file", "x.png", "image/png", "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> controller.upload(fakePng, "uploads"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("与声明的图片格式不符");
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void uploadRejectsContentAndTypeMismatchAcrossImageFormats() {
        // WebP 字节 + Content-Type: image/gif → 扩展名/类型绑定与魔数双重校验必须同时拦下
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P'};
        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "x.gif", "image/gif", webpBytes);

        assertThatThrownBy(() -> controller.upload(mismatched, "uploads"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("与声明的图片格式不符");
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void uploadAcceptsGifMagicForGifContentType() {
        MockMultipartFile gif = new MockMultipartFile(
                "file", "anim.gif", "image/gif", GIF_MAGIC);
        when(mediaStorageService.storeFile(any(Path.class), eq("uploads"), eq("gif")))
                .thenReturn("/media/uploads/abc.gif");

        var result = controller.upload(gif, "uploads");

        assertThat(result.getData()).isEqualTo("/media/uploads/abc.gif");
        verify(mediaStorageService).storeFile(any(Path.class), eq("uploads"), eq("gif"));
    }

    @Test
    void uploadRejectsUnsupportedContentType() {
        MockMultipartFile html = new MockMultipartFile(
                "file", "page.html", "text/html", "<html></html>".getBytes());

        assertThatThrownBy(() -> controller.upload(html, "uploads"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持图片格式");
        verifyNoInteractions(mediaStorageService);
    }

    private AiModel enabledModel(List<String> types, Map<String, List<String>> transports) {
        return AiModel.builder()
                .modelType(1)
                .status(1)
                .multimodalInputTypes(types)
                .multimodalInputTransports(transports)
                .build();
    }
}

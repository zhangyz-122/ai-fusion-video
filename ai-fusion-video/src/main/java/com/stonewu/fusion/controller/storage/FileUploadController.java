package com.stonewu.fusion.controller.storage;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.service.ai.AiModelMultimodalCapabilities;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.storage.StorageTypes;
import com.stonewu.fusion.service.system.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * 通用文件上传 Controller
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    /**
     * 扩展名由 Content-Type 白名单映射决定，与客户端提供的文件名解耦
     * （红队 A-4：文件名扩展走私 x.html + Content-Type: image/png → /media 存储型 XSS）。
     */
    private static final Map<String, String> IMAGE_CONTENT_TYPE_TO_EXT = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final Map<String, String> ASSISTANT_UPLOAD_TYPES = Map.ofEntries(
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/jpg", "jpg"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("video/mpeg", "mpeg"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/flac", "flac"),
            Map.entry("audio/aac", "aac"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("text/plain", "txt"),
            Map.entry("text/markdown", "md"),
            Map.entry("text/csv", "csv"),
            Map.entry("application/json", "json")
    );
    /** 魔数检测所需的最大头部长度（WebP 需要 RIFF....WEBP 共 12 字节）。 */
    private static final int MAGIC_HEADER_LENGTH = 12;

    private final MediaStorageService mediaStorageService;
    private final StorageConfigService storageConfigService;
    private final SystemConfigService systemConfigService;
    private final AiModelService aiModelService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public CommonResult<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subDir", defaultValue = "uploads") String subDir) {

        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 100MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        // 扩展名与 Content-Type 绑定：文件名扩展不参与落盘决策
        String extension = IMAGE_CONTENT_TYPE_TO_EXT.get(contentType);
        if (extension == null) {
            throw new BusinessException("仅支持图片格式：PNG, JPEG, WebP, GIF");
        }
        validateSubDir(subDir);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("afv-upload-", "." + extension);
            try (InputStream in = new BufferedInputStream(file.getInputStream())) {
                // 魔数校验：声明类型必须与真实文件头一致（红队 A-4/A-6）
                in.mark(MAGIC_HEADER_LENGTH);
                byte[] header = in.readNBytes(MAGIC_HEADER_LENGTH);
                requireImageMagic(header, contentType);
                in.reset();
                // 全程流式落盘，不将文件整体读入堆内存
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            String url = mediaStorageService.storeFile(tempFile, subDir, extension);
            log.info("[FileUpload] 上传成功: size={}KB, url={}", file.getSize() / 1024, url);
            return CommonResult.success(url);
        } catch (IOException e) {
            log.error("[FileUpload] 上传失败", e);
            throw new BusinessException("上传失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    log.warn("[FileUpload] 临时文件清理失败: {}", tempFile, cleanupError);
                }
            }
        }
    }

    /**
     * 校验用户可控的 subDir：拒绝目录穿越、绝对路径、盘符与非法字符（红队 A-3）。
     */
    private void validateSubDir(String subDir) {
        String message = "非法存储子目录: " + subDir;
        if (StrUtil.isBlank(subDir)) {
            throw new BusinessException(message);
        }
        String normalized = subDir.trim().replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.contains(":")
                || !normalized.matches("[a-zA-Z0-9][a-zA-Z0-9_/-]*")
                || Arrays.stream(normalized.split("/"))
                        .anyMatch(part -> part.isEmpty() || ".".equals(part) || "..".equals(part))) {
            throw new BusinessException(message);
        }
    }

    /**
     * 按声明的 Content-Type 校验图片魔数，防止伪装成图片的可执行/任意文件落盘。
     */
    private void requireImageMagic(byte[] header, String contentType) {
        boolean matches = switch (contentType) {
            case "image/png" -> startsWith(header, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg", "image/jpg" -> startsWith(header, new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> header.length >= MAGIC_HEADER_LENGTH
                    && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && startsWith(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
        if (!matches) {
            throw new BusinessException("文件内容与声明的图片格式不符");
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, 0, prefix);
    }

    private boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    @PostMapping("/assistant-upload")
    @Operation(summary = "上传助手多模态输入")
    public CommonResult<String> uploadAssistantInput(
            @RequestParam("file") MultipartFile file,
            @RequestParam("modelId") Long modelId,
            @RequestParam(value = "transport", defaultValue = "url") String transport) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 100MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        String extension = ASSISTANT_UPLOAD_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException("不支持的助手输入格式: " + contentType);
        }
        AiModel model = aiModelService.getById(modelId);
        String normalizedTransport = transport.trim().toLowerCase(Locale.ROOT);
        String inputType = AiModelMultimodalCapabilities.validateUpload(
                model, contentType, normalizedTransport);
        if (AiModelMultimodalCapabilities.TRANSPORT_URL.equals(normalizedTransport)) {
            requirePublicUploadStorage();
        }

        Path tempFile = null;
        try {
            // 流式落盘：不再把最大 100MB 的文件整体读入堆（红队 P-4 内存放大）
            tempFile = Files.createTempFile("afv-assistant-", "." + extension);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            String storedUrl = mediaStorageService.storeFile(
                    tempFile, "assistant/" + inputType, extension);
            if (AiModelMultimodalCapabilities.TRANSPORT_BASE64.equals(normalizedTransport)) {
                log.info("[FileUpload] 助手回显资源上传成功: modelId={}, type={}, size={}KB, url={}",
                        modelId, inputType, file.getSize() / 1024, storedUrl);
                return CommonResult.success(storedUrl);
            }
            String publicUrl = systemConfigService.resolvePublicUrl(storedUrl);
            if (publicUrl == null) {
                throw new BusinessException("URL 输入需要公开可访问的对象存储，或在系统设置中配置后端资源公网地址");
            }
            log.info("[FileUpload] 助手输入上传成功: modelId={}, type={}, size={}KB, url={}",
                    modelId, inputType, file.getSize() / 1024, publicUrl);
            return CommonResult.success(publicUrl);
        } catch (IOException e) {
            log.error("[FileUpload] 助手输入上传失败", e);
            throw new BusinessException("上传失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    log.warn("[FileUpload] 临时文件清理失败: {}", tempFile, cleanupError);
                }
            }
        }
    }

    private void requirePublicUploadStorage() {
        StorageConfig config = storageConfigService.getDefaultConfig();
        boolean localStorage = config == null || !StorageTypes.isS3Like(config.getType());
        if (localStorage && StrUtil.isBlank(systemConfigService.getPublicResourceBaseUrl())) {
            throw new BusinessException("本地存储用于 URL 输入时，必须先在系统设置中配置后端资源公网地址");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

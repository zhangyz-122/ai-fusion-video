package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.service.ai.proxy.AiProxySupport;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Media loading for OpenAI-compatible image protocols: turns reference-image
 * sources (data URLs, local media, preset art styles, files, HTTP URLs) into
 * in-memory binary resources and derives MIME types / file extensions.
 * Extracted verbatim from {@link OpenAiCompatibleImageProtocolSupport};
 * behavior is unchanged.
 */
public class ImageMediaLoader {

    private static final String DEFAULT_LOCAL_MEDIA_BASE_PATH = "./data/media";

    private final StorageConfigService storageConfigService;
    private final PresetArtStyleResourceResolver presetArtStyleResourceResolver;
    private final OkHttpClient okHttpClient;

    public ImageMediaLoader(StorageConfigService storageConfigService,
                            PresetArtStyleResourceResolver presetArtStyleResourceResolver,
                            OkHttpClient okHttpClient) {
        this.storageConfigService = storageConfigService;
        this.presetArtStyleResourceResolver = presetArtStyleResourceResolver;
        this.okHttpClient = okHttpClient;
    }

    public BinaryResource loadBinaryResource(String sourceUrl, ApiConfig apiConfig) throws IOException {
        if (StrUtil.isBlank(sourceUrl)) throw new BusinessException("OpenAI 参考图地址为空");
        String trimmed = sourceUrl.trim();
        if (StrUtil.startWithIgnoreCase(trimmed, "data:")) return parseDataUrl(trimmed);
        if (trimmed.startsWith("/media/")) return loadLocalMedia(trimmed);
        if (presetArtStyleResourceResolver != null && presetArtStyleResourceResolver.isPresetArtStylePath(trimmed)) {
            var resource = presetArtStyleResourceResolver.load(trimmed);
            return new BinaryResource(resource.bytes(), resource.mimeType(), extensionFromMimeType(resource.mimeType()));
        }
        if (trimmed.startsWith("file:")) return loadFile(Paths.get(URI.create(trimmed)));
        if (OpenAiCompatibleImageProtocolUtil.isHttpUrl(trimmed)) {
            Request request = new Request.Builder().url(trimmed).get()
                    .addHeader("Accept", "image/*,*/*;q=0.8").build();
            OkHttpClient client = AiProxySupport.okHttpClient(okHttpClient, apiConfig);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("下载参考图失败: HTTP " + response.code() + " url=" + trimmed);
                }
                String mimeType = normalizeMimeType(response.header("Content-Type"), trimmed);
                return new BinaryResource(response.body().bytes(), mimeType, extensionFromMimeType(mimeType));
            }
        }
        Path localPath = Paths.get(trimmed);
        if (Files.exists(localPath) && Files.isRegularFile(localPath)) return loadFile(localPath);
        throw new BusinessException("参考图地址不可访问: " + trimmed);
    }

    public MediaType mediaTypeOrDefault(String mimeType) {
        try {
            return MediaType.get(StrUtil.blankToDefault(mimeType, "image/png"));
        } catch (Exception ignored) {
            return MediaType.get("application/octet-stream");
        }
    }

    private BinaryResource parseDataUrl(String sourceUrl) {
        int commaIndex = sourceUrl.indexOf(',');
        if (commaIndex <= 0) throw new BusinessException("OpenAI 参考图 data URL 格式非法");
        String metadata = sourceUrl.substring(0, commaIndex);
        String payload = sourceUrl.substring(commaIndex + 1);
        String mimeType = normalizeMimeType(metadata.substring("data:".length()), sourceUrl);
        try {
            byte[] bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
            return new BinaryResource(bytes, mimeType, extensionFromMimeType(mimeType));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("OpenAI 参考图 data URL base64 非法: " + e.getMessage());
        }
    }

    private BinaryResource loadLocalMedia(String sourceUrl) throws IOException {
        String relativePath = sourceUrl.replaceFirst("^/media/?", "");
        List<Path> candidates = new ArrayList<>();
        StorageConfig config = storageConfigService == null ? null : storageConfigService.getDefaultConfig();
        if (config != null && StrUtil.isNotBlank(config.getBasePath())) {
            candidates.add(Paths.get(config.getBasePath()).resolve(relativePath));
        }
        candidates.add(Paths.get(DEFAULT_LOCAL_MEDIA_BASE_PATH).resolve(relativePath));
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) return loadFile(candidate);
        }
        throw new BusinessException("本地参考图不存在: " + sourceUrl);
    }

    private BinaryResource loadFile(Path path) throws IOException {
        String mimeType = normalizeMimeType(null, path.getFileName().toString());
        return new BinaryResource(Files.readAllBytes(path), mimeType, extensionFromMimeType(mimeType));
    }

    private String normalizeMimeType(String contentType, String sourceUrl) {
        if (StrUtil.isNotBlank(contentType)) return contentType.split(";", 2)[0].trim();
        String lower = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    private String extensionFromMimeType(String mimeType) {
        return switch (normalizeMimeType(mimeType, mimeType).toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    /** Bytes plus the MIME type and file extension resolved for a reference image. */
    public record BinaryResource(byte[] bytes, String mimeType, String extension) {
    }
}

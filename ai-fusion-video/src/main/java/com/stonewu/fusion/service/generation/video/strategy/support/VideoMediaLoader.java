package com.stonewu.fusion.service.generation.video.strategy.support;

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
import java.util.concurrent.TimeUnit;

/**
 * Media loading for OpenAI-compatible video protocols: turns reference-image
 * sources (data URLs, local media, preset art styles, files, HTTP URLs) into
 * in-memory binary resources and derives MIME types / file extensions.
 * Extracted verbatim from {@link OpenAiCompatibleVideoProtocolSupport};
 * behavior is unchanged.
 */
public class VideoMediaLoader {

    private static final String DEFAULT_LOCAL_MEDIA_BASE_PATH = "./data/media";

    private final StorageConfigService storageConfigService;
    private final PresetArtStyleResourceResolver presetArtStyleResourceResolver;

    private final OkHttpClient resourceHttpClient = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(25, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public VideoMediaLoader(StorageConfigService storageConfigService,
                            PresetArtStyleResourceResolver presetArtStyleResourceResolver) {
        this.storageConfigService = storageConfigService;
        this.presetArtStyleResourceResolver = presetArtStyleResourceResolver;
    }

    public OpenAiCompatibleVideoProtocolSupport.BinaryResource loadBinaryResource(String sourceUrl, ApiConfig apiConfig) throws IOException {
        if (StrUtil.isBlank(sourceUrl)) {
            throw new BusinessException("OpenAI 视频参考图地址为空");
        }
        String trimmed = sourceUrl.trim();
        if (trimmed.startsWith("data:")) {
            return parseDataUrl(trimmed);
        }
        if (trimmed.startsWith("/media/")) {
            return loadLocalMedia(trimmed);
        }
        if (presetArtStyleResourceResolver.isPresetArtStylePath(trimmed)) {
            PresetArtStyleResourceResolver.PresetArtStyleResource resource = presetArtStyleResourceResolver.load(trimmed);
            return new OpenAiCompatibleVideoProtocolSupport.BinaryResource(
                    resource.bytes(), resource.mimeType(), extensionFromImageMimeType(resource.mimeType()));
        }
        if (trimmed.startsWith("file:")) {
            return loadFile(Paths.get(URI.create(trimmed)));
        }
        if (StrUtil.startWithIgnoreCase(trimmed, "http://") || StrUtil.startWithIgnoreCase(trimmed, "https://")) {
            Request request = new Request.Builder()
                    .url(trimmed)
                    .get()
                    .addHeader("Accept", "image/*,*/*;q=0.8")
                    .build();
            OkHttpClient client = AiProxySupport.okHttpClient(resourceHttpClient, apiConfig);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("下载视频参考图失败: HTTP " + response.code() + " url=" + trimmed);
                }
                String mimeType = normalizeImageMimeType(response.header("Content-Type"), trimmed);
                return new OpenAiCompatibleVideoProtocolSupport.BinaryResource(
                        response.body().bytes(), mimeType, extensionFromImageMimeType(mimeType));
            }
        }

        Path localPath = Paths.get(trimmed);
        if (Files.exists(localPath) && Files.isRegularFile(localPath)) {
            return loadFile(localPath);
        }
        throw new BusinessException("视频参考图地址不可访问: " + trimmed);
    }

    public MediaType mediaTypeOrDefault(String mimeType) {
        try {
            return MediaType.get(StrUtil.blankToDefault(mimeType, "image/png"));
        } catch (Exception ignored) {
            return MediaType.get("application/octet-stream");
        }
    }

    public String extensionFromImageMimeType(String mimeType) {
        String normalized = normalizeImageMimeType(mimeType, mimeType).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    public String extensionFromVideoMimeType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return "mp4";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            case "video/x-matroska" -> "mkv";
            default -> "mp4";
        };
    }

    private OpenAiCompatibleVideoProtocolSupport.BinaryResource parseDataUrl(String sourceUrl) {
        int commaIndex = sourceUrl.indexOf(',');
        if (commaIndex <= 0) {
            throw new BusinessException("OpenAI 视频参考图 data URL 格式非法");
        }
        String metadata = sourceUrl.substring(0, commaIndex);
        String payload = sourceUrl.substring(commaIndex + 1);
        String mimeType = normalizeImageMimeType(metadata.substring("data:".length()), sourceUrl);
        try {
            byte[] bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
            return new OpenAiCompatibleVideoProtocolSupport.BinaryResource(bytes, mimeType, extensionFromImageMimeType(mimeType));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("OpenAI 视频参考图 data URL base64 非法: " + e.getMessage());
        }
    }

    private OpenAiCompatibleVideoProtocolSupport.BinaryResource loadLocalMedia(String sourceUrl) throws IOException {
        String relativePath = sourceUrl.replaceFirst("^/media/?", "");
        List<Path> candidates = new ArrayList<>();
        StorageConfig config = storageConfigService.getDefaultConfig();
        if (config != null && StrUtil.isNotBlank(config.getBasePath())) {
            candidates.add(Paths.get(config.getBasePath()).resolve(relativePath));
        }
        candidates.add(Paths.get(DEFAULT_LOCAL_MEDIA_BASE_PATH).resolve(relativePath));

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return loadFile(candidate);
            }
        }
        throw new BusinessException("本地视频参考图不存在: " + sourceUrl);
    }

    private OpenAiCompatibleVideoProtocolSupport.BinaryResource loadFile(Path path) throws IOException {
        String mimeType = normalizeImageMimeType(null, path.getFileName().toString());
        return new OpenAiCompatibleVideoProtocolSupport.BinaryResource(
                Files.readAllBytes(path), mimeType, extensionFromImageMimeType(mimeType));
    }

    private String normalizeImageMimeType(String contentType, String sourceUrl) {
        if (StrUtil.isNotBlank(contentType)) {
            return contentType.split(";", 2)[0].trim();
        }
        String lower = sourceUrl == null ? "" : sourceUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }
}

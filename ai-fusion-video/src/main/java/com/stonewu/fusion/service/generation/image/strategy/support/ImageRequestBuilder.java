package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ApiConfig;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.Locale;

/**
 * HTTP request construction for OpenAI-compatible image protocols: JSON and
 * multipart bodies for generations/edits plus endpoint URL resolution.
 * Extracted verbatim from {@link OpenAiCompatibleImageProtocolSupport};
 * behavior is unchanged.
 */
public class ImageRequestBuilder {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final String DEFAULT_IMAGE_MODEL = "gpt-image-1";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImageSizeMapper sizeMapper;
    private final ImageMediaLoader mediaLoader;

    public ImageRequestBuilder(ImageSizeMapper sizeMapper, ImageMediaLoader mediaLoader) {
        this.sizeMapper = sizeMapper;
        this.mediaLoader = mediaLoader;
    }

    /** Build a request containing only fields defined by the official OpenAI Images API. */
    public OpenAiCompatibleImageRequest buildOpenAiGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        try {
            var root = OBJECT_MAPPER.createObjectNode();
            root.put("prompt", StrUtil.blankToDefault(context.prompt(), ""));
            root.put("model", StrUtil.blankToDefault(context.modelCode(), DEFAULT_IMAGE_MODEL));
            root.put("n", Math.max(context.count(), 1));
            root.put("size", sizeMapper.mapSize(context.modelCode(), context.width(), context.height(), context.modelConfig()));
            appendOptionalString(root, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
            appendOptionalString(root, "background", getString(context.modelConfig(), "background"));
            appendOptionalString(root, "moderation", getString(context.modelConfig(), "moderation"));
            appendOptionalString(root, "output_format", getString(context.modelConfig(), "outputFormat", "output_format"));
            appendOptionalInteger(root, "output_compression",
                    getInteger(context.modelConfig(), "outputCompression", "output_compression"));
            appendOptionalString(root, "response_format",
                    getString(context.modelConfig(), "responseFormat", "response_format"));
            appendOptionalString(root, "style", getString(context.modelConfig(), "style"));
            appendOptionalString(root, "user", getString(context.modelConfig(), "user", "endUser", "end_user"));
            return jsonRequest(resolveImagesGenerateUrl(context.apiConfig()), root);
        } catch (Exception e) {
            throw new RuntimeException("构建 OpenAI 图片请求失败: " + e.getMessage(), e);
        }
    }

    /** Build an OpenAI-shaped request with NewAPI-compatible extension fields. */
    public OpenAiCompatibleImageRequest buildNewApiGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        try {
            var root = OBJECT_MAPPER.createObjectNode();
            root.put("prompt", StrUtil.blankToDefault(context.prompt(), ""));
            root.put("model", StrUtil.blankToDefault(context.modelCode(), DEFAULT_IMAGE_MODEL));
            root.put("n", Math.max(context.count(), 1));
            root.put("size", sizeMapper.mapSize(context.modelCode(), context.width(), context.height(), context.modelConfig()));
            if (context.hasReferenceImages()) {
                var imageUrlArray = root.putArray("image_urls");
                context.imageUrls().stream()
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .forEach(imageUrlArray::add);
            }
            appendOptionalString(root, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
            appendOptionalString(root, "resolution", getString(context.modelConfig(), "resolution", "defaultResolution"));
            appendOptionalString(root, "background", getString(context.modelConfig(), "background"));
            appendOptionalString(root, "moderation", getString(context.modelConfig(), "moderation"));
            appendOptionalString(root, "output_format", getString(context.modelConfig(), "outputFormat", "output_format"));
            appendOptionalInteger(root, "output_compression",
                    getInteger(context.modelConfig(), "outputCompression", "output_compression"));
            appendOptionalString(root, "response_format",
                    getString(context.modelConfig(), "responseFormat", "response_format"));
            appendOptionalString(root, "mask_url", getString(context.modelConfig(), "maskUrl", "mask_url"));
            appendOptionalString(root, "style", getString(context.modelConfig(), "style"));
            return jsonRequest(resolveImagesGenerateUrl(context.apiConfig()), root);
        } catch (Exception e) {
            throw new RuntimeException("构建 NewAPI 图片请求失败: " + e.getMessage(), e);
        }
    }

    /** Build the shared Agnes Image 2.0/2.1 generations protocol. */
    public OpenAiCompatibleImageRequest buildAgnesGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        try {
            var root = OBJECT_MAPPER.createObjectNode();
            root.put("model", StrUtil.blankToDefault(context.modelCode(), DEFAULT_IMAGE_MODEL));
            root.put("prompt", StrUtil.blankToDefault(context.prompt(), ""));

            ImageSizeMapper.AgnesImageSize requestSize = sizeMapper.resolveAgnesImageSize(
                    context.modelCode(), context.width(), context.height(), context.modelConfig());
            root.put("size", requestSize.size());
            if (StrUtil.isNotBlank(requestSize.ratio())) {
                root.put("ratio", requestSize.ratio());
            }

            var extraBody = OBJECT_MAPPER.createObjectNode();
            if (context.hasReferenceImages()) {
                var imageArray = extraBody.putArray("image");
                context.imageUrls().stream()
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .map(imageUrl -> resolveAgnesImageInput(imageUrl, context.modelCode()))
                        .forEach(imageArray::add);
            }

            String responseFormat = StrUtil.blankToDefault(
                    getString(context.modelConfig(), "agnesResponseFormat", "responseFormat", "response_format"),
                    "url");
            boolean returnBase64 = Boolean.TRUE.equals(getBoolean(context.modelConfig(),
                    "returnBase64", "return_base64")) || "b64_json".equalsIgnoreCase(responseFormat);
            if (returnBase64 && !context.hasReferenceImages()) {
                root.put("return_base64", true);
            } else {
                extraBody.put("response_format", returnBase64 ? "b64_json" : responseFormat.trim());
            }
            if (!extraBody.isEmpty()) {
                root.set("extra_body", extraBody);
            }
            return jsonRequest(resolveImagesGenerateUrl(context.apiConfig()), root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("构建 Agnes 图片请求失败: " + e.getMessage(), e);
        }
    }

    /** Build an official OpenAI image edit request using the model's declared reference-image transports. */
    public OpenAiCompatibleImageRequest buildOpenAiEditsRequest(OpenAiCompatibleImageProtocolContext context) {
        if (supportsReferenceImageUrlInput(context.modelConfig())) {
            return buildOpenAiJsonEditsRequest(context);
        }
        MultipartBody.Builder builder = createEditsRequestBuilder(context);
        appendOptionalFormField(builder, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
        appendOptionalFormField(builder, "background", getString(context.modelConfig(), "background"));
        appendOptionalFormField(builder, "moderation", getString(context.modelConfig(), "moderation"));
        appendOptionalFormField(builder, "output_format", getString(context.modelConfig(), "outputFormat", "output_format"));
        appendOptionalFormField(builder, "output_compression",
                getString(context.modelConfig(), "outputCompression", "output_compression"));
        if (!"gpt-image-2".equalsIgnoreCase(StrUtil.trim(context.modelCode()))) {
            appendOptionalFormField(builder, "input_fidelity",
                    getString(context.modelConfig(), "inputFidelity", "input_fidelity"));
        }
        appendOptionalFormField(builder, "user", getString(context.modelConfig(), "user", "endUser", "end_user"));
        appendReferenceImages(builder, context);
        return new OpenAiCompatibleImageRequest(resolveImagesEditUrl(context.apiConfig()), builder.build());
    }

    /** Build an image edit request with fields commonly accepted by NewAPI-compatible gateways. */
    public OpenAiCompatibleImageRequest buildNewApiEditsRequest(OpenAiCompatibleImageProtocolContext context) {
        if (supportsReferenceImageUrlInput(context.modelConfig())) {
            return buildNewApiJsonEditsRequest(context);
        }
        MultipartBody.Builder builder = createEditsRequestBuilder(context);

        appendOptionalFormField(builder, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
        appendOptionalFormField(builder, "resolution", getString(context.modelConfig(), "resolution", "defaultResolution"));
        appendOptionalFormField(builder, "background", getString(context.modelConfig(), "background"));
        appendOptionalFormField(builder, "moderation", getString(context.modelConfig(), "moderation"));
        appendOptionalFormField(builder, "output_format", getString(context.modelConfig(), "outputFormat", "output_format"));
        appendOptionalFormField(builder, "output_compression",
                getString(context.modelConfig(), "outputCompression", "output_compression"));
        appendOptionalFormField(builder, "style", getString(context.modelConfig(), "style"));

        appendReferenceImages(builder, context);
        return new OpenAiCompatibleImageRequest(resolveImagesEditUrl(context.apiConfig()), builder.build());
    }

    public String resolveImagesGenerateUrl(ApiConfig apiConfig) {
        String baseUrl = normalizeBaseUrl(apiConfig != null ? apiConfig.getApiUrl() : null);
        if (endsWithIgnoreCase(baseUrl, "/images/generations")) {
            return baseUrl;
        }
        boolean appendV1 = shouldAutoAppendV1Path(apiConfig);
        if (endsWithIgnoreCase(baseUrl, "/v1")) {
            return baseUrl + "/images/generations";
        }
        return baseUrl + (appendV1 ? "/v1/images/generations" : "/images/generations");
    }

    public String resolveAsyncTaskUrl(ApiConfig apiConfig, JSONObject modelConfig, String taskId) {
        String configuredPath = getString(modelConfig, "asyncTaskStatusPath", "asyncTaskPath", "taskStatusPath");
        if (StrUtil.isNotBlank(configuredPath)) {
            String path = configuredPath.trim()
                    .replace("{task_id}", taskId)
                    .replace("{taskId}", taskId)
                    .replace("{id}", taskId);
            if (OpenAiCompatibleImageProtocolUtil.isHttpUrl(path)) {
                return path;
            }
            String rootUrl = resolveApiRootUrl(apiConfig);
            return rootUrl + (path.startsWith("/") ? path : "/" + path);
        }
        String rootUrl = resolveApiRootUrl(apiConfig);
        boolean appendV1 = shouldAutoAppendV1Path(apiConfig);
        if (endsWithIgnoreCase(rootUrl, "/v1")) {
            return rootUrl + "/tasks/" + taskId;
        }
        return rootUrl + (appendV1 ? "/v1/tasks/" : "/tasks/") + taskId;
    }

    private OpenAiCompatibleImageRequest buildOpenAiJsonEditsRequest(
            OpenAiCompatibleImageProtocolContext context) {
        try {
            ObjectNode root = createJsonEditsRoot(context);
            appendOptionalString(root, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
            appendOptionalString(root, "background", getString(context.modelConfig(), "background"));
            appendOptionalString(root, "moderation", getString(context.modelConfig(), "moderation"));
            appendOptionalString(root, "output_format",
                    getString(context.modelConfig(), "outputFormat", "output_format"));
            appendOptionalInteger(root, "output_compression",
                    getInteger(context.modelConfig(), "outputCompression", "output_compression"));
            if (!"gpt-image-2".equalsIgnoreCase(StrUtil.trim(context.modelCode()))) {
                appendOptionalString(root, "input_fidelity",
                        getString(context.modelConfig(), "inputFidelity", "input_fidelity"));
            }
            appendOptionalString(root, "user", getString(context.modelConfig(), "user", "endUser", "end_user"));
            return jsonRequest(resolveImagesEditUrl(context.apiConfig()), root);
        } catch (IOException e) {
            throw new RuntimeException("构建 OpenAI 图片编辑 JSON 请求失败: " + e.getMessage(), e);
        }
    }

    private OpenAiCompatibleImageRequest buildNewApiJsonEditsRequest(
            OpenAiCompatibleImageProtocolContext context) {
        try {
            ObjectNode root = createJsonEditsRoot(context);
            appendOptionalString(root, "quality", getString(context.modelConfig(), "quality", "imageQuality"));
            appendOptionalString(root, "resolution",
                    getString(context.modelConfig(), "resolution", "defaultResolution"));
            appendOptionalString(root, "background", getString(context.modelConfig(), "background"));
            appendOptionalString(root, "moderation", getString(context.modelConfig(), "moderation"));
            appendOptionalString(root, "output_format",
                    getString(context.modelConfig(), "outputFormat", "output_format"));
            appendOptionalInteger(root, "output_compression",
                    getInteger(context.modelConfig(), "outputCompression", "output_compression"));
            appendOptionalString(root, "style", getString(context.modelConfig(), "style"));
            return jsonRequest(resolveImagesEditUrl(context.apiConfig()), root);
        } catch (IOException e) {
            throw new RuntimeException("构建 NewAPI 图片编辑 JSON 请求失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode createJsonEditsRoot(OpenAiCompatibleImageProtocolContext context) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", StrUtil.blankToDefault(context.modelCode(), DEFAULT_IMAGE_MODEL));
        root.put("prompt", StrUtil.blankToDefault(context.prompt(), ""));
        root.put("n", Math.max(context.count(), 1));
        root.put("size", sizeMapper.mapSize(context.modelCode(), context.width(), context.height(), context.modelConfig()));
        var images = root.putArray("images");
        context.imageUrls().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .forEach(imageUrl -> images.addObject().put("image_url", imageUrl));
        return root;
    }

    private boolean supportsReferenceImageUrlInput(JSONObject config) {
        return OpenAiCompatibleImageProtocolUtil.getStringList(config, "referenceImageInputFormats").stream()
                .map(value -> StrUtil.blankToDefault(value, "").trim())
                .anyMatch("url"::equalsIgnoreCase);
    }

    private MultipartBody.Builder createEditsRequestBuilder(OpenAiCompatibleImageProtocolContext context) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", StrUtil.blankToDefault(context.modelCode(), DEFAULT_IMAGE_MODEL))
                .addFormDataPart("prompt", StrUtil.blankToDefault(context.prompt(), ""))
                .addFormDataPart("n", String.valueOf(Math.max(context.count(), 1)))
                .addFormDataPart("size", sizeMapper.mapSize(context.modelCode(), context.width(), context.height(), context.modelConfig()));
        return builder;
    }

    private void appendReferenceImages(MultipartBody.Builder builder,
                                       OpenAiCompatibleImageProtocolContext context) {
        for (int i = 0; i < context.imageUrls().size(); i++) {
            String imageUrl = context.imageUrls().get(i);
            try {
                ImageMediaLoader.BinaryResource resource = mediaLoader.loadBinaryResource(imageUrl, context.apiConfig());
                builder.addFormDataPart("image[]", "reference-" + (i + 1) + "." + resource.extension(),
                        RequestBody.create(resource.bytes(), mediaLoader.mediaTypeOrDefault(resource.mimeType())));
            } catch (IOException e) {
                throw new RuntimeException("加载 OpenAI 参考图失败: " + e.getMessage(), e);
            }
        }
    }

    private String resolveAgnesImageInput(String imageUrl, String modelCode) {
        String trimmed = StrUtil.blankToDefault(imageUrl, "").trim();
        if (StrUtil.isBlank(trimmed)) {
            throw new BusinessException("Agnes 参考图地址为空");
        }
        if (StrUtil.startWithIgnoreCase(trimmed, "data:") || OpenAiCompatibleImageProtocolUtil.isHttpUrl(trimmed)) {
            return trimmed;
        }
        throw unsupportedReferenceImageInput(modelCode,
                "参考图尚未按模型能力解析为公网 URL 或 base64/Data URI");
    }

    private BusinessException unsupportedReferenceImageInput(String modelCode, String reason) {
        return new BusinessException("图片模型 " + StrUtil.blankToDefault(modelCode, "Agnes")
                + " 无法使用当前参考图：" + reason + "。");
    }

    private OpenAiCompatibleImageRequest jsonRequest(String url, ObjectNode root)
            throws IOException {
        return new OpenAiCompatibleImageRequest(url,
                RequestBody.create(OBJECT_MAPPER.writeValueAsString(root), JSON_MEDIA_TYPE));
    }

    private String resolveImagesEditUrl(ApiConfig apiConfig) {
        String baseUrl = normalizeBaseUrl(apiConfig != null ? apiConfig.getApiUrl() : null);
        if (endsWithIgnoreCase(baseUrl, "/images/edits")) return baseUrl;
        if (endsWithIgnoreCase(baseUrl, "/v1")) return baseUrl + "/images/edits";
        return baseUrl + (shouldAutoAppendV1Path(apiConfig) ? "/v1/images/edits" : "/images/edits");
    }

    private String resolveApiRootUrl(ApiConfig apiConfig) {
        String baseUrl = normalizeBaseUrl(apiConfig != null ? apiConfig.getApiUrl() : null);
        if (endsWithIgnoreCase(baseUrl, "/images/generations")) {
            return baseUrl.substring(0, baseUrl.length() - "/images/generations".length());
        }
        if (endsWithIgnoreCase(baseUrl, "/images/edits")) {
            return baseUrl.substring(0, baseUrl.length() - "/images/edits".length());
        }
        return baseUrl;
    }

    private boolean shouldAutoAppendV1Path(ApiConfig apiConfig) {
        if (apiConfig == null) return true;
        if (!"openai_compatible".equalsIgnoreCase(apiConfig.getPlatform())) return true;
        return !Boolean.FALSE.equals(apiConfig.getAutoAppendV1Path());
    }

    private String normalizeBaseUrl(String baseUrl) {
        return StrUtil.blankToDefault(baseUrl, DEFAULT_BASE_URL).trim().replaceAll("/+$", "");
    }

    private boolean endsWithIgnoreCase(String text, String suffix) {
        return text != null && suffix != null && text.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
    }

    private String getString(JSONObject config, String... keys) {
        return OpenAiCompatibleImageProtocolUtil.getString(config, keys);
    }

    private Integer getInteger(JSONObject config, String... keys) {
        return OpenAiCompatibleImageProtocolUtil.getInteger(config, keys);
    }

    private Boolean getBoolean(JSONObject config, String... keys) {
        return OpenAiCompatibleImageProtocolUtil.getBoolean(config, keys);
    }

    private void appendOptionalString(ObjectNode root, String field, String value) {
        if (StrUtil.isNotBlank(value)) root.put(field, value.trim());
    }

    private void appendOptionalInteger(ObjectNode root, String field, Integer value) {
        if (value != null) root.put(field, value);
    }

    private void appendOptionalFormField(MultipartBody.Builder builder, String field, String value) {
        if (StrUtil.isNotBlank(value)) builder.addFormDataPart(field, value.trim());
    }
}

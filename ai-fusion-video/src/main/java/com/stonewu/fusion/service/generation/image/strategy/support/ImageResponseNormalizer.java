package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.service.storage.MediaStorageService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Response normalization for OpenAI-compatible image protocols: parses image
 * URLs / base64 payloads, async task submission and polling responses, and
 * error payloads.  Extracted verbatim from
 * {@link OpenAiCompatibleImageProtocolSupport}; behavior is unchanged.
 */
public class ImageResponseNormalizer {

    private static final int RESPONSE_PREVIEW_LENGTH = 240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MediaStorageService mediaStorageService;

    public ImageResponseNormalizer(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    public List<String> parseImageUrls(String responseBody,
                                        boolean allowCompatibleAliases,
                                        String providerLabel) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String errorMessage = extractErrorMessage(root);
            if (StrUtil.isNotBlank(errorMessage)) {
                throw new RuntimeException(providerLabel + " 图片生成失败: " + errorMessage);
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new RuntimeException(providerLabel + " 返回空结果，响应预览: " + previewResponse(responseBody));
            }
            List<String> urls = new ArrayList<>();
            for (JsonNode item : data) {
                String url = allowCompatibleAliases ? firstText(item, "url", "image_url") : textValue(item, "url");
                if (StrUtil.isNotBlank(url)) {
                    urls.add(url);
                    continue;
                }
                String b64Json = textValue(item, "b64_json");
                if (StrUtil.isNotBlank(b64Json)) {
                    urls.add(storeBase64Image(b64Json, resolveImageExtension(root, item)));
                }
            }
            if (urls.isEmpty()) {
                throw new RuntimeException(providerLabel + " 图片响应中未找到 url 或 b64_json，响应预览: "
                        + previewResponse(responseBody));
            }
            return urls;
        } catch (IOException e) {
            throw new RuntimeException("解析 " + providerLabel + " 图片响应失败: " + e.getMessage(), e);
        }
    }

    public String parseAsyncTaskId(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String errorMessage = extractErrorMessage(root);
            if (StrUtil.isNotBlank(errorMessage)) {
                throw new RuntimeException("NewAPI 异步图片任务提交失败: " + errorMessage);
            }
            JsonNode data = root.path("data");
            JsonNode taskNode = data.isArray() && !data.isEmpty() ? data.get(0) : data;
            String taskId = firstText(taskNode, "task_id", "taskId", "id");
            if (StrUtil.isBlank(taskId)) {
                taskId = firstText(root, "task_id", "taskId", "id");
            }
            if (StrUtil.isBlank(taskId)) {
                throw new RuntimeException("NewAPI 异步图片任务响应中未找到 task_id，响应预览: "
                        + previewResponse(responseBody));
            }
            return taskId;
        } catch (IOException e) {
            throw new RuntimeException("解析 NewAPI 异步图片任务响应失败: " + e.getMessage(), e);
        }
    }

    public OpenAiCompatibleImageAsyncTaskResult parseAsyncTaskResult(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String errorMessage = extractErrorMessage(root);
            if (StrUtil.isNotBlank(errorMessage)) {
                return new OpenAiCompatibleImageAsyncTaskResult(null, true, false, List.of(), errorMessage);
            }
            JsonNode data = root.path("data");
            JsonNode taskNode = data.isArray() && !data.isEmpty() ? data.get(0) : data;
            if (taskNode.isMissingNode() || taskNode.isNull()) {
                taskNode = root;
            }
            String status = StrUtil.blankToDefault(firstText(taskNode, "status", "state"),
                    firstText(root, "status", "state"));
            String normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
            if (isAsyncSuccessStatus(normalizedStatus)) {
                return new OpenAiCompatibleImageAsyncTaskResult(status, false, true,
                        extractAsyncImageUrls(root), null);
            }
            if (isAsyncFailureStatus(normalizedStatus)) {
                return new OpenAiCompatibleImageAsyncTaskResult(status, true, false, List.of(),
                        extractAsyncErrorMessage(root, taskNode));
            }
            return new OpenAiCompatibleImageAsyncTaskResult(status, false, false, List.of(), null);
        } catch (IOException e) {
            throw new RuntimeException("解析 NewAPI 异步图片任务查询响应失败: " + e.getMessage(), e);
        }
    }

    private String storeBase64Image(String base64Payload, String extension) {
        String trimmed = StrUtil.trim(base64Payload);
        String actualExtension = extension;
        if (trimmed.startsWith("data:")) {
            int commaIndex = trimmed.indexOf(',');
            String metadata = commaIndex > 0 ? trimmed.substring(0, commaIndex) : trimmed;
            if (commaIndex > 0) trimmed = trimmed.substring(commaIndex + 1);
            actualExtension = resolveExtensionFromMetadata(metadata, actualExtension);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed.getBytes(StandardCharsets.UTF_8));
            if (mediaStorageService == null) throw new BusinessException("图片存储服务未配置");
            return mediaStorageService.storeBytes(bytes, "images", actualExtension);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("OpenAI 返回的图片 base64 数据无效", e);
        }
    }

    private String resolveImageExtension(JsonNode root, JsonNode item) {
        String outputFormat = firstText(root, "output_format");
        if (StrUtil.isBlank(outputFormat)) outputFormat = firstText(item, "output_format", "mime_type");
        if (StrUtil.isBlank(outputFormat)) return "png";
        String normalized = outputFormat.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/")) normalized = normalized.substring("image/".length());
        return switch (normalized) {
            case "png", "jpeg", "jpg", "webp", "gif" -> normalized;
            default -> "png";
        };
    }

    private List<String> extractAsyncImageUrls(JsonNode root) {
        List<String> urls = new ArrayList<>();
        JsonNode data = root.path("data");
        JsonNode taskNode = data.isArray() && !data.isEmpty() ? data.get(0) : data;
        collectAsyncImageUrls(taskNode.path("result").path("images"), urls);
        collectAsyncImageUrls(taskNode.path("images"), urls);
        collectAsyncImageUrls(root.path("result").path("images"), urls);
        collectAsyncImageUrls(root.path("images"), urls);
        if (urls.isEmpty() && data.isArray()) for (JsonNode item : data) collectUrlFields(item, urls);
        return urls;
    }

    private void collectAsyncImageUrls(JsonNode node, List<String> urls) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) {
            for (JsonNode item : node) collectUrlFields(item, urls);
        } else {
            collectUrlFields(node, urls);
        }
    }

    private void collectUrlFields(JsonNode node, List<String> urls) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        collectUrlValue(node.path("url"), urls);
        collectUrlValue(node.path("urls"), urls);
        collectUrlValue(node.path("image_url"), urls);
        collectUrlValue(node.path("imageUrl"), urls);
    }

    private void collectUrlValue(JsonNode value, List<String> urls) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        if (value.isArray()) for (JsonNode item : value) collectUrlValue(item, urls);
        else if (value.isTextual() && StrUtil.isNotBlank(value.asText())) urls.add(value.asText().trim());
    }

    private boolean isAsyncSuccessStatus(String status) {
        return "completed".equals(status) || "succeeded".equals(status) || "success".equals(status) || "done".equals(status);
    }

    private boolean isAsyncFailureStatus(String status) {
        return "failed".equals(status) || "error".equals(status) || "cancelled".equals(status) || "canceled".equals(status);
    }

    private String extractAsyncErrorMessage(JsonNode root, JsonNode taskNode) {
        String message = firstText(taskNode, "error", "error_message", "errorMessage", "message");
        return StrUtil.blankToDefault(message, StrUtil.blankToDefault(extractErrorMessage(root), previewResponse(root.toString())));
    }

    private String extractErrorMessage(JsonNode root) {
        if (root == null) return null;
        JsonNode error = root.path("error");
        if (error.isMissingNode() || error.isNull()) return null;
        String message = textValue(error, "message");
        return StrUtil.isNotBlank(message) ? message : error.isTextual() ? error.asText() : previewResponse(error.toString());
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String field : fields) {
            String value = textValue(node, field);
            if (StrUtil.isNotBlank(value)) return value;
        }
        return null;
    }

    private String textValue(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return StrUtil.isBlank(text) ? null : text;
    }

    private String resolveExtensionFromMetadata(String metadata, String fallback) {
        String normalized = metadata == null ? "" : metadata.toLowerCase(Locale.ROOT);
        if (normalized.contains("image/jpeg")) return "jpeg";
        if (normalized.contains("image/jpg")) return "jpg";
        if (normalized.contains("image/webp")) return "webp";
        if (normalized.contains("image/gif")) return "gif";
        return normalized.contains("image/png") ? "png" : StrUtil.blankToDefault(fallback, "png");
    }

    private String previewResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) return "<empty>";
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() <= RESPONSE_PREVIEW_LENGTH
                ? normalized : normalized.substring(0, RESPONSE_PREVIEW_LENGTH) + "...";
    }
}

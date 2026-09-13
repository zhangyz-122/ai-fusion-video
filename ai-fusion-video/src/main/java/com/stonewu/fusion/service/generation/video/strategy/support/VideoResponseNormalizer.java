package com.stonewu.fusion.service.generation.video.strategy.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;

import java.io.IOException;
import java.util.Locale;

/**
 * Response normalization for OpenAI-compatible video protocols: JSON reading,
 * error extraction, field lookup, status normalization and duration parsing.
 * Extracted verbatim from {@link OpenAiCompatibleVideoProtocolSupport};
 * behavior is unchanged.
 */
public class VideoResponseNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public JsonNode readJson(String responseBody, String invalidMessage) {
        try {
            return OBJECT_MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new BusinessException(invalidMessage + ": " + previewResponse(responseBody));
        }
    }

    public String extractErrorMessage(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return "响应体为空";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String message = extractErrorMessage(root);
            return StrUtil.isNotBlank(message) ? message : previewResponse(responseBody);
        } catch (Exception ignored) {
            return previewResponse(responseBody);
        }
    }

    public String extractErrorMessage(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = firstText(error, "message", "detail", "code");
            if (StrUtil.isNotBlank(message)) {
                return message;
            }
            if (error.isTextual()) {
                return error.asText();
            }
        }
        return firstText(root, "message", "detail");
    }

    public String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (StrUtil.isNotBlank(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    public String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    public Integer parsePositiveSeconds(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? (int) Math.round(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String previewResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return "<empty>";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }
}

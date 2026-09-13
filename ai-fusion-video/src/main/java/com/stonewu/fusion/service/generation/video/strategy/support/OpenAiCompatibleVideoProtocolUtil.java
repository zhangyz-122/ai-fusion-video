package com.stonewu.fusion.service.generation.video.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

import java.util.Map;

/**
 * Stateless model-config field readers shared by the OpenAI-compatible video
 * protocol classes.  Extracted verbatim from
 * {@link OpenAiCompatibleVideoProtocolSupport}; behavior is unchanged.
 */
final class OpenAiCompatibleVideoProtocolUtil {

    private OpenAiCompatibleVideoProtocolUtil() {
    }

    static String getString(JSONObject config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (StrUtil.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    static Integer getInteger(JSONObject config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            if (!config.containsKey(key)) {
                continue;
            }
            Object value = config.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    static Long getLong(JSONObject config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            if (!config.containsKey(key)) {
                continue;
            }
            Object value = config.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    static Integer getPositiveInteger(JSONObject config, String... keys) {
        Integer value = getInteger(config, keys);
        return value != null && value > 0 ? value : null;
    }

    static JSONObject asJsonObject(Object value) {
        if (value instanceof JSONObject jsonObject) {
            return new JSONObject(jsonObject);
        }
        if (value instanceof Map<?, ?> map) {
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    jsonObject.set(entry.getKey().toString(), entry.getValue());
                }
            }
            return jsonObject;
        }
        return null;
    }
}

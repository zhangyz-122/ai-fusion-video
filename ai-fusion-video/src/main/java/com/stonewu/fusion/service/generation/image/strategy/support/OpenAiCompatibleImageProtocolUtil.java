package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless helpers shared by the OpenAI-compatible image protocol classes:
 * model-config field readers and URL predicates.  Extracted verbatim from
 * {@link OpenAiCompatibleImageProtocolSupport}; behavior is unchanged.
 */
final class OpenAiCompatibleImageProtocolUtil {

    private OpenAiCompatibleImageProtocolUtil() {
    }

    static Boolean getBoolean(JSONObject config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            if (!config.containsKey(key)) continue;
            Object value = config.get(key);
            if (value instanceof Boolean bool) return bool;
            if (value != null) {
                String text = value.toString().trim();
                if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) return true;
                if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) return false;
            }
        }
        return null;
    }

    static String getString(JSONObject config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && StrUtil.isNotBlank(value.toString())) return value.toString().trim();
        }
        return null;
    }

    static Integer getInteger(JSONObject config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            if (!config.containsKey(key)) continue;
            Object value = config.get(key);
            if (value instanceof Number number) return number.intValue();
            if (value != null) {
                try { return Integer.parseInt(value.toString().trim()); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    static Long getLong(JSONObject config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            if (!config.containsKey(key)) continue;
            Object value = config.get(key);
            if (value instanceof Number number) return number.longValue();
            if (value != null) {
                try { return Long.parseLong(value.toString().trim()); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    static Double getDouble(JSONObject config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            if (!config.containsKey(key)) continue;
            Object value = config.get(key);
            if (value instanceof Number number) return number.doubleValue();
            if (value != null) {
                try { return Double.parseDouble(value.toString().trim()); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    static List<String> getStringList(JSONObject config, String... keys) {
        if (config == null) return List.of();
        for (String key : keys) {
            if (!config.containsKey(key)) continue;
            Object value = config.get(key);
            if (value instanceof Iterable<?> iterable) {
                List<String> result = new ArrayList<>();
                for (Object item : iterable) if (item != null && StrUtil.isNotBlank(item.toString())) result.add(item.toString().trim());
                if (!result.isEmpty()) return result;
            } else if (value != null && StrUtil.isNotBlank(value.toString())) {
                return List.of(value.toString().trim());
            }
        }
        return List.of();
    }

    static JSONObject asJsonObject(Object value) {
        if (value instanceof JSONObject object) return object;
        if (value instanceof Map<?, ?> map) return JSONUtil.parseObj(map);
        return null;
    }

    static Integer getIntegerOrDefault(JSONObject config, int defaultValue, String... keys) {
        Integer value = getInteger(config, keys);
        return value != null && value >= 0 ? value : defaultValue;
    }

    static long secondsToMillis(Integer seconds) {
        return Math.max(seconds == null ? 0 : seconds, 0) * 1000L;
    }

    static boolean isHttpUrl(String value) {
        return StrUtil.startWithIgnoreCase(value, "http://") || StrUtil.startWithIgnoreCase(value, "https://");
    }
}

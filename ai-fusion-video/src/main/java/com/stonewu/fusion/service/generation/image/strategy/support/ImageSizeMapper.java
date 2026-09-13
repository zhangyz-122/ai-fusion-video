package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.stonewu.fusion.entity.generation.ImageTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Size mapping for OpenAI-compatible image protocols: resolves the requested
 * pixel size, configured size tiers and aspect ratios into the size text a
 * given protocol expects.  Extracted verbatim from
 * {@link OpenAiCompatibleImageProtocolSupport}; behavior is unchanged.
 */
public class ImageSizeMapper {

    /** Resolve the effective pixel size for a task from task fields and model config. */
    public int[] resolveConfiguredSize(ImageTask task, JSONObject modelConfig) {
        int width = task != null && task.getWidth() != null && task.getWidth() > 0 ? task.getWidth() : 0;
        int height = task != null && task.getHeight() != null && task.getHeight() > 0 ? task.getHeight() : 0;
        int[] requestedSize = resolveTaskRequestedSize(task, modelConfig);
        if (requestedSize != null) {
            if (width <= 0) width = requestedSize[0];
            if (height <= 0) height = requestedSize[1];
        }
        if (width <= 0) width = valueOrZero(OpenAiCompatibleImageProtocolUtil.getInteger(modelConfig, "defaultWidth", "width"));
        if (height <= 0) height = valueOrZero(OpenAiCompatibleImageProtocolUtil.getInteger(modelConfig, "defaultHeight", "height"));
        if (width <= 0 || height <= 0) {
            int[] fallback = parseSizeText(resolveFallbackSize(modelConfig));
            if (width <= 0) width = fallback[0];
            if (height <= 0) height = fallback[1];
        }
        return new int[]{width > 0 ? width : 1024, height > 0 ? height : 1024};
    }

    public String mapSize(String modelCode, int width, int height, JSONObject config) {
        String requested = width + "x" + height;
        if (isConfiguredSizeSupported(config, requested)
                || (Boolean.TRUE.equals(OpenAiCompatibleImageProtocolUtil.getBoolean(config, "supportCustomSize", "supportsCustomSize",
                "allowCustomSize", "customSizeEnabled")) && isValidConfiguredCustomSize(width, height, config))) {
            return requested;
        }
        String fallback = resolveFallbackSize(config);
        if (!requested.equalsIgnoreCase(fallback)) {
            // Keep this warning in the support layer so all adapters use the
            // same fallback behavior.
            return fallback;
        }
        return fallback;
    }

    public AgnesImageSize resolveAgnesImageSize(String modelCode, int width, int height, JSONObject config) {
        boolean tierMode = hasTierResolution(config);
        List<ConfiguredImageSize> sizes = collectConfiguredImageSizes(config);
        ConfiguredImageSize selected = null;
        for (ConfiguredImageSize candidate : sizes) {
            if (candidate.width() == width && candidate.height() == height) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            double best = Double.MAX_VALUE;
            for (ConfiguredImageSize candidate : sizes) {
                double distance = imageSizeDistance(width, height, candidate.width(), candidate.height());
                if (distance < best) {
                    selected = candidate;
                    best = distance;
                }
            }
        }
        if (selected != null) {
            if (tierMode && isTier(selected.tier())) {
                return new AgnesImageSize(selected.tier(), selected.ratio());
            }
            return new AgnesImageSize(selected.width() + "x" + selected.height(), null);
        }
        String exact = mapSize(modelCode, width, height, config);
        return new AgnesImageSize(exact, tierMode ? deriveClosestAspectRatio(width, height, config) : null);
    }

    private boolean hasTierResolution(JSONObject config) {
        for (String resolution : OpenAiCompatibleImageProtocolUtil.getStringList(config, "supportedResolutions")) {
            if (isTier(resolution)) return true;
        }
        JSONObject supportedSizes = OpenAiCompatibleImageProtocolUtil.asJsonObject(config == null ? null : config.get("supportedSizes"));
        if (supportedSizes != null) {
            for (String key : supportedSizes.keySet()) {
                if (isTier(key)) return true;
            }
        }
        return false;
    }

    private boolean isTier(String value) {
        return StrUtil.isNotBlank(value) && value.trim().matches("\\d+[kK]");
    }

    private List<ConfiguredImageSize> collectConfiguredImageSizes(JSONObject config) {
        JSONObject supportedSizes = OpenAiCompatibleImageProtocolUtil.asJsonObject(config == null ? null : config.get("supportedSizes"));
        if (supportedSizes == null || supportedSizes.isEmpty()) return List.of();
        List<ConfiguredImageSize> result = new ArrayList<>();
        for (String tier : supportedSizes.keySet()) {
            JSONObject ratioSizes = OpenAiCompatibleImageProtocolUtil.asJsonObject(supportedSizes.get(tier));
            if (ratioSizes == null) continue;
            for (String ratio : ratioSizes.keySet()) {
                String text = ratioSizes.getStr(ratio);
                if (!isPixelSizeText(text)) continue;
                int[] dimensions = parseSizeText(text);
                result.add(new ConfiguredImageSize(tier, ratio, dimensions[0], dimensions[1]));
            }
        }
        return result;
    }

    private int[] resolveTaskRequestedSize(ImageTask task, JSONObject config) {
        if (task == null) return null;
        String resolution = StrUtil.blankToDefault(task.getResolution(), "").trim();
        if (isPixelSizeText(resolution)) return parseSizeText(resolution);
        String tier = StrUtil.isNotBlank(resolution) ? resolution : OpenAiCompatibleImageProtocolUtil.getString(config, "defaultSizeTier", "defaultResolutionTier");
        if (StrUtil.isBlank(tier)) return null;
        String ratio = firstNonBlank(task.getRatio(), task.getAspectRatio(), OpenAiCompatibleImageProtocolUtil.getString(config, "defaultAspectRatio"));
        ConfiguredImageSize first = null;
        for (ConfiguredImageSize candidate : collectConfiguredImageSizes(config)) {
            if (!candidate.tier().equalsIgnoreCase(tier.trim())) continue;
            if (first == null) first = candidate;
            if (StrUtil.isNotBlank(ratio) && candidate.ratio().equalsIgnoreCase(ratio.trim())) {
                return new int[]{candidate.width(), candidate.height()};
            }
        }
        return first == null ? null : new int[]{first.width(), first.height()};
    }

    private boolean isConfiguredSizeSupported(JSONObject config, String size) {
        return containsConfiguredSize(config == null ? null : config.get("supportedSizes"), size);
    }

    private boolean containsConfiguredSize(Object value, String size) {
        if (value == null) return false;
        if (value instanceof JSONObject object) {
            for (String key : object.keySet()) if (containsConfiguredSize(object.get(key), size)) return true;
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) if (containsConfiguredSize(item, size)) return true;
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (containsConfiguredSize(item, size)) return true;
            return false;
        }
        return size.equalsIgnoreCase(normalizeSizeText(value.toString()));
    }

    private boolean isValidConfiguredCustomSize(int width, int height, JSONObject config) {
        if (width <= 0 || height <= 0) return false;
        Integer multiple = OpenAiCompatibleImageProtocolUtil.getInteger(config, "sizeMultiple", "imageSizeMultiple", "customSizeMultiple");
        if (multiple != null && multiple > 1 && (width % multiple != 0 || height % multiple != 0)) return false;
        Integer maxEdge = OpenAiCompatibleImageProtocolUtil.getInteger(config, "maxEdge", "maxImageEdge", "maxDimension");
        if (maxEdge != null && maxEdge > 0 && Math.max(width, height) > maxEdge) return false;
        Integer minEdge = OpenAiCompatibleImageProtocolUtil.getInteger(config, "minEdge", "minImageEdge", "minDimension");
        if (minEdge != null && minEdge > 0 && Math.min(width, height) < minEdge) return false;
        Double maxRatio = OpenAiCompatibleImageProtocolUtil.getDouble(config, "maxAspectRatio", "maxRatio");
        if (maxRatio != null && maxRatio > 0 && (double) Math.max(width, height) / Math.min(width, height) > maxRatio) return false;
        long pixels = (long) width * height;
        Long minPixels = OpenAiCompatibleImageProtocolUtil.getLong(config, "minPixels");
        Long maxPixels = OpenAiCompatibleImageProtocolUtil.getLong(config, "maxPixels");
        return (minPixels == null || minPixels <= 0 || pixels >= minPixels)
                && (maxPixels == null || maxPixels <= 0 || pixels <= maxPixels);
    }

    private String resolveFallbackSize(JSONObject config) {
        String defaultSize = OpenAiCompatibleImageProtocolUtil.getString(config, "defaultSize");
        if (isPixelSizeText(defaultSize)) return normalizeSizeText(defaultSize);
        Integer width = OpenAiCompatibleImageProtocolUtil.getInteger(config, "defaultWidth", "width");
        Integer height = OpenAiCompatibleImageProtocolUtil.getInteger(config, "defaultHeight", "height");
        if (width != null && width > 0 && height != null && height > 0) return width + "x" + height;
        String first = findFirstConfiguredSize(config == null ? null : config.get("supportedSizes"));
        return StrUtil.isNotBlank(first) ? first : "1024x1024";
    }

    private String findFirstConfiguredSize(Object value) {
        if (value == null) return null;
        if (value instanceof JSONObject object) {
            for (String key : object.keySet()) {
                String found = findFirstConfiguredSize(object.get(key));
                if (StrUtil.isNotBlank(found)) return found;
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                String found = findFirstConfiguredSize(item);
                if (StrUtil.isNotBlank(found)) return found;
            }
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String found = findFirstConfiguredSize(item);
                if (StrUtil.isNotBlank(found)) return found;
            }
            return null;
        }
        String text = normalizeSizeText(value.toString());
        return isPixelSizeText(text) ? text : null;
    }

    private double imageSizeDistance(int requestedWidth, int requestedHeight, int candidateWidth, int candidateHeight) {
        if (requestedWidth <= 0 || requestedHeight <= 0 || candidateWidth <= 0 || candidateHeight <= 0) {
            return Double.MAX_VALUE;
        }
        return Math.abs(Math.log((double) candidateWidth / requestedWidth))
                + Math.abs(Math.log((double) candidateHeight / requestedHeight));
    }

    private String deriveClosestAspectRatio(int width, int height, JSONObject config) {
        if (width <= 0 || height <= 0) return StrUtil.blankToDefault(OpenAiCompatibleImageProtocolUtil.getString(config, "defaultAspectRatio"), "1:1");
        List<String> ratios = OpenAiCompatibleImageProtocolUtil.getStringList(config, "supportedAspectRatios");
        if (ratios.isEmpty()) ratios = List.of("1:1", "3:4", "4:3", "16:9", "9:16", "2:3", "3:2", "21:9");
        double requestedRatio = (double) width / height;
        String closest = null;
        double score = Double.MAX_VALUE;
        for (String ratio : ratios) {
            String[] parts = StrUtil.blankToDefault(ratio, "").split(":", 2);
            if (parts.length != 2) continue;
            try {
                double candidate = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
                double current = Math.abs(Math.log(candidate / requestedRatio));
                if (current < score) {
                    score = current;
                    closest = ratio.trim();
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Ignore malformed custom ratio entries.
            }
        }
        return StrUtil.blankToDefault(closest, "1:1");
    }

    private int[] parseSizeText(String sizeText) {
        String normalized = normalizeSizeText(sizeText);
        if (!isPixelSizeText(normalized)) return new int[]{1024, 1024};
        String[] parts = normalized.split("x", 2);
        try { return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}; }
        catch (NumberFormatException ignored) { return new int[]{1024, 1024}; }
    }

    private String normalizeSizeText(String sizeText) {
        return StrUtil.blankToDefault(sizeText, "").trim().toLowerCase(Locale.ROOT).replace('*', 'x');
    }

    private boolean isPixelSizeText(String sizeText) {
        return StrUtil.isNotBlank(sizeText) && normalizeSizeText(sizeText).matches("\\d+x\\d+");
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (StrUtil.isNotBlank(value)) return value.trim();
        return null;
    }

    /** Agnes protocol size: either a pixel size ("1024x768") or a tier ("2K") plus optional ratio. */
    public record AgnesImageSize(String size, String ratio) {
    }

    private record ConfiguredImageSize(String tier, String ratio, int width, int height) {
    }
}

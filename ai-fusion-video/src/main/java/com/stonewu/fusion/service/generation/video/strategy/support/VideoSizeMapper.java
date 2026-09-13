package com.stonewu.fusion.service.generation.video.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.stonewu.fusion.entity.generation.VideoTask;

import java.util.Locale;

/**
 * Size and duration mapping for OpenAI-compatible video protocols: resolves
 * dimensions, frame rate and frame count from task fields and model config.
 * Extracted verbatim from {@link OpenAiCompatibleVideoProtocolSupport};
 * behavior is unchanged.
 */
public class VideoSizeMapper {

    /** Resolve the effective duration in seconds for a task. */
    public Integer resolveDurationSeconds(VideoTask task, JSONObject modelConfig) {
        if (task.getDuration() != null && task.getDuration() > 0) {
            return task.getDuration();
        }
        Integer defaultDuration = OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(
                modelConfig, "defaultDuration", "seconds", "duration");
        return defaultDuration != null ? defaultDuration : null;
    }

    public Integer resolveAgnesFrameRate(JSONObject modelConfig) {
        Integer frameRate = OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(modelConfig, "frameRate", "frame_rate", "fps");
        return frameRate != null ? Math.min(frameRate, 60) : 24;
    }

    public Integer resolveAgnesNumFrames(VideoTask task, JSONObject modelConfig, Integer frameRate) {
        Integer configured = OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(modelConfig, "numFrames", "num_frames");
        if (configured != null) {
            return normalizeAgnesNumFrames(configured);
        }

        Integer durationSeconds = resolveDurationSeconds(task, modelConfig);
        int fps = frameRate != null && frameRate > 0 ? frameRate : 24;
        int rawFrames = durationSeconds != null && durationSeconds > 0 ? durationSeconds * fps : 121;
        return normalizeAgnesNumFrames(rawFrames);
    }

    public int[] resolveAgnesDimensions(VideoTask task, JSONObject modelConfig) {
        int[] parsed = parseDimensions(task.getResolution());
        if (parsed != null) {
            return parsed;
        }

        Integer width = OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(
                modelConfig, "width", "defaultWidth", "videoWidth", "video_width");
        Integer height = OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(
                modelConfig, "height", "defaultHeight", "videoHeight", "video_height");
        if (width != null && height != null) {
            return new int[]{width, height};
        }

        parsed = parseDimensions(OpenAiCompatibleVideoProtocolUtil.getString(modelConfig, "resolution", "defaultResolution"));
        if (parsed != null) {
            return parsed;
        }

        return defaultDimensionsByRatio(task.getRatio());
    }

    /** Normalize a "1280x720"-style size text; returns null when blank or not pixel-shaped. */
    public String normalizeSize(String size) {
        if (StrUtil.isBlank(size)) {
            return null;
        }
        String normalized = size.trim().toLowerCase(Locale.ROOT).replace('*', 'x').replace('×', 'x');
        return normalized.matches("\\d+x\\d+") ? normalized : null;
    }

    private int normalizeAgnesNumFrames(int candidate) {
        int normalized = Math.max(candidate, 1);
        if (normalized > 441) {
            normalized = 441;
        }
        int remainder = Math.floorMod(normalized - 1, 8);
        if (remainder != 0) {
            normalized += 8 - remainder;
        }
        return Math.min(normalized, 441);
    }

    private int[] defaultDimensionsByRatio(String ratio) {
        String normalized = StrUtil.blankToDefault(ratio, "16:9")
                .trim()
                .replace('：', ':')
                .replace(" ", "");
        return switch (normalized) {
            case "9:16", "2:3" -> new int[]{768, 1152};
            case "1:1" -> new int[]{1024, 1024};
            case "4:3" -> new int[]{1024, 768};
            case "3:4" -> new int[]{768, 1024};
            case "21:9" -> new int[]{1536, 640};
            default -> new int[]{1152, 768};
        };
    }

    private int[] parseDimensions(String resolution) {
        if (StrUtil.isBlank(resolution)) {
            return null;
        }

        String normalized = resolution.trim()
                .toLowerCase(Locale.ROOT)
                .replace('*', 'x')
                .replace('×', 'x')
                .replace(" ", "");
        if (!normalized.matches("\\d+x\\d+")) {
            return null;
        }

        String[] parts = normalized.split("x", 2);
        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            return width > 0 && height > 0 ? new int[]{width, height} : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

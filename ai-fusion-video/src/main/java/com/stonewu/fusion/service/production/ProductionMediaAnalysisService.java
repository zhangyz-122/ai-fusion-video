package com.stonewu.fusion.service.production;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * PR-020 补充：对候选视频做少量帧采样，给出"是否完全静止""是否有可见内容"两项客观媒体信号。
 *
 * <p>测量依赖 ffmpeg 解码，判定是纯函数；两者分开，媒体工具缺失时只保留未知，
 * 不把"没测到"当成通过，也不当成失败。</p>
 */
@Service
@Slf4j
public class ProductionMediaAnalysisService {

    public static final String MOTION_STATIC = "VIDEO_MOTION_STATIC";
    public static final String NO_VISIBLE_CONTENT = "VIDEO_NO_VISIBLE_CONTENT";

    private static final int SAMPLE_FRAMES = 5;
    private static final int SAMPLE_WIDTH = 160;
    private static final double MIN_VISIBLE_BRIGHTNESS = 2.0;
    private static final double MAX_VISIBLE_BRIGHTNESS = 253.0;

    @Value("${video.compose.ffmpeg-path:ffmpeg}")
    private String ffmpegPath = "ffmpeg";

    @Value("${video.technical-qc.timeout-seconds:45}")
    private long timeoutSeconds = 45;

    /**
     * @param sampledFrames     成功解码的帧数，不足 2 帧时无法判定运动
     * @param meanMotion        相邻采样帧的逐像素平均灰度差，0 表示画面完全静止
     * @param meanBrightness    采样帧的平均灰度
     * @param unavailableReason 无法测量时的原因，可为 null
     */
    public record MediaSignals(Integer sampledFrames, Double meanMotion, Double meanBrightness,
                               String unavailableReason) {

        public static MediaSignals unavailable(String reason) {
            return new MediaSignals(0, null, null, reason);
        }
    }

    public record Finding(String code, String note) {
    }

    public MediaSignals measure(String input, double durationSeconds) {
        if (!StringUtils.hasText(input)) {
            return MediaSignals.unavailable("媒体地址为空");
        }
        Path directory = null;
        try {
            directory = Files.createTempDirectory("fusion-media-qc-");
            return analyse(extractFrames(input, durationSeconds, directory));
        } catch (Exception exception) {
            log.warn("[ProductionMediaAnalysis] 帧采样失败: input={}, error={}", input, exception.getMessage());
            return MediaSignals.unavailable(trimReason(exception.getMessage()));
        } finally {
            deleteQuietly(directory);
        }
    }

    /** 只在信号明确异常时给出判定；测量缺失返回 null，保持原有结论。 */
    public Finding judge(MediaSignals signals) {
        if (signals == null || signals.sampledFrames() == null || signals.sampledFrames() < 2) {
            return null;
        }
        if (signals.meanMotion() != null && signals.meanMotion() == 0.0) {
            return new Finding(MOTION_STATIC, "采样帧之间没有任何像素变化，判定为静止画面");
        }
        Double brightness = signals.meanBrightness();
        if (brightness != null
                && (brightness <= MIN_VISIBLE_BRIGHTNESS || brightness >= MAX_VISIBLE_BRIGHTNESS)) {
            return new Finding(NO_VISIBLE_CONTENT,
                    String.format(Locale.ROOT, "采样帧平均灰度 %.1f，画面接近全黑或全白", brightness));
        }
        return null;
    }

    public Map<String, Object> metrics(MediaSignals signals) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (signals == null) {
            metrics.put("mediaCheck", "UNAVAILABLE");
            return metrics;
        }
        metrics.put("mediaSampledFrames", signals.sampledFrames());
        if (signals.meanMotion() != null) {
            metrics.put("mediaMotionScore", signals.meanMotion());
        }
        if (signals.meanBrightness() != null) {
            metrics.put("mediaBrightness", signals.meanBrightness());
        }
        metrics.put("mediaCheck", signals.unavailableReason() == null ? "MEASURED" : "UNAVAILABLE");
        if (signals.unavailableReason() != null) {
            metrics.put("mediaUnavailableReason", signals.unavailableReason());
        }
        return metrics;
    }

    private List<Path> extractFrames(String input, double durationSeconds, Path directory)
            throws IOException, InterruptedException {
        double fps = durationSeconds > 0 ? SAMPLE_FRAMES / durationSeconds : 1.0;
        String filter = String.format(Locale.ROOT, "fps=%.6f,scale=%d:-2,format=gray", fps, SAMPLE_WIDTH);
        List<String> command = List.of(
                StringUtils.hasText(ffmpegPath) ? ffmpegPath.trim() : "ffmpeg",
                "-hide_banner", "-loglevel", "error", "-y",
                "-i", input,
                "-vf", filter,
                "-frames:v", String.valueOf(SAMPLE_FRAMES),
                directory.resolve("frame_%03d.png").toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg 抽帧超时");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg 抽帧失败，退出码 " + process.exitValue());
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private MediaSignals analyse(List<Path> frames) throws IOException {
        List<double[]> pixels = new ArrayList<>();
        for (Path frame : frames) {
            BufferedImage image = ImageIO.read(frame.toFile());
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                pixels.add(lumaPlane(image));
            }
        }
        if (pixels.isEmpty()) {
            return MediaSignals.unavailable("没有可解码的帧");
        }
        double brightness = pixels.stream().mapToDouble(this::mean).average().orElse(0);
        if (pixels.size() < 2) {
            return new MediaSignals(pixels.size(), null, round(brightness), "可解码帧不足，无法判定运动");
        }
        double motion = 0;
        for (int index = 1; index < pixels.size(); index++) {
            motion += meanAbsoluteDifference(pixels.get(index - 1), pixels.get(index));
        }
        return new MediaSignals(pixels.size(),
                round(motion / (pixels.size() - 1)), round(brightness), null);
    }

    private double[] lumaPlane(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean singleBand = image.getSampleModel().getNumBands() == 1;
        double[] plane = new double[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                plane[y * width + x] = singleBand
                        ? image.getRaster().getSample(x, y, 0)
                        : lumaOfRgb(image.getRGB(x, y));
            }
        }
        return plane;
    }

    private double lumaOfRgb(int rgb) {
        return 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
    }

    /** 尺寸不同的相邻帧不可比，按较短长度对齐，避免越界。 */
    private double meanAbsoluteDifference(double[] previous, double[] current) {
        int length = Math.min(previous.length, current.length);
        if (length == 0) {
            return 0;
        }
        double total = 0;
        for (int index = 0; index < length; index++) {
            total += Math.abs(previous[index] - current[index]);
        }
        return total / length;
    }

    private double mean(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String trimReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "未知原因";
        }
        String normalized = reason.trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            log.debug("[ProductionMediaAnalysis] 临时帧目录清理失败: {}", directory);
        }
    }
}

package com.stonewu.fusion.service.production;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.service.storage.StorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对已持久化的视频候选执行便宜、可解释的自动技术质检。
 *
 * <p>这里只负责媒体可读性和基础流属性，不判断审美质量，也不接管视频生成队列。
 * 技术通过不会绕过人工复核，技术失败会阻止候选被选择。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionTechnicalQcService {

    private static final String LOCAL_MEDIA_PREFIX = "/media/";
    private static final String DEFAULT_LOCAL_MEDIA_PATH = "./data/media";
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L;

    private final QcResultMapper qcResultMapper;
    private final StorageConfigService storageConfigService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${video.compose.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${video.technical-qc.timeout-seconds:45}")
    private long timeoutSeconds;

    public QcResult evaluate(ProductionTake take, VideoItem item) {
        QcResult result = findOrCreate(take);
        Evaluation evaluation = inspect(item == null ? null : item.getVideoUrl());
        result.setTechnicalStatus(evaluation.status());
        result.setTechnicalFailureCode(evaluation.failureCode());
        result.setTechnicalMetricsJson(JSONUtil.toJsonStr(evaluation.metrics()));
        result.setTechnicalEvaluatedAt(LocalDateTime.now());

        if (QcResult.FAIL.equals(evaluation.status())) {
            result.setStatus(QcResult.FAIL);
            result.setEvaluatorType("AUTO");
            result.setNote(evaluation.note());
        } else {
            // Technical PASS only clears the automatic blocker. Manual review remains required.
            if (QcResult.FAIL.equals(result.getStatus()) && !"MANUAL".equals(result.getEvaluatorType())) {
                result.setStatus(QcResult.REVIEW_REQUIRED);
            }
            if (!StringUtils.hasText(result.getStatus())) {
                result.setStatus(QcResult.REVIEW_REQUIRED);
            }
            if ("SYSTEM".equals(result.getEvaluatorType()) && !StringUtils.hasText(result.getNote())) {
                result.setNote("自动技术质检通过，等待人工复核");
            }
        }
        qcResultMapper.updateById(result);
        return result;
    }

    private QcResult findOrCreate(ProductionTake take) {
        QcResult result = qcResultMapper.selectOne(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getTakeId, take.getId()));
        if (result != null) {
            return result;
        }
        result = QcResult.builder()
                .runId(take.getRunId())
                .takeId(take.getId())
                .storyboardItemId(take.getStoryboardItemId())
                .status(StringUtils.hasText(take.getQcStatus()) ? take.getQcStatus() : QcResult.REVIEW_REQUIRED)
                .evaluatorType("SYSTEM")
                .note(take.getQcNote())
                .build();
        qcResultMapper.insert(result);
        return result;
    }

    private Evaluation inspect(String videoUrl) {
        if (!StringUtils.hasText(videoUrl)) {
            return Evaluation.fail("VIDEO_URL_MISSING", "候选视频没有可质检的 URL");
        }

        Path tempFile = null;
        try {
            String input = videoUrl.trim();
            if (input.startsWith("http://") || input.startsWith("https://")) {
                tempFile = download(input);
                input = tempFile.toString();
            } else if (input.startsWith(LOCAL_MEDIA_PREFIX)) {
                input = resolveLocalMedia(input).toString();
            }

            ProbeResult probe = probe(input);
            if (!probe.success()) {
                return Evaluation.fail("VIDEO_PROBE_FAILED", probe.error());
            }
            return validateProbe(probe.json());
        } catch (Exception exception) {
            log.warn("[ProductionTechnicalQc] 视频技术质检失败: url={}, error={}", videoUrl, exception.getMessage());
            return Evaluation.fail("VIDEO_QC_EXCEPTION", trim(exception.getMessage()));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.debug("[ProductionTechnicalQc] 临时视频清理失败: {}", tempFile);
                }
            }
        }
    }

    Evaluation validateProbe(JSONObject root) {
        JSONArray streams = root.getJSONArray("streams");
        JSONObject format = root.getJSONObject("format");
        JSONObject video = null;
        JSONObject audio = null;
        if (streams != null) {
            for (Object value : streams) {
                JSONObject stream = JSONUtil.parseObj(value);
                if ("video".equalsIgnoreCase(stream.getStr("codec_type")) && video == null) {
                    video = stream;
                } else if ("audio".equalsIgnoreCase(stream.getStr("codec_type")) && audio == null) {
                    audio = stream;
                }
            }
        }
        if (video == null) {
            return Evaluation.fail("VIDEO_STREAM_MISSING", "媒体中没有视频流");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("container", format == null ? null : format.getStr("format_name"));
        metrics.put("durationSeconds", number(format, "duration"));
        metrics.put("width", video.getInt("width"));
        metrics.put("height", video.getInt("height"));
        metrics.put("videoCodec", video.getStr("codec_name"));
        metrics.put("pixelFormat", video.getStr("pix_fmt"));
        metrics.put("frameRate", parseFrameRate(video));
        metrics.put("audioPresent", audio != null);
        if (audio != null) {
            metrics.put("audioCodec", audio.getStr("codec_name"));
            metrics.put("audioSampleRate", audio.getStr("sample_rate"));
        }

        double duration = number(format, "duration");
        int width = video.getInt("width", 0);
        int height = video.getInt("height", 0);
        double frameRate = parseFrameRate(video);
        List<String> failures = new ArrayList<>();
        if (duration <= 0) failures.add("DURATION_INVALID");
        if (width <= 0 || height <= 0) failures.add("DIMENSION_INVALID");
        if (frameRate <= 0) failures.add("FRAME_RATE_INVALID");
        if (!StringUtils.hasText(video.getStr("codec_name"))) failures.add("VIDEO_CODEC_MISSING");

        if (!failures.isEmpty()) {
            String code = failures.get(0);
            return new Evaluation(QcResult.FAIL, code, String.join(",", failures), metrics);
        }
        return new Evaluation(QcResult.PASS, null, "自动技术质检通过，等待人工复核", metrics);
    }

    private ProbeResult probe(String input) throws IOException, InterruptedException {
        List<String> command = List.of(
                StringUtils.hasText(ffprobePath) ? ffprobePath.trim() : "ffprobe",
                "-v", "error",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                input
        );
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new ProbeResult(false, null, "ffprobe 超时");
        }
        byte[] output;
        try (InputStream inputStream = process.getInputStream()) {
            output = inputStream.readAllBytes();
        }
        String text = new String(output, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            return new ProbeResult(false, null, trim(text));
        }
        return new ProbeResult(true, JSONUtil.parseObj(text), null);
    }

    private Path download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<Path> response;
        Path target = Files.createTempFile("fusion-qc-", ".mp4");
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("视频下载失败，HTTP " + response.statusCode());
            }
            if (Files.size(target) > MAX_DOWNLOAD_BYTES) {
                throw new IOException("视频超过技术质检大小上限");
            }
            return target;
        } catch (Exception exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private Path resolveLocalMedia(String url) throws IOException {
        StorageConfig config = storageConfigService.getDefaultConfig();
        String configuredBasePath = config == null ? null : config.getBasePath();
        Path basePath = Paths.get(StringUtils.hasText(configuredBasePath)
                        ? configuredBasePath : DEFAULT_LOCAL_MEDIA_PATH)
                .toAbsolutePath().normalize();
        Path target = basePath.resolve(url.substring(LOCAL_MEDIA_PREFIX.length())).normalize();
        if (!target.startsWith(basePath) || !Files.isRegularFile(target)) {
            throw new IOException("本地视频文件不存在: " + target);
        }
        return target;
    }

    private double number(JSONObject object, String key) {
        if (object == null || object.getStr(key) == null) return 0;
        try {
            return Double.parseDouble(object.getStr(key));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double parseFrameRate(JSONObject video) {
        String value = video.getStr("avg_frame_rate");
        if (!StringUtils.hasText(value) || "0/0".equals(value)) return 0;
        try {
            if (value.contains("/")) {
                String[] parts = value.split("/", 2);
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return "未知技术质检错误";
        String normalized = value.trim();
        return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
    }

    private record ProbeResult(boolean success, JSONObject json, String error) {
    }

    record Evaluation(String status, String failureCode, String note, Map<String, Object> metrics) {
        private static Evaluation fail(String code, String note) {
            return new Evaluation(QcResult.FAIL, code, note, Map.of("technicalCheck", "FAILED"));
        }
    }
}

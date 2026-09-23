package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.service.production.ProductionMediaAnalysisService.Finding;
import com.stonewu.fusion.service.production.ProductionMediaAnalysisService.MediaSignals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionMediaAnalysisServiceTests {

    private final ProductionMediaAnalysisService service = new ProductionMediaAnalysisService();

    @Test
    void fullyStaticFramesAreReportedAsStatic() {
        Finding finding = service.judge(new MediaSignals(5, 0.0, 128.4, null));

        assertThat(finding).isNotNull();
        assertThat(finding.code()).isEqualTo(ProductionMediaAnalysisService.MOTION_STATIC);
    }

    @Test
    void nearBlackAndNearWhiteFramesAreReportedAsNoContent() {
        assertThat(service.judge(new MediaSignals(5, 12.5, 1.2, null)))
                .extracting(Finding::code)
                .isEqualTo(ProductionMediaAnalysisService.NO_VISIBLE_CONTENT);
        assertThat(service.judge(new MediaSignals(5, 12.5, 254.0, null)))
                .extracting(Finding::code)
                .isEqualTo(ProductionMediaAnalysisService.NO_VISIBLE_CONTENT);
    }

    @Test
    void measuredSignalsWithMotionAndNormalExposurePass() {
        assertThat(service.judge(new MediaSignals(5, 0.46, 128.1, null))).isNull();
    }

    @Test
    void missingMeasurementNeverChangesTheVerdict() {
        assertThat(service.judge(MediaSignals.unavailable("ffmpeg 抽帧超时"))).isNull();
        assertThat(service.judge(new MediaSignals(1, null, 90.0, "可解码帧不足，无法判定运动"))).isNull();
        assertThat(service.judge(null)).isNull();
    }

    @Test
    void unavailableMeasurementIsRecordedInsteadOfFabricated() {
        Map<String, Object> metrics = service.metrics(MediaSignals.unavailable("没有可解码的帧"));

        assertThat(metrics)
                .containsEntry("mediaCheck", "UNAVAILABLE")
                .containsEntry("mediaUnavailableReason", "没有可解码的帧")
                .doesNotContainKey("mediaMotionScore");
        assertThat(service.metrics(new MediaSignals(5, 0.46, 128.1, null)))
                .containsEntry("mediaCheck", "MEASURED")
                .containsEntry("mediaMotionScore", 0.46);
    }

    @Test
    void measureReadsRealFramesFromGeneratedVideo(@TempDir Path directory) throws Exception {
        Path clip = buildClip(directory, "moving.mp4",
                "testsrc=duration=5:size=320x240:rate=25");
        Assumptions.assumeTrue(clip != null, "ffmpeg 不可用，跳过真实帧采样验证");

        MediaSignals signals = service.measure(clip.toString(), 5.0);

        assertThat(signals.unavailableReason()).isNull();
        assertThat(signals.sampledFrames()).isGreaterThanOrEqualTo(2);
        assertThat(signals.meanMotion()).isGreaterThan(0.0);
        assertThat(signals.meanBrightness()).isBetween(20.0, 235.0);
        assertThat(service.judge(signals)).isNull();
    }

    @Test
    void measureDetectsAStaticClip(@TempDir Path directory) throws Exception {
        Path clip = buildClip(directory, "static.mp4", "color=gray:duration=2:size=320x240:rate=25");
        Assumptions.assumeTrue(clip != null, "ffmpeg 不可用，跳过真实帧采样验证");

        assertThat(service.judge(service.measure(clip.toString(), 2.0)))
                .extracting(Finding::code)
                .isEqualTo(ProductionMediaAnalysisService.MOTION_STATIC);
    }

    @Test
    void staticVideoDowngradesAStreamingPass(@TempDir Path directory) throws Exception {
        Path clip = buildClip(directory, "static.mp4", "color=gray:duration=2:size=320x240:rate=25");
        Assumptions.assumeTrue(clip != null, "ffmpeg 不可用，跳过真实帧采样验证");
        ProductionTechnicalQcService technicalQc =
                new ProductionTechnicalQcService(null, null, service);

        ProductionTechnicalQcService.Evaluation evaluation = technicalQc.withMediaSignals(
                new ProductionTechnicalQcService.Evaluation(
                        QcResult.PASS, null, "自动技术质检通过，等待人工复核", Map.of("durationSeconds", 2.0)),
                clip.toString());

        assertThat(evaluation.status()).isEqualTo(QcResult.FAIL);
        assertThat(evaluation.failureCode()).isEqualTo(ProductionMediaAnalysisService.MOTION_STATIC);
        assertThat(evaluation.metrics())
                .containsEntry("mediaCheck", "MEASURED")
                .containsEntry("durationSeconds", 2.0);
    }

    @Test
    void unmeasurableMediaKeepsTheStreamingPass(@TempDir Path directory) {
        ProductionTechnicalQcService technicalQc =
                new ProductionTechnicalQcService(null, null, service);

        ProductionTechnicalQcService.Evaluation evaluation = technicalQc.withMediaSignals(
                new ProductionTechnicalQcService.Evaluation(
                        QcResult.PASS, null, "自动技术质检通过，等待人工复核", Map.of("durationSeconds", 5.0)),
                directory.resolve("missing.mp4").toString());

        assertThat(evaluation.status()).isEqualTo(QcResult.PASS);
        assertThat(evaluation.failureCode()).isNull();
        assertThat(evaluation.metrics()).containsEntry("mediaCheck", "UNAVAILABLE");
    }

    /** 返回 null 表示本机没有可用的 ffmpeg，调用方据此跳过而非放行。 */
    private Path buildClip(Path directory, String name, String source) {
        Path target = directory.resolve(name);
        List<String> command = List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", source, "-pix_fmt", "yuv420p", target.toString());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            return Files.isRegularFile(target) ? target : null;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}

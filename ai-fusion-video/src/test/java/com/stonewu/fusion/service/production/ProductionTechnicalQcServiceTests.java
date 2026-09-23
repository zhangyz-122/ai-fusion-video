package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.service.storage.StorageConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import cn.hutool.json.JSONUtil;

@ExtendWith(MockitoExtension.class)
class ProductionTechnicalQcServiceTests {

    @Mock
    private QcResultMapper qcResultMapper;

    @Mock
    private StorageConfigService storageConfigService;

    @Test
    void missingVideoIsRecordedAsTechnicalFailure() {
        when(qcResultMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            QcResult result = invocation.getArgument(0);
            result.setId(91L);
            return 1;
        }).when(qcResultMapper).insert(any(QcResult.class));

        ProductionTechnicalQcService service = new ProductionTechnicalQcService(
                qcResultMapper, storageConfigService, new ProductionMediaAnalysisService());
        QcResult result = service.evaluate(
                ProductionTake.builder()
                        .id(11L).runId(21L).storyboardItemId(31L).qcStatus(QcResult.REVIEW_REQUIRED).build(),
                VideoItem.builder().id(41L).videoUrl(null).build());

        assertThat(result.getStatus()).isEqualTo(QcResult.FAIL);
        assertThat(result.getTechnicalStatus()).isEqualTo(QcResult.FAIL);
        assertThat(result.getTechnicalFailureCode()).isEqualTo("VIDEO_URL_MISSING");
        assertThat(result.getTechnicalMetricsJson()).contains("technicalCheck");
    }

    @Test
    void realSeedVr2OutputProbePassesWithAudio() throws IOException {
        ProductionTechnicalQcService service = service();

        ProductionTechnicalQcService.Evaluation evaluation = service.validateProbe(loadProbe("valid-h264-aac-probe.json"));

        assertThat(evaluation.status()).isEqualTo(QcResult.PASS);
        assertThat(evaluation.failureCode()).isNull();
        assertThat(evaluation.metrics())
                .containsEntry("width", 1910)
                .containsEntry("height", 1080)
                .containsEntry("audioPresent", true);
    }

    @Test
    void noAudioIsAllowedButRecordedInTechnicalMetrics() throws IOException {
        ProductionTechnicalQcService.Evaluation evaluation = service()
                .validateProbe(loadProbe("no-audio-probe.json"));

        assertThat(evaluation.status()).isEqualTo(QcResult.PASS);
        assertThat(evaluation.metrics()).containsEntry("audioPresent", false);
    }

    @Test
    void invalidDimensionFailsWithStableFailureCode() throws IOException {
        ProductionTechnicalQcService.Evaluation evaluation = service()
                .validateProbe(loadProbe("invalid-dimension-probe.json"));

        assertThat(evaluation.status()).isEqualTo(QcResult.FAIL);
        assertThat(evaluation.failureCode()).isEqualTo("DIMENSION_INVALID");
    }

    private ProductionTechnicalQcService service() {
        return new ProductionTechnicalQcService(
                qcResultMapper, storageConfigService, new ProductionMediaAnalysisService());
    }

    private cn.hutool.json.JSONObject loadProbe(String fileName) throws IOException {
        try (var input = getClass().getResourceAsStream("/production/technical-qc/" + fileName)) {
            if (input == null) throw new IOException("fixture not found: " + fileName);
            return JSONUtil.parseObj(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

package com.stonewu.fusion.controller.ai;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.ai.vo.AiModelRespVO;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.ModelPresetService;
import com.stonewu.fusion.service.ai.model.OllamaCapabilitiesClient;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模型列表接口的工具调用能力标注测试（controller 级）：
 * Ollama 全部离线时，列表接口必须在单模型超时上限附近快速返回（并行探测 + 整体
 * deadline），并通过负缓存保证第二次下拉不再重复发起探测。
 */
class AiModelControllerTests {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final AiModelController controller =
            new AiModelController(aiModelService, mock(ModelPresetService.class));

    @Test
    void listReturnsFastWhenEveryOllamaProbeHangsBeyondPerModelTimeout() {
        stubModels("qwen3:8b", "llama3:8b", "gemma3:4b");
        // 每个探测都挂住远超单模型超时（模拟 Ollama 半死不活：TCP 已连、响应不到）
        when(aiModelService.supportsToolCalls(any(AiModel.class))).thenAnswer(invocation -> {
            Thread.sleep(6000);
            return null;
        });

        long startMillis = System.currentTimeMillis();
        List<AiModelRespVO> result = controller.list().getData();
        long elapsedMillis = System.currentTimeMillis() - startMillis;

        // 3 个模型串行探测需要 90s；并行 + 单模型独立超时下应在 3s 超时附近返回
        assertThat(elapsedMillis)
                .as("列表接口应在整体 deadline 附近快速返回，实际耗时 %dms", elapsedMillis)
                .isLessThan(5500);
        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(vo -> assertThat(vo.getSupportsToolCalls()).isNull());
    }

    @Test
    void listProbesModelsInParallelInsteadOfSerialSum() {
        stubModels("m1", "m2", "m3", "m4", "m5", "m6");
        when(aiModelService.supportsToolCalls(any(AiModel.class))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return Boolean.TRUE;
        });

        long startMillis = System.currentTimeMillis();
        List<AiModelRespVO> result = controller.list().getData();
        long elapsedMillis = System.currentTimeMillis() - startMillis;

        // 6 × 300ms = 1800ms 的串行求和被并行化压缩到约单模型耗时
        assertThat(elapsedMillis)
                .as("并行探测总耗时应≈最慢单模型，实际耗时 %dms", elapsedMillis)
                .isLessThan(1200);
        assertThat(result).hasSize(6);
        assertThat(result).allSatisfy(vo -> assertThat(vo.getSupportsToolCalls()).isTrue());
    }

    @Test
    void listSkipsSecondRoundProbesViaNegativeCacheWhenOllamaOffline() {
        stubModels("qwen3:8b", "llama3:8b", "gemma3:4b");
        // 真实客户端 + 探测计数桩：探测全部返回“未知”（Ollama 离线）
        ProbeCountingClient countingClient = new ProbeCountingClient(new ConcurrentMapCacheManager());
        when(aiModelService.supportsToolCalls(any(AiModel.class)))
                .thenAnswer(invocation -> countingClient.supportsToolCalls(
                        "http://127.0.0.1:1", ((AiModel) invocation.getArgument(0)).getCode()));

        CommonResult<List<AiModelRespVO>> first = controller.list();
        assertThat(first.getData()).hasSize(3);
        assertThat(first.getData()).allSatisfy(vo -> assertThat(vo.getSupportsToolCalls()).isNull());
        assertThat(countingClient.probes.get()).as("首次下拉应逐模型发起探测").isEqualTo(3);

        // Ollama 仍离线：第二次下拉命中负缓存，不再重复发起探测
        CommonResult<List<AiModelRespVO>> second = controller.list();
        assertThat(second.getData()).hasSize(3);
        assertThat(second.getData()).allSatisfy(vo -> assertThat(vo.getSupportsToolCalls()).isNull());
        assertThat(countingClient.probes.get()).as("负缓存命中后不应再次探测").isEqualTo(3);
    }

    private void stubModels(String... codes) {
        AiModel[] models = new AiModel[codes.length];
        for (int index = 0; index < codes.length; index++) {
            models[index] = AiModel.builder().id((long) (index + 1)).code(codes[index]).build();
        }
        when(aiModelService.getEnabledList()).thenReturn(List.of(models));
    }

    /** 探测计数桩：把真实 HTTP 探测替换为“Ollama 离线”，统计探测发起次数 */
    static class ProbeCountingClient extends OllamaCapabilitiesClient {

        final AtomicInteger probes = new AtomicInteger();

        ProbeCountingClient(CacheManager cacheManager) {
            super(cacheManager);
        }

        @Override
        public Boolean probeToolCalls(String baseUrl, String modelName) {
            probes.incrementAndGet();
            return null;
        }
    }
}

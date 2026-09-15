package com.stonewu.fusion.service.ai.model;

import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.service.ai.ApiConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiModelToolCallSupportResolverTests {

    private final ApiConfigService apiConfigService = mock(ApiConfigService.class);
    private final OllamaCapabilitiesClient ollamaCapabilitiesClient = mock(OllamaCapabilitiesClient.class);
    private final AiModelToolCallSupportResolver resolver = new AiModelToolCallSupportResolver(
            new AiModelMetadataResolver(apiConfigService),
            ollamaCapabilitiesClient);

    @Test
    void apiHostedPlatformsAreTreatedAsToolCallCapable() {
        for (String platform : new String[]{"deepseek", "dashscope", "openai_compatible", "gemini", "anthropic"}) {
            assertEquals(Boolean.TRUE, resolver.supportsToolCalls(
                    AiModel.builder().build(),
                    ApiConfig.builder().platform(platform).build()),
                    "平台 " + platform + " 应默认视为支持工具调用");
        }
        verifyNoInteractions(ollamaCapabilitiesClient);
    }

    @Test
    void ollamaDelegatesToCapabilitiesProbe() {
        AiModel model = AiModel.builder().code("qwen3:8b").build();
        when(ollamaCapabilitiesClient.supportsToolCalls("http://127.0.0.1:11434", "qwen3:8b"))
                .thenReturn(Boolean.TRUE);

        assertEquals(Boolean.TRUE, resolver.supportsToolCalls(model,
                ApiConfig.builder().platform("ollama").apiUrl("http://127.0.0.1:11434").build()));
    }

    @Test
    void ollamaOfflineMeansUnknown() {
        AiModel model = AiModel.builder().code("qwen3:8b").build();
        when(ollamaCapabilitiesClient.supportsToolCalls("http://localhost:11434", "qwen3:8b"))
                .thenReturn(null);

        assertNull(resolver.supportsToolCalls(model,
                ApiConfig.builder().platform("ollama").build()));
    }

    @Test
    void ollamaPrefersConfigModelNameOverCode() {
        AiModel model = AiModel.builder()
                .code("display-code")
                .config("{\"modelName\":\"qwen3:8b\"}")
                .build();
        when(ollamaCapabilitiesClient.supportsToolCalls("http://localhost:11434", "qwen3:8b"))
                .thenReturn(Boolean.FALSE);

        assertEquals(Boolean.FALSE, resolver.supportsToolCalls(model,
                ApiConfig.builder().platform("ollama").build()));
    }

    @Test
    void blankPlatformMeansUnknown() {
        assertNull(resolver.supportsToolCalls(AiModel.builder().build(), null));
        verifyNoInteractions(ollamaCapabilitiesClient);
    }

    @Test
    void resolveLoadsApiConfigById() {
        AiModel model = AiModel.builder().code("llama3").apiConfigId(7L).build();
        when(apiConfigService.getById(7L)).thenReturn(ApiConfig.builder().id(7L).platform("ollama").build());
        when(ollamaCapabilitiesClient.supportsToolCalls("http://localhost:11434", "llama3"))
                .thenReturn(Boolean.TRUE);

        assertEquals(Boolean.TRUE, resolver.supportsToolCalls(model));
    }
}

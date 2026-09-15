package com.stonewu.fusion.service.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ollama 能力探测客户端测试：
 * 覆盖 capabilities 含 tools、不含 tools、Ollama 不可达三种场景。
 */
class OllamaCapabilitiesClientTests {

    private final OllamaCapabilitiesClient client = new OllamaCapabilitiesClient();

    @Test
    void capabilitiesWithToolsMeansSupported() {
        String response = """
                {
                  "model": "qwen3:8b",
                  "capabilities": ["completion", "tools"]
                }
                """;

        assertEquals(Boolean.TRUE, client.parseToolCallSupport(response));
    }

    @Test
    void capabilitiesWithoutToolsMeansUnsupported() {
        String response = """
                {
                  "model": "qwen3:8b",
                  "capabilities": ["completion"]
                }
                """;

        assertEquals(Boolean.FALSE, client.parseToolCallSupport(response));
    }

    @Test
    void missingCapabilitiesMeansUnsupportedForLegacyOllama() {
        String response = """
                {
                  "model": "qwen3:8b",
                  "license": "..."
                }
                """;

        assertEquals(Boolean.FALSE, client.parseToolCallSupport(response));
    }

    @Test
    void blankResponseMeansUnknown() {
        assertNull(client.parseToolCallSupport(" "));
    }

    @Test
    void unreachableOllamaMeansUnknownInsteadOfError() {
        // 127.0.0.1:1 保留端口，连接必然被拒绝，模拟 Ollama 不在线
        assertNull(client.supportsToolCalls("http://127.0.0.1:1", "qwen3:8b"));
    }

    @Test
    void blankBaseUrlOrModelMeansUnknown() {
        assertNull(client.supportsToolCalls(" ", "qwen3:8b"));
        assertNull(client.supportsToolCalls("http://127.0.0.1:11434", " "));
    }
}

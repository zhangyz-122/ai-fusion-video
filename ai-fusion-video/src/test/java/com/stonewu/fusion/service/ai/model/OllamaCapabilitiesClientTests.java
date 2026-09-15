package com.stonewu.fusion.service.ai.model;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ollama 能力探测客户端测试：
 * 覆盖 capabilities 含 tools、不含 tools、Ollama 不可达三种场景，
 * 以及“未知”结果的短 TTL 负缓存（TTL 内不再重复发起探测）。
 */
class OllamaCapabilitiesClientTests {

    private final OllamaCapabilitiesClient client =
            new OllamaCapabilitiesClient(new ConcurrentMapCacheManager());

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

    @Test
    void unknownResultIsNegativeCachedSoNextQuerySkipsProbe() {
        ProbeCountingClient countingClient = new ProbeCountingClient(new ConcurrentMapCacheManager());

        // 第一次：Ollama 离线，真实发起探测并写入负缓存
        assertNull(countingClient.supportsToolCalls("http://127.0.0.1:1", "qwen3:8b"));
        assertEquals(1, countingClient.probes.get());

        // 第二次：负缓存命中，按未知快速返回，不再发起探测
        assertNull(countingClient.supportsToolCalls("http://127.0.0.1:1", "qwen3:8b"));
        assertEquals(1, countingClient.probes.get());
    }

    @Test
    void negativeCacheIsKeyedPerModelAndDoesNotHideRealResults() {
        ProbeCountingClient countingClient = new ProbeCountingClient(new ConcurrentMapCacheManager());
        countingClient.nextProbeResult = Boolean.TRUE;

        // 真实“支持”结果不进负缓存：第二次查询仍会探测（主缓存由 @Cacheable 承担）
        assertEquals(Boolean.TRUE, countingClient.supportsToolCalls("http://127.0.0.1:1", "llama3"));
        assertEquals(Boolean.TRUE, countingClient.supportsToolCalls("http://127.0.0.1:1", "llama3"));
        assertEquals(2, countingClient.probes.get());

        // 未探测过的模型不受其他模型负缓存影响，按未知处理并进负缓存
        countingClient.nextProbeResult = null;
        assertNull(countingClient.supportsToolCalls("http://127.0.0.1:1", "qwen3:8b"));
        assertTrue(countingClient.probesReturnedUnknown.get());
        assertEquals(3, countingClient.probes.get());

        // 未知结果已被负缓存：第二次不再发起探测
        assertNull(countingClient.supportsToolCalls("http://127.0.0.1:1", "qwen3:8b"));
        assertEquals(3, countingClient.probes.get());
    }

    /** 探测计数桩：把真实 HTTP 探测替换为可编程结果，统计探测发起次数 */
    static class ProbeCountingClient extends OllamaCapabilitiesClient {

        final AtomicInteger probes = new AtomicInteger();
        final AtomicBoolean probesReturnedUnknown = new AtomicBoolean();
        Boolean nextProbeResult;

        ProbeCountingClient(CacheManager cacheManager) {
            super(cacheManager);
        }

        @Override
        public Boolean probeToolCalls(String baseUrl, String modelName) {
            probes.incrementAndGet();
            Boolean result = nextProbeResult;
            if (result == null) {
                probesReturnedUnknown.set(true);
            }
            return result;
        }
    }
}

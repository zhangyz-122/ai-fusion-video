package com.stonewu.fusion.service.ai.model;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 模型能力查询客户端。
 * <p>
 * 通过 {@code POST /api/show} 读取模型 capabilities 列表，用于判断模型是否支持工具调用。
 * Ollama 不在线或响应异常时返回 null（未知），不向调用方抛出业务异常，避免拖垮模型列表接口。
 * <p>
 * 缓存分两层协同：探测成功（true/false）写入主缓存 {@code ollamaModelCapabilities}
 * （{@code unless = "#result == null"} 使 null 不进主缓存）；探测未知（null，如 Ollama
 * 离线）写入短 TTL 的负缓存 {@link #NEGATIVE_CACHE_NAME}，TTL 内不再重复发起探测，
 * 避免 Ollama 离线时每次下拉都全量重试。
 */
@Component
@Slf4j
public class OllamaCapabilitiesClient {

    private static final String SHOW_PATH = "/api/show";

    /** 负缓存名：只存“近期探测未知”的占位值，TTL 在 CacheConfig 中配置为 3 分钟（public 供 CacheConfig 注册同名缓存） */
    public static final String NEGATIVE_CACHE_NAME = "ollamaModelCapabilitiesNegative";
    /** 负缓存条目的占位值：仅用于判断“近期探测未知”，与真实能力语义无关 */
    private static final Boolean UNKNOWN_MARKER = Boolean.TRUE;

    /** 能力探测只服务于下拉提示，使用短超时避免 Ollama 离线时阻塞列表请求 */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private final CacheManager cacheManager;

    @Autowired
    public OllamaCapabilitiesClient(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** 供单元测试使用：未注入 CacheManager 时不启用负缓存 */
    OllamaCapabilitiesClient() {
        this(null);
    }

    /**
     * 判断 Ollama 模型是否支持工具调用。
     *
     * @param baseUrl   Ollama 服务地址（已去除尾部斜杠，非空）
     * @param modelName Ollama 侧的模型名
     * @return true 支持（capabilities 含 tools）；false 不支持；null 未知（Ollama 不可达或响应异常）
     */
    @Cacheable(value = "ollamaModelCapabilities", key = "#baseUrl + ':' + #modelName", unless = "#result == null")
    public Boolean supportsToolCalls(String baseUrl, String modelName) {
        if (StrUtil.isBlank(baseUrl) || StrUtil.isBlank(modelName)) {
            return null;
        }
        if (isRecentlyUnknown(baseUrl, modelName)) {
            // 负缓存命中：Ollama 刚被探测为不可达，短 TTL 内直接按未知返回，不再发起探测
            return null;
        }
        Boolean support = probeToolCalls(baseUrl, modelName);
        if (support == null) {
            markUnknown(baseUrl, modelName);
        }
        return support;
    }

    /**
     * 实际发起 {@code /api/show} 探测。设为 public 仅为便于测试子类桩定与探测计数，
     * 业务调用方一律通过 {@link #supportsToolCalls} 走缓存入口。
     */
    public Boolean probeToolCalls(String baseUrl, String modelName) {
        String url = baseUrl.replaceAll("/+$", "") + SHOW_PATH;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        JSONUtil.createObj().set("model", modelName).toString(),
                        MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[OllamaCapabilitiesClient] /api/show 响应异常: url={}, code={}", url, response.code());
                return null;
            }
            if (response.body() == null) {
                log.warn("[OllamaCapabilitiesClient] /api/show 响应体为空: url={}", url);
                return null;
            }
            return parseToolCallSupport(response.body().string());
        } catch (IOException | JSONException e) {
            // Ollama 不在线、网络不可达或响应不是 JSON：按未知处理，等待负缓存过期后再重试
            log.warn("[OllamaCapabilitiesClient] 查询 Ollama 模型能力失败: url={}, model={}, message={}",
                    url, modelName, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 /api/show 响应中的 capabilities。
     * <p>
     * 响应成功但缺少 capabilities 字段，说明 Ollama 版本早于工具调用支持，视为不支持。
     */
    Boolean parseToolCallSupport(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(responseBody);
        JSONArray capabilities = root.getJSONArray("capabilities");
        if (capabilities == null) {
            return Boolean.FALSE;
        }
        for (int index = 0; index < capabilities.size(); index++) {
            if ("tools".equalsIgnoreCase(capabilities.getStr(index))) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** 负缓存键与主缓存键保持同一格式，便于排查时对照 */
    private static String negativeCacheKey(String baseUrl, String modelName) {
        return baseUrl + ':' + modelName;
    }

    private boolean isRecentlyUnknown(String baseUrl, String modelName) {
        Cache cache = negativeCache();
        return cache != null && Boolean.TRUE.equals(
                cache.get(negativeCacheKey(baseUrl, modelName), Boolean.class));
    }

    private void markUnknown(String baseUrl, String modelName) {
        Cache cache = negativeCache();
        if (cache != null) {
            cache.put(negativeCacheKey(baseUrl, modelName), UNKNOWN_MARKER);
        }
    }

    private Cache negativeCache() {
        return cacheManager == null ? null : cacheManager.getCache(NEGATIVE_CACHE_NAME);
    }
}

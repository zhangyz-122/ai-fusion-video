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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 模型能力查询客户端。
 * <p>
 * 通过 {@code POST /api/show} 读取模型 capabilities 列表，用于判断模型是否支持工具调用。
 * Ollama 不在线或响应异常时返回 null（未知），不向调用方抛出业务异常，避免拖垮模型列表接口。
 */
@Component
@Slf4j
public class OllamaCapabilitiesClient {

    private static final String SHOW_PATH = "/api/show";

    /** 能力探测只服务于下拉提示，使用短超时避免 Ollama 离线时阻塞列表请求 */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

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
            // Ollama 不在线、网络不可达或响应不是 JSON：按未知处理，等待下次查询重试
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
}

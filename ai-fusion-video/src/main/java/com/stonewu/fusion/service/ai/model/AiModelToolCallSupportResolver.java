package com.stonewu.fusion.service.ai.model;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 解析模型是否支持工具调用。
 * <p>
 * API 托管平台（deepseek / dashscope / openai_compatible / gemini / anthropic 等）
 * 的主流对话模型均支持工具调用，统一视为支持，不为单个平台写特例猜测；
 * Ollama 平台的本地模型能力差异大，通过 /api/show 的 capabilities 实时判断。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelToolCallSupportResolver {

    private static final String PLATFORM_OLLAMA = "ollama";

    /** Ollama 默认服务地址，与 OllamaAiProvider 保持一致 */
    private static final String OLLAMA_DEFAULT_BASE_URL = "http://localhost:11434";

    private final AiModelMetadataResolver aiModelMetadataResolver;
    private final OllamaCapabilitiesClient ollamaCapabilitiesClient;

    /**
     * 解析模型是否支持工具调用。
     *
     * @return true 支持；false 不支持；null 未知（平台未配置或 Ollama 不可达）
     */
    public Boolean supportsToolCalls(AiModel model) {
        return supportsToolCalls(model, aiModelMetadataResolver.resolveApiConfig(model));
    }

    /**
     * 按已取得的 API 配置解析模型是否支持工具调用，避免调用方重复查询配置。
     */
    public Boolean supportsToolCalls(AiModel model, ApiConfig apiConfig) {
        String platform = aiModelMetadataResolver.normalizePlatform(
                apiConfig != null ? apiConfig.getPlatform() : null);
        if (StrUtil.isBlank(platform)) {
            return null;
        }
        if (PLATFORM_OLLAMA.equals(platform)) {
            String baseUrl = StrUtil.blankToDefault(apiConfig.getApiUrl(), OLLAMA_DEFAULT_BASE_URL);
            return ollamaCapabilitiesClient.supportsToolCalls(baseUrl, resolveOllamaModelName(model));
        }
        return Boolean.TRUE;
    }

    /**
     * Ollama 模型名与实际请求保持一致：优先模型 config JSON 的 modelName，其次模型 code。
     */
    private String resolveOllamaModelName(AiModel model) {
        if (model == null) {
            return "";
        }
        if (StrUtil.isNotBlank(model.getConfig())) {
            try {
                Object modelName = JSONUtil.parseObj(model.getConfig()).get("modelName");
                if (modelName != null && StrUtil.isNotBlank(modelName.toString())) {
                    return modelName.toString();
                }
            } catch (Exception e) {
                log.warn("[AiModelToolCallSupportResolver] 模型 config JSON 解析失败: modelId={}", model.getId(), e);
            }
        }
        return StrUtil.blankToDefault(model.getCode(), "");
    }
}

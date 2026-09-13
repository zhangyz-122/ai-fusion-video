package com.stonewu.fusion.service.ai.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.controller.ai.vo.RemoteModelVO;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.service.ai.proxy.AiProxySupport;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.ai.chat.model.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OpenAI 兼容提供商。
 */
@Component
@Slf4j
public class OpenAiCompatibleAiProvider extends AbstractAiProvider {

    /**
     * 火山方舟的官方 Chat API 文档没有提供通用的 /models 查询接口，
     * 只能使用模型 ID 调用 /api/v3/chat/completions。这里保留官方文档中的
     * 可直接用于 Chat API 示例模型，避免把火山引擎错误地请求成 OpenAI 的 /models。
     */
    private static final String VOLCENGINE_DOCUMENTED_CHAT_MODEL = "doubao-seed-2-1-pro-260628";
    private static final String VOLCENGINE_CONTROL_PLANE_URL =
            "https://ark.cn-beijing.volcengineapi.com/?Action=ListModelActivations&Version=2024-01-01";
    private static final String VOLCENGINE_REGION = "cn-beijing";
    private static final String VOLCENGINE_SERVICE = "ark";
    private static final DateTimeFormatter VOLCENGINE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter VOLCENGINE_SHORT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of(
            "openai_compatible", "openai", "deepseek", "zhipu", "moonshot", "volcengine",
            "volcengine_agent_plan", "siliconflow", "newapi");

    @Override
    public boolean supports(String platform) {
        return platform != null && SUPPORTED_PLATFORMS.contains(platform.toLowerCase());
    }

    @Override
    public ChatModel createChatModel(AiProviderContext context) {
        String platform = context.getPlatform();
        String apiKey = context.getApiKey();
        String baseUrl = resolveRootBaseUrl(platform, context.getBaseUrl());
        String completionsPath = resolveCompletionsPath(context);
        String embeddingsPath = resolveEmbeddingsPath(context);
        Map<String, Object> config = context.getConfig();
        String modelName = context.getModelName();

        requireApiKey(apiKey, "OpenAI Compatible (" + platform + ")");

        if (shouldUseResponsesApi(context)) {
            log.warn("[OpenAiCompatibleAiProvider] Responses API 目前仅接入 AgentScope 主链路，Spring AI ChatModel 仍回退到 chat/completions: model={}",
                    context.getModelName());
        }

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey);
        apiBuilder.restClientBuilder(AiProxySupport.restClientBuilder(
            context.getApiConfig(), 60 * 1000, 25 * 60 * 1000));
        apiBuilder.webClientBuilder(AiProxySupport.webClientBuilder(
            context.getApiConfig(), "openai-compatible-provider", Duration.ofMinutes(25)));
        if (StrUtil.isNotBlank(baseUrl)) {
            apiBuilder.baseUrl(baseUrl);
        }
        apiBuilder.completionsPath(completionsPath);
        apiBuilder.embeddingsPath(embeddingsPath);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
        applyDouble(config, "temperature", optionsBuilder::temperature);
        applyDouble(config, "topP", optionsBuilder::topP);
        applyInt(config, "maxTokens", optionsBuilder::maxTokens);

        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    @Override
    public ChatModelBase createAgentScopeModel(AiProviderContext context) {
        String platform = context.getPlatform();
        String apiKey = context.getApiKey();
        String baseUrl = resolveRootBaseUrl(platform, context.getBaseUrl());
        String endpointPath = resolveCompletionsPath(context);

        requireApiKey(apiKey, "OpenAI Compatible (" + platform + ")");

        GenerateOptions generateOptions = buildGenerateOptions(context);
        if (shouldUseResponsesApi(context)) {
            return new OpenAiResponsesAgentScopeModel(
                    context.getApiConfig(),
                    apiKey,
                    baseUrl,
                    context.getModelName(),
                    generateOptions);
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(context.getModelName())
                .stream(true);
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        if (StrUtil.isNotBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        builder.endpointPath(endpointPath);
        HttpTransport proxyTransport = AiProxySupport.agentScopeHttpTransport(context.getApiConfig());
        if (proxyTransport != null) {
            builder.httpTransport(proxyTransport);
        }
        return builder.build();
    }

    @Override
    public List<RemoteModelVO> listRemoteModels(AiProviderContext context) {
        if ("volcengine_agent_plan".equalsIgnoreCase(context.getPlatform())) {
            requireApiKey(context.getApiKey(), "Volcengine Agent Plan");
            return agentPlanModels();
        }
        if (isVolcengineContext(context)) {
            if (hasVolcengineAccessKey(context.getApiConfig())) {
                return listVolcengineActivatedModels(context);
            }
            requireApiKey(context.getApiKey(), "Volcengine Ark");
            log.info("[OpenAiCompatibleAiProvider] 火山方舟未提供通用模型列表接口，使用官方 Chat API 文档示例模型: {}",
                    VOLCENGINE_DOCUMENTED_CHAT_MODEL);
            return List.of(RemoteModelVO.builder()
                    .id(VOLCENGINE_DOCUMENTED_CHAT_MODEL)
                    .displayName("Doubao Seed 2.1 Pro（官方 Chat API 示例）")
                    .ownedBy("volcengine")
                    .providerPlatform("volcengine")
                    .modelType(1)
                    .modelProtocol("volcengine")
                    .inferredMetadata(true)
                    .build());
        }

        String rootBaseUrl = resolveRootBaseUrl(context.getPlatform(), context.getBaseUrl());
        String url = joinUrl(rootBaseUrl, resolveModelsPath(context));

        log.info("[OpenAiCompatibleAiProvider] 获取远程模型列表: {}", url);
        String response = executeGet(url, context.getApiKey() == null
                ? Map.of()
            : Map.of("Authorization", "Bearer " + context.getApiKey()), context.getApiConfig());
        return parseDataArrayModels(response, context.getPlatform());
    }

    private List<RemoteModelVO> agentPlanModels() {
        return List.of(
                agentPlanModel("doubao-seed-2.0-mini", "Doubao Seed 2.0 Mini", 1),
                agentPlanModel("doubao-seed-2.0-lite", "Doubao Seed 2.0 Lite", 1),
                agentPlanModel("deepseek-v4-flash", "DeepSeek V4 Flash", 1),
                agentPlanModel("glm-5.3-flash", "GLM 5.3 Flash", 1),
                agentPlanModel("doubao-seed-2.1-turbo", "Doubao Seed 2.1 Turbo", 1),
                agentPlanModel("doubao-seed-evolving", "Doubao Seed Evolving", 1),
                agentPlanModel("minimax-m3", "MiniMax M3", 1),
                agentPlanModel("glm-5.3", "GLM 5.3", 1),
                agentPlanModel("kimi-k2.7-code", "Kimi K2.7 Code", 1),
                agentPlanModel("deepseek-v4-pro", "DeepSeek V4 Pro", 1),
                agentPlanModel("kimi-k3", "Kimi K3", 1));
    }

    private RemoteModelVO agentPlanModel(String id, String displayName, Integer modelType) {
        return RemoteModelVO.builder()
                .id(id)
                .displayName(displayName)
                .ownedBy("volcengine-agent-plan")
                .providerPlatform("volcengine_agent_plan")
                .modelType(modelType)
                .modelProtocol("agent_plan")
                .inferredMetadata(true)
                .build();
    }

    private boolean hasVolcengineAccessKey(ApiConfig apiConfig) {
        return apiConfig != null
                && StrUtil.isNotBlank(apiConfig.getAppId())
                && StrUtil.isNotBlank(apiConfig.getAppSecret());
    }

    private List<RemoteModelVO> listVolcengineActivatedModels(AiProviderContext context) {
        ApiConfig apiConfig = context.getApiConfig();
        String accessKeyId = apiConfig.getAppId().trim();
        String secretAccessKey = apiConfig.getAppSecret().trim();
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("States", List.of("Available"));
        filter.put("VendorName", "火山方舟");
        filter.put("IncludeDeprecatedModels", false);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("PageNumber", 1);
        request.put("PageSize", 100);
        request.put("SortOrder", "Desc");
        request.put("SortBy", "VendorName");
        request.put("Filter", filter);
        String body = JSONUtil.toJsonStr(request);
        String xDate = VOLCENGINE_DATE_FORMATTER.format(Instant.now());
        String payloadHash = sha256Hex(body);
        Map<String, String> headers = signVolcengineRequest(
                accessKeyId, secretAccessKey, xDate, payloadHash);

        log.info("[OpenAiCompatibleAiProvider] 使用火山方舟 ListModelActivations 查询已开通模型");
        String response = executePost(VOLCENGINE_CONTROL_PLANE_URL, body, headers, apiConfig);
        return parseVolcengineActivatedModels(response);
    }

    private Map<String, String> signVolcengineRequest(String accessKeyId, String secretAccessKey,
                                                       String xDate, String payloadHash) {
        String host = "ark.cn-beijing.volcengineapi.com";
        String canonicalQuery = "Action=ListModelActivations&Version=2024-01-01";
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n"
                + "x-content-sha256:" + payloadHash + "\n"
                + "x-date:" + xDate + "\n";
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalRequest = "POST\n/\n" + canonicalQuery + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String shortDate = VOLCENGINE_SHORT_DATE_FORMATTER.format(Instant.now());
        String credentialScope = shortDate + "/" + VOLCENGINE_REGION + "/" + VOLCENGINE_SERVICE + "/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kDate = hmacSha256(("VOLC" + secretAccessKey).getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] kRegion = hmacSha256(kDate, VOLCENGINE_REGION);
        byte[] kService = hmacSha256(kRegion, VOLCENGINE_SERVICE);
        byte[] kSigning = hmacSha256(kService, "request");
        String signature = hex(hmacSha256(kSigning, stringToSign));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Host", host);
        headers.put("X-Date", xDate);
        headers.put("X-Content-Sha256", payloadHash);
        headers.put("Authorization", "HMAC-SHA256 Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return headers;
    }

    private List<RemoteModelVO> parseVolcengineActivatedModels(String json) {
        List<RemoteModelVO> models = new ArrayList<>();
        JSONObject root = JSONUtil.parseObj(json);
        JSONObject result = root.getJSONObject("Result");
        JSONArray items = result == null ? null : result.getJSONArray("Items");
        if (items == null) {
            return models;
        }
        for (int index = 0; index < items.size(); index++) {
            JSONObject item = items.getJSONObject(index);
            if (!"Available".equalsIgnoreCase(item.getStr("State"))) {
                continue;
            }
            String modelId = item.getStr("FoundationModelName");
            if (StrUtil.isBlank(modelId)) {
                continue;
            }
            models.add(RemoteModelVO.builder()
                    .id(modelId)
                    .displayName(StrUtil.blankToDefault(item.getStr("DisplayName"), modelId))
                    .ownedBy(StrUtil.blankToDefault(item.getStr("VendorName"), "volcengine"))
                    .providerPlatform("volcengine")
                    .modelType(volcengineModelType(item.getStr("FoundationModelDomain")))
                    .modelProtocol("volcengine")
                    .inferredMetadata(true)
                    .build());
        }
        models.sort(java.util.Comparator.comparing(RemoteModelVO::getId));
        return models;
    }

    private Integer volcengineModelType(String domain) {
        if ("Chat".equalsIgnoreCase(domain)) {
            return 1;
        }
        if ("Image".equalsIgnoreCase(domain)) {
            return 2;
        }
        if ("Video".equalsIgnoreCase(domain)) {
            return 3;
        }
        return null;
    }

    private String sha256Hex(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算火山引擎请求摘要", e);
        }
    }

    private byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成火山引擎请求签名", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private boolean isVolcengineContext(AiProviderContext context) {
        if ("volcengine".equalsIgnoreCase(context.getPlatform())) {
            return true;
        }
        ApiConfig apiConfig = context.getApiConfig();
        return apiConfig != null
                && ("volcengine".equalsIgnoreCase(apiConfig.getTextProtocol())
                || "volcengine".equalsIgnoreCase(apiConfig.getImageProtocol())
                || "volcengine".equalsIgnoreCase(apiConfig.getVideoProtocol()));
    }

    private GenerateOptions buildGenerateOptions(AiProviderContext context) {
        GenerateOptions.Builder builder = GenerateOptions.builder();
        boolean hasOptions = false;

        Double temperature = getConfigDoubleValue(context.getConfig(), "temperature");
        if (temperature != null) {
            builder.temperature(temperature);
            hasOptions = true;
        }

        Double topP = getConfigDoubleValue(context.getConfig(), "topP", "top_p");
        if (topP != null) {
            builder.topP(topP);
            hasOptions = true;
        }

        Integer maxTokens = getConfigInteger(context.getConfig(), "maxTokens", "max_tokens");
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
            builder.maxCompletionTokens(maxTokens);
            hasOptions = true;
        }

        String reasoningEffort = getConfigString(context.getConfig(), "reasoningEffort", "reasoning_effort");
        if (StrUtil.isNotBlank(reasoningEffort)) {
            builder.reasoningEffort(reasoningEffort);
            hasOptions = true;
        }

        Integer thinkingBudget = getConfigInteger(context.getConfig(), "thinkingBudget", "thinking_budget");
        if (thinkingBudget != null) {
            builder.thinkingBudget(thinkingBudget);
            hasOptions = true;
        }

        Boolean includeReasoning = getConfigBoolean(context.getConfig(), "includeReasoning", "include_reasoning");
        if (includeReasoning == null && isReasoningEnabled(context)) {
            includeReasoning = true;
        }
        if (includeReasoning != null) {
            builder.additionalBodyParam("include_reasoning", includeReasoning);
            hasOptions = true;
        }

        return hasOptions ? builder.build() : null;
    }

    private boolean shouldUseResponsesApi(AiProviderContext context) {
        Boolean useResponsesApi = getConfigBoolean(context.getConfig(),
                "useResponsesApi", "useResponses", "responseApi", "responsesApi");
        if (useResponsesApi != null) {
            return useResponsesApi;
        }

        String apiMode = getConfigString(context.getConfig(),
                "apiMode", "api_mode", "openaiApiMode", "openai_api_mode");
        if (StrUtil.isBlank(apiMode)) {
            return false;
        }

        String normalized = apiMode.trim().toLowerCase();
        return "responses".equals(normalized) || "response".equals(normalized);
    }

    private Double getConfigDoubleValue(Map<String, Object> config, String... keys) {
        Object value = getConfigValue(config, keys);
        if (value == null) {
            return null;
        }
        try {
            return toDouble(value);
        } catch (Exception e) {
            log.warn("[OpenAiCompatibleAiProvider] 参数解析失败: keys={}, value={}", String.join(",", keys), value);
            return null;
        }
    }

    private String resolveCompletionsPath(AiProviderContext context) {
        return switch (context.getPlatform().toLowerCase()) {
            case "zhipu" -> "/api/paas/v4/chat/completions";
            case "volcengine" -> "/api/v3/chat/completions";
            case "volcengine_agent_plan" -> "/chat/completions";
            default -> shouldAutoAppendV1Path(context) ? "/v1/chat/completions" : "/chat/completions";
        };
    }

    private String resolveEmbeddingsPath(AiProviderContext context) {
        return switch (context.getPlatform().toLowerCase()) {
            case "zhipu" -> "/api/paas/v4/embeddings";
            case "volcengine" -> "/api/v3/embeddings";
            case "volcengine_agent_plan" -> "/embeddings";
            default -> shouldAutoAppendV1Path(context) ? "/v1/embeddings" : "/embeddings";
        };
    }

    private String resolveModelsPath(AiProviderContext context) {
        return switch (context.getPlatform().toLowerCase()) {
            case "zhipu" -> "/api/paas/v4/models";
            case "volcengine" -> "/api/v3/models";
            case "volcengine_agent_plan" -> "/models";
            default -> shouldAutoAppendV1Path(context) ? "/v1/models" : "/models";
        };
    }

    private String resolveRootBaseUrl(String platform, String baseUrl) {
        return StrUtil.isBlank(baseUrl) ? inferRootBaseUrl(platform) : normalizeBaseUrl(baseUrl);
    }

    private boolean shouldAutoAppendV1Path(AiProviderContext context) {
        if (!"openai_compatible".equalsIgnoreCase(context.getPlatform())) {
            return true;
        }
        ApiConfig apiConfig = context.getApiConfig();
        return apiConfig == null || !Boolean.FALSE.equals(apiConfig.getAutoAppendV1Path());
    }

    private String inferRootBaseUrl(String platform) {
        return switch (platform.toLowerCase()) {
            case "deepseek" -> "https://api.deepseek.com";
            case "zhipu" -> "https://open.bigmodel.cn";
            case "volcengine" -> "https://ark.cn-beijing.volces.com";
            case "volcengine_agent_plan" -> "https://ark.cn-beijing.volces.com/api/plan/v3";
            case "moonshot" -> "https://api.moonshot.cn";
            case "siliconflow" -> "https://api.siliconflow.cn";
            case "newapi" -> "https://docs.newapi.ai";
            case "openai" -> "https://api.openai.com";
            default -> "https://api.openai.com";
        };
    }
}

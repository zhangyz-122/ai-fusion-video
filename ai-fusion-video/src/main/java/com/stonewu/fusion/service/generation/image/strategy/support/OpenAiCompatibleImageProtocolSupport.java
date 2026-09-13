package com.stonewu.fusion.service.generation.image.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.service.ai.ModelPresetService;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Facade for the OpenAI-compatible image protocol mechanics.
 *
 * <p>Responsibilities live in dedicated collaborators: request construction
 * and URL resolution in {@link ImageRequestBuilder}, response normalization in
 * {@link ImageResponseNormalizer}, media loading in {@link ImageMediaLoader}
 * and model-size mapping in {@link ImageSizeMapper}.  This class keeps the
 * historical public API (and static constants) stable for strategies and
 * protocol adapters, and intentionally contains no task orchestration or
 * persistence logic; those concerns stay in the strategy and protocol
 * adapters.</p>
 */
@Component
public class OpenAiCompatibleImageProtocolSupport {

    public static final String DEFAULT_BASE_URL = ImageRequestBuilder.DEFAULT_BASE_URL;
    public static final String DEFAULT_IMAGE_MODEL = ImageRequestBuilder.DEFAULT_IMAGE_MODEL;

    private final ModelPresetService modelPresetService;
    private final ImageRequestBuilder requestBuilder;
    private final ImageResponseNormalizer responseNormalizer;
    private final ImageSizeMapper sizeMapper;

    @Autowired
    public OpenAiCompatibleImageProtocolSupport(ModelPresetService modelPresetService,
                                                MediaStorageService mediaStorageService,
                                                StorageConfigService storageConfigService,
                                                PresetArtStyleResourceResolver presetArtStyleResourceResolver) {
        this(modelPresetService, mediaStorageService, storageConfigService,
                presetArtStyleResourceResolver, defaultHttpClient());
    }

    /** Convenient constructor for protocol-level tests and embedded callers. */
    public OpenAiCompatibleImageProtocolSupport(ModelPresetService modelPresetService,
                                                MediaStorageService mediaStorageService,
                                                StorageConfigService storageConfigService,
                                                PresetArtStyleResourceResolver presetArtStyleResourceResolver,
                                                OkHttpClient okHttpClient) {
        this.modelPresetService = modelPresetService;
        ImageMediaLoader mediaLoader = new ImageMediaLoader(
                storageConfigService, presetArtStyleResourceResolver,
                okHttpClient == null ? defaultHttpClient() : okHttpClient);
        this.sizeMapper = new ImageSizeMapper();
        this.responseNormalizer = new ImageResponseNormalizer(mediaStorageService);
        this.requestBuilder = new ImageRequestBuilder(sizeMapper, mediaLoader);
    }

    /** Build a request containing only fields defined by the official OpenAI Images API. */
    public OpenAiCompatibleImageRequest buildOpenAiGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        return requestBuilder.buildOpenAiGenerationsRequest(context);
    }

    /** Build an OpenAI-shaped request with NewAPI-compatible extension fields. */
    public OpenAiCompatibleImageRequest buildNewApiGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        return requestBuilder.buildNewApiGenerationsRequest(context);
    }

    /** Build the shared Agnes Image 2.0/2.1 generations protocol. */
    public OpenAiCompatibleImageRequest buildAgnesGenerationsRequest(OpenAiCompatibleImageProtocolContext context) {
        return requestBuilder.buildAgnesGenerationsRequest(context);
    }

    /** Build an official OpenAI image edit request using the model's declared reference-image transports. */
    public OpenAiCompatibleImageRequest buildOpenAiEditsRequest(OpenAiCompatibleImageProtocolContext context) {
        return requestBuilder.buildOpenAiEditsRequest(context);
    }

    /** Build an image edit request with fields commonly accepted by NewAPI-compatible gateways. */
    public OpenAiCompatibleImageRequest buildNewApiEditsRequest(OpenAiCompatibleImageProtocolContext context) {
        return requestBuilder.buildNewApiEditsRequest(context);
    }

    public List<String> parseImageUrls(String responseBody) {
        return responseNormalizer.parseImageUrls(responseBody, true, "兼容图片");
    }

    /** Parse only response fields documented by the official OpenAI Images API. */
    public List<String> parseOpenAiImageUrls(String responseBody) {
        return responseNormalizer.parseImageUrls(responseBody, false, "OpenAI");
    }

    public List<String> parseAgnesImageUrls(String responseBody) {
        return responseNormalizer.parseImageUrls(responseBody, false, "Agnes");
    }

    public String parseAsyncTaskId(String responseBody) {
        return responseNormalizer.parseAsyncTaskId(responseBody);
    }

    public OpenAiCompatibleImageAsyncTaskResult parseAsyncTaskResult(String responseBody) {
        return responseNormalizer.parseAsyncTaskResult(responseBody);
    }

    public String resolveImagesGenerateUrl(ApiConfig apiConfig) {
        return requestBuilder.resolveImagesGenerateUrl(apiConfig);
    }

    public String resolveAsyncTaskUrl(ApiConfig apiConfig, JSONObject modelConfig, String taskId) {
        return requestBuilder.resolveAsyncTaskUrl(apiConfig, modelConfig, taskId);
    }

    public JSONObject resolveModelConfig(String modelCode, AiModel model) {
        JSONObject merged = new JSONObject();
        String capabilityPresetCode = model != null ? model.getCapabilityPresetCode() : null;
        if (modelPresetService != null && StrUtil.isNotBlank(capabilityPresetCode)) {
            mergeConfig(merged, parseConfig(
                    modelPresetService.getPresetConfig(capabilityPresetCode), capabilityPresetCode));
        }
        if (model != null) {
            mergeConfig(merged, parseConfig(model.getConfig(), model.getCode()));
        }
        return merged.isEmpty() ? null : merged;
    }

    public boolean isAsyncMode(JSONObject modelConfig) {
        return Boolean.TRUE.equals(OpenAiCompatibleImageProtocolUtil.getBoolean(modelConfig,
                "asyncMode", "useAsyncMode", "asyncTaskMode", "enableAsyncTask", "asyncEnabled"));
    }

    public int[] resolveConfiguredSize(ImageTask task, JSONObject modelConfig) {
        return sizeMapper.resolveConfiguredSize(task, modelConfig);
    }

    public long resolveAsyncInitialDelayMillis(JSONObject config) {
        return OpenAiCompatibleImageProtocolUtil.secondsToMillis(OpenAiCompatibleImageProtocolUtil.getIntegerOrDefault(config, 10,
                "asyncTaskInitialDelaySeconds", "asyncInitialDelaySeconds", "taskInitialDelaySeconds"));
    }

    public long resolveAsyncPollIntervalMillis(JSONObject config) {
        return Math.max(100L, OpenAiCompatibleImageProtocolUtil.secondsToMillis(
                OpenAiCompatibleImageProtocolUtil.getIntegerOrDefault(config, 5,
                "asyncTaskPollIntervalSeconds", "asyncPollIntervalSeconds", "taskPollIntervalSeconds")));
    }

    public long resolveAsyncTimeoutMillis(JSONObject config) {
        return OpenAiCompatibleImageProtocolUtil.secondsToMillis(OpenAiCompatibleImageProtocolUtil.getIntegerOrDefault(config, 3600,
                "asyncTaskTimeoutSeconds", "asyncTimeoutSeconds", "taskTimeoutSeconds"));
    }

    public Boolean getBoolean(JSONObject config, String... keys) {
        return OpenAiCompatibleImageProtocolUtil.getBoolean(config, keys);
    }

    public String getString(JSONObject config, String... keys) {
        return OpenAiCompatibleImageProtocolUtil.getString(config, keys);
    }

    private JSONObject parseConfig(String configJson, String modelCode) {
        if (StrUtil.isBlank(configJson)) return null;
        try {
            return JSONUtil.parseObj(configJson);
        } catch (Exception e) {
            return null;
        }
    }

    private void mergeConfig(JSONObject target, JSONObject source) {
        if (target == null || source == null || source.isEmpty()) return;
        for (String key : source.keySet()) target.set(key, source.get(key));
    }

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .readTimeout(25, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}

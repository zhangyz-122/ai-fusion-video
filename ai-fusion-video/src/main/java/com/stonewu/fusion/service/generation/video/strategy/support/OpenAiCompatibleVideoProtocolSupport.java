package com.stonewu.fusion.service.generation.video.strategy.support;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 门面:OpenAI 兼容视频协议公共能力。
 *
 * <p>具体职责位于协作类:提交体与 URL 解析在 {@link VideoRequestBuilder},
 * 响应归一化在 {@link VideoResponseNormalizer},媒体装载在 {@link VideoMediaLoader},
 * 尺寸/时长映射在 {@link VideoSizeMapper}。本类保留历史公共 API 与
 * {@link BinaryResource} 嵌套类型,保证策略与协议适配器零改动。</p>
 */
@Component
@Slf4j
public class OpenAiCompatibleVideoProtocolSupport {

    private final VideoRequestBuilder requestBuilder;
    private final VideoResponseNormalizer responseNormalizer;
    private final VideoMediaLoader mediaLoader;
    private final VideoSizeMapper sizeMapper;

    public OpenAiCompatibleVideoProtocolSupport(StorageConfigService storageConfigService,
                                                PresetArtStyleResourceResolver presetArtStyleResourceResolver) {
        this.mediaLoader = new VideoMediaLoader(storageConfigService, presetArtStyleResourceResolver);
        this.sizeMapper = new VideoSizeMapper();
        this.responseNormalizer = new VideoResponseNormalizer();
        this.requestBuilder = new VideoRequestBuilder(sizeMapper, mediaLoader);
    }

    public RequestBody buildSoraSubmitBody(OpenAiCompatibleVideoProtocolContext context) {
        return requestBuilder.buildSoraSubmitBody(context);
    }

    public RequestBody buildAgnesSubmitBody(OpenAiCompatibleVideoProtocolContext context) {
        return requestBuilder.buildAgnesSubmitBody(context);
    }

    public String resolveOpenAiVideosUrl(ApiConfig apiConfig) {
        return requestBuilder.resolveOpenAiVideosUrl(apiConfig);
    }

    public String resolveFixedV1VideosUrl(ApiConfig apiConfig) {
        return requestBuilder.resolveFixedV1VideosUrl(apiConfig);
    }

    public String resolveApiRoot(ApiConfig apiConfig) {
        return requestBuilder.resolveApiRoot(apiConfig);
    }

    public String resolveModelCode(AiModel model) {
        return requestBuilder.resolveModelCode(model);
    }

    public String resolveAgnesQueryUrl(OpenAiCompatibleVideoProtocolContext context, String trackingId) {
        return requestBuilder.resolveAgnesQueryUrl(context, trackingId);
    }

    public List<String> resolveAgnesImageInputs(VideoTask task) {
        return requestBuilder.resolveAgnesImageInputs(task);
    }

    public String resolveSingleReferenceImageUrl(VideoTask task) {
        return requestBuilder.resolveSingleReferenceImageUrl(task);
    }

    public List<String> parseJsonUrls(String jsonUrls) {
        return requestBuilder.parseJsonUrls(jsonUrls);
    }

    public JsonNode readJson(String responseBody, String invalidMessage) {
        return responseNormalizer.readJson(responseBody, invalidMessage);
    }

    public String extractErrorMessage(String responseBody) {
        return responseNormalizer.extractErrorMessage(responseBody);
    }

    public String extractErrorMessage(JsonNode root) {
        return responseNormalizer.extractErrorMessage(root);
    }

    public String firstText(JsonNode node, String... fields) {
        return responseNormalizer.firstText(node, fields);
    }

    public String normalizeStatus(String status) {
        return responseNormalizer.normalizeStatus(status);
    }

    public Integer parsePositiveSeconds(String value) {
        return responseNormalizer.parsePositiveSeconds(value);
    }

    public String getString(JSONObject config, String... keys) {
        return OpenAiCompatibleVideoProtocolUtil.getString(config, keys);
    }

    public Integer getInteger(JSONObject config, String... keys) {
        return OpenAiCompatibleVideoProtocolUtil.getInteger(config, keys);
    }

    public Long getLong(JSONObject config, String... keys) {
        return OpenAiCompatibleVideoProtocolUtil.getLong(config, keys);
    }

    public Integer getPositiveInteger(JSONObject config, String... keys) {
        return OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(config, keys);
    }

    public Integer resolveDurationSeconds(VideoTask task, JSONObject modelConfig) {
        return sizeMapper.resolveDurationSeconds(task, modelConfig);
    }

    public Integer resolveAgnesFrameRate(JSONObject modelConfig) {
        return sizeMapper.resolveAgnesFrameRate(modelConfig);
    }

    public Integer resolveAgnesNumFrames(VideoTask task, JSONObject modelConfig, Integer frameRate) {
        return sizeMapper.resolveAgnesNumFrames(task, modelConfig, frameRate);
    }

    public int[] resolveAgnesDimensions(VideoTask task, JSONObject modelConfig) {
        return sizeMapper.resolveAgnesDimensions(task, modelConfig);
    }

    public OpenAiCompatibleVideoProtocolSupport.BinaryResource loadBinaryResource(String sourceUrl, ApiConfig apiConfig) throws IOException {
        return mediaLoader.loadBinaryResource(sourceUrl, apiConfig);
    }

    public MediaType mediaTypeOrDefault(String mimeType) {
        return mediaLoader.mediaTypeOrDefault(mimeType);
    }

    public String extensionFromImageMimeType(String mimeType) {
        return mediaLoader.extensionFromImageMimeType(mimeType);
    }

    public String extensionFromVideoMimeType(String contentType) {
        return mediaLoader.extensionFromVideoMimeType(contentType);
    }

    public record BinaryResource(byte[] bytes, String mimeType, String extension) {
    }
}

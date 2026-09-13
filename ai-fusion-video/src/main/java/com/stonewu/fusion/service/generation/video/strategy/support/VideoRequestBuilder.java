package com.stonewu.fusion.service.generation.video.strategy.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.VideoTask;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HTTP request construction for OpenAI-compatible video protocols: Sora
 * multipart and Agnes JSON submit bodies plus endpoint URL resolution and
 * task reference-image input collection.  Extracted verbatim from
 * {@link OpenAiCompatibleVideoProtocolSupport}; behavior is unchanged.
 */
public class VideoRequestBuilder {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_VIDEO_MODEL = "sora-2";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final VideoSizeMapper sizeMapper;
    private final VideoMediaLoader mediaLoader;

    public VideoRequestBuilder(VideoSizeMapper sizeMapper, VideoMediaLoader mediaLoader) {
        this.sizeMapper = sizeMapper;
        this.mediaLoader = mediaLoader;
    }

    public RequestBody buildSoraSubmitBody(OpenAiCompatibleVideoProtocolContext context) {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", resolveModelCode(context.model()));

        String prompt = StrUtil.trim(context.task().getPrompt());
        if (StrUtil.isNotBlank(prompt)) {
            builder.addFormDataPart("prompt", prompt);
        }

        String seconds = resolveSeconds(context.task(), context.modelConfig());
        if (StrUtil.isNotBlank(seconds)) {
            builder.addFormDataPart("seconds", seconds);
        }

        String size = resolveSize(context.task(), context.modelConfig());
        if (StrUtil.isNotBlank(size)) {
            builder.addFormDataPart("size", size);
        }

        String referenceImageUrl = resolveSingleReferenceImageUrl(context.task());
        if (StrUtil.isNotBlank(referenceImageUrl)) {
            OpenAiCompatibleVideoProtocolSupport.BinaryResource resource;
            try {
                resource = mediaLoader.loadBinaryResource(referenceImageUrl, context.apiConfig());
            } catch (IOException e) {
                throw new BusinessException("加载 OpenAI 视频参考图失败: " + e.getMessage());
            }
            builder.addFormDataPart(
                    "input_reference",
                    "reference." + resource.extension(),
                    RequestBody.create(resource.bytes(), mediaLoader.mediaTypeOrDefault(resource.mimeType()))
            );
        }

        return builder.build();
    }

    public RequestBody buildAgnesSubmitBody(OpenAiCompatibleVideoProtocolContext context) {
        JSONObject body = JSONUtil.createObj();
        String modelCode = resolveModelCode(context.model());
        String prompt = StrUtil.trim(context.task().getPrompt());
        if (StrUtil.isBlank(prompt)) {
            throw new BusinessException("Agnes 视频任务缺少 prompt");
        }

        body.set("model", modelCode);
        body.set("prompt", prompt);

        int[] dimensions = sizeMapper.resolveAgnesDimensions(context.task(), context.modelConfig());
        body.set("width", dimensions[0]);
        body.set("height", dimensions[1]);

        Integer frameRate = sizeMapper.resolveAgnesFrameRate(context.modelConfig());
        if (frameRate != null) {
            body.set("frame_rate", frameRate);
        }

        Integer numFrames = sizeMapper.resolveAgnesNumFrames(context.task(), context.modelConfig(), frameRate);
        if (numFrames != null) {
            body.set("num_frames", numFrames);
        }

        appendOptionalInteger(body, "num_inference_steps",
                OpenAiCompatibleVideoProtocolUtil.getPositiveInteger(
                        context.modelConfig(), "numInferenceSteps", "num_inference_steps"));
        appendOptionalString(body, "negative_prompt",
                OpenAiCompatibleVideoProtocolUtil.getString(context.modelConfig(), "negativePrompt", "negative_prompt"));

        if (context.task().getSeed() != null) {
            body.set("seed", context.task().getSeed());
        }

        List<String> imageInputs = resolveAgnesImageInputs(context.task());
        JSONObject extraBody = OpenAiCompatibleVideoProtocolUtil.asJsonObject(context.modelConfig().get("agnesExtraBody"));
        if (extraBody == null) {
            extraBody = OpenAiCompatibleVideoProtocolUtil.asJsonObject(context.modelConfig().get("extraBody"));
        }
        if (extraBody == null) {
            extraBody = new JSONObject();
        }

        if (imageInputs.size() == 1) {
            body.set("image", imageInputs.get(0));
        } else if (!imageInputs.isEmpty()) {
            extraBody.set("image", imageInputs);
        }

        String mode = resolveAgnesMode(context.task(), context.modelConfig(), imageInputs.size());
        if (StrUtil.isNotBlank(mode)) {
            if (!extraBody.isEmpty() || imageInputs.size() > 1) {
                extraBody.set("mode", mode);
            } else {
                body.set("mode", mode);
            }
        }

        if (!extraBody.isEmpty()) {
            body.set("extra_body", extraBody);
        }

        return RequestBody.create(body.toString(), JSON_MEDIA_TYPE);
    }

    public String resolveOpenAiVideosUrl(ApiConfig apiConfig) {
        String baseUrl = normalizeBaseUrl(StrUtil.blankToDefault(apiConfig != null ? apiConfig.getApiUrl() : null,
                DEFAULT_BASE_URL));
        if (endsWithIgnoreCase(baseUrl, "/videos")) {
            return baseUrl;
        }
        if (endsWithIgnoreCase(baseUrl, "/v1")) {
            return baseUrl + "/videos";
        }
        boolean appendV1 = shouldAutoAppendV1Path(apiConfig);
        return baseUrl + (appendV1 ? "/v1/videos" : "/videos");
    }

    public String resolveFixedV1VideosUrl(ApiConfig apiConfig) {
        return resolveApiRoot(apiConfig) + "/v1/videos";
    }

    public String resolveApiRoot(ApiConfig apiConfig) {
        String baseUrl = normalizeBaseUrl(StrUtil.blankToDefault(apiConfig != null ? apiConfig.getApiUrl() : null,
                DEFAULT_BASE_URL));
        if (endsWithIgnoreCase(baseUrl, "/v1/videos")) {
            return baseUrl.substring(0, baseUrl.length() - "/v1/videos".length());
        }
        if (endsWithIgnoreCase(baseUrl, "/videos")) {
            return baseUrl.substring(0, baseUrl.length() - "/videos".length());
        }
        if (endsWithIgnoreCase(baseUrl, "/v1")) {
            return baseUrl.substring(0, baseUrl.length() - "/v1".length());
        }
        return baseUrl;
    }

    public String resolveModelCode(AiModel model) {
        return model != null && StrUtil.isNotBlank(model.getCode()) ? model.getCode() : DEFAULT_VIDEO_MODEL;
    }

    public String resolveAgnesQueryUrl(OpenAiCompatibleVideoProtocolContext context, String trackingId) {
        StringBuilder builder = new StringBuilder(resolveApiRoot(context.apiConfig()))
                .append("/agnesapi?video_id=")
                .append(URLEncoder.encode(StrUtil.blankToDefault(trackingId, ""), StandardCharsets.UTF_8));

        String modelCode = resolveModelCode(context.model());
        if (StrUtil.isNotBlank(modelCode)) {
            builder.append("&model_name=")
                    .append(URLEncoder.encode(modelCode, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    public List<String> resolveAgnesImageInputs(VideoTask task) {
        Set<String> ordered = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(task.getFirstFrameImageUrl())) {
            ordered.add(task.getFirstFrameImageUrl().trim());
        }
        ordered.addAll(parseJsonUrls(task.getReferenceImageUrls()));
        if (StrUtil.isNotBlank(task.getLastFrameImageUrl())) {
            ordered.add(task.getLastFrameImageUrl().trim());
        }
        return new ArrayList<>(ordered);
    }

    public String resolveSingleReferenceImageUrl(VideoTask task) {
        if (StrUtil.isNotBlank(task.getFirstFrameImageUrl())) {
            return task.getFirstFrameImageUrl();
        }
        List<String> referenceImages = parseJsonUrls(task.getReferenceImageUrls());
        return referenceImages.isEmpty() ? null : referenceImages.get(0);
    }

    public List<String> parseJsonUrls(String jsonUrls) {
        List<String> urls = new ArrayList<>();
        if (StrUtil.isBlank(jsonUrls)) {
            return urls;
        }

        String trimmed = jsonUrls.trim();
        if (!trimmed.startsWith("[")) {
            if (StrUtil.isNotBlank(trimmed)) {
                urls.add(trimmed);
            }
            return urls;
        }

        try {
            JSONArray array = JSONUtil.parseArray(trimmed);
            for (Object item : array) {
                String value = item == null ? null : item.toString().trim();
                if (StrUtil.isNotBlank(value)) {
                    urls.add(value);
                }
            }
            return urls;
        } catch (Exception e) {
            throw new BusinessException("解析参考图列表失败: " + e.getMessage());
        }
    }

    private String resolveSeconds(VideoTask task, JSONObject modelConfig) {
        Integer duration = sizeMapper.resolveDurationSeconds(task, modelConfig);
        return duration != null ? String.valueOf(duration) : null;
    }

    private String resolveSize(VideoTask task, JSONObject modelConfig) {
        String resolution = sizeMapper.normalizeSize(task.getResolution());
        if (StrUtil.isNotBlank(resolution)) {
            return resolution;
        }
        return sizeMapper.normalizeSize(OpenAiCompatibleVideoProtocolUtil.getString(
                modelConfig, "size", "defaultResolution", "resolution"));
    }

    private String resolveAgnesMode(VideoTask task, JSONObject modelConfig, int imageCount) {
        String explicitMode = OpenAiCompatibleVideoProtocolUtil.getString(modelConfig, "agnesMode", "mode");
        if (StrUtil.isNotBlank(explicitMode)) {
            return explicitMode;
        }

        String generateMode = StrUtil.blankToDefault(task.getGenerateMode(), "").toLowerCase(Locale.ROOT);
        if (generateMode.contains("keyframe")) {
            return "keyframes";
        }
        if (StrUtil.isNotBlank(task.getLastFrameImageUrl()) && imageCount >= 2) {
            return "keyframes";
        }
        return null;
    }

    private boolean shouldAutoAppendV1Path(ApiConfig apiConfig) {
        if (apiConfig == null) {
            return true;
        }
        return !Boolean.FALSE.equals(apiConfig.getAutoAppendV1Path());
    }

    private String normalizeBaseUrl(String baseUrl) {
        return StrUtil.blankToDefault(baseUrl, DEFAULT_BASE_URL).trim().replaceAll("/+$", "");
    }

    private boolean endsWithIgnoreCase(String text, String suffix) {
        return text != null && suffix != null && text.toLowerCase(Locale.ROOT)
                .endsWith(suffix.toLowerCase(Locale.ROOT));
    }

    private void appendOptionalString(JSONObject target, String key, String value) {
        if (target != null && StrUtil.isNotBlank(value)) {
            target.set(key, value);
        }
    }

    private void appendOptionalInteger(JSONObject target, String key, Integer value) {
        if (target != null && value != null) {
            target.set(key, value);
        }
    }
}

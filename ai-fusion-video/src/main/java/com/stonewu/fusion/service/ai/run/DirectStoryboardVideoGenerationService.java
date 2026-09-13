package com.stonewu.fusion.service.ai.run;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.ai.tool.generation.GenerateVideoToolExecutor;
import com.stonewu.fusion.service.ai.tool.storyboard.UpdateStoryboardItemVideoToolExecutor;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.generation.GenerationModelCapabilityService;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic video pipeline for one storyboard shot.
 *
 * It resolves the actual model capabilities before building the request, so a
 * model such as the local H3 workflow receives its available reference-image
 * input instead of an invalid first/last-frame field or no image at all.
 */
@Service
@RequiredArgsConstructor
public class DirectStoryboardVideoGenerationService {

    private static final int VIDEO_MODEL_TYPE = 3;
    private static final Pattern ITEM_ID = Pattern.compile("storyboardItemId\\s*[\\\"']?\\s*[:=]\\s*(\\d+)");
    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*[\\\"']?\\s*[:=]\\s*(\\d+)");
    private static final Pattern GENERIC_ITEM_ID = Pattern.compile("镜头\\s*(?:编号|ID|#|：|:)\\s*(\\d+)");
    private static final Pattern PROMPT_ONLY = Pattern.compile("promptOnly\\s*[:=]\\s*true", Pattern.CASE_INSENSITIVE);

    private final StoryboardService storyboardService;
    private final ProjectService projectService;
    private final AssetService assetService;
    private final ToolResourceAccessGuard accessGuard;
    private final AiModelService aiModelService;
    private final GenerationModelCapabilityService capabilityService;
    private final GenerateVideoToolExecutor generateVideoTool;
    private final UpdateStoryboardItemVideoToolExecutor updateVideoTool;

    public String execute(String message, ToolExecutionContext context) {
        return execute(message, context, null);
    }

    public String execute(String message, ToolExecutionContext context, Long defaultProjectId) {
        Long itemId = extract(ITEM_ID, message);
        if (itemId == null) itemId = extract(GENERIC_ITEM_ID, message);
        Long projectId = extract(PROJECT_ID, message);
        if (projectId == null) projectId = defaultProjectId;
        if (itemId == null || projectId == null) {
            return error("缺少 storyboardItemId 或 projectId");
        }

        StoryboardItem item = accessGuard.requireStoryboardItem(itemId, context.getUserId());
        Project project = accessGuard.requireProject(projectId, context.getUserId());
        if (item.getStoryboardId() == null
                || !projectId.equals(storyboardService.getById(item.getStoryboardId()).getProjectId())) {
            return error("分镜镜头不属于指定项目");
        }

        String prompt = buildPrompt(project, item);
        if (PROMPT_ONLY.matcher(message == null ? "" : message).find()) {
            JSONObject updated = JSONUtil.parseObj(updateVideoTool.execute(
                    JSONUtil.createObj()
                            .set("storyboardItemId", itemId)
                            .set("videoPrompt", prompt)
                            .toString(), context));
            return updated.toString();
        }

        AiModel model = resolveVideoModel();
        GenerationModelCapabilityService.VideoModelCapability capability =
                capabilityService.resolveVideoCapability(model);
        JSONObject params = JSONUtil.createObj()
                .set("prompt", prompt)
                .set("ratio", "16:9")
                .set("duration", resolveDuration(item));

        String firstFrame = item.getFirstFrameImageUrl();
        String lastFrame = item.getLastFrameImageUrl();
        if (capability.supportsFirstFrame() && StrUtil.isNotBlank(firstFrame)) {
            params.set("firstFrameImageUrl", firstFrame);
            if (capability.supportsLastFrame() && StrUtil.isNotBlank(lastFrame)) {
                params.set("lastFrameImageUrl", lastFrame);
            }
        } else if (capability.supportsReferenceImages()) {
            // H3 is configured as a reference-image workflow: its first frame
            // becomes 图片1, never an unsupported firstFrameImageUrl field.
            List<String> refs = new ArrayList<>();
            if (StrUtil.isNotBlank(firstFrame)) refs.add(firstFrame);
            if (refs.isEmpty()) refs.addAll(collectAssetImages(item));
            if (capability.maxReferenceImages() != null
                    && refs.size() > capability.maxReferenceImages()) {
                refs = refs.subList(0, capability.maxReferenceImages());
            }
            if (!refs.isEmpty()) params.set("referenceImageUrls", refs);
        } else if (StrUtil.isNotBlank(lastFrame) && capability.supportsLastFrame()) {
            params.set("lastFrameImageUrl", lastFrame);
        }

        JSONObject generated = JSONUtil.parseObj(generateVideoTool.execute(
                params.toString(), context));
        if (!"success".equalsIgnoreCase(generated.getStr("status"))) {
            return generated.toString();
        }
        String videoUrl = generated.getStr("videoUrl");
        if (StrUtil.isBlank(videoUrl)) {
            return error("视频生成成功但没有返回视频地址");
        }

        JSONObject updated = JSONUtil.parseObj(updateVideoTool.execute(
                JSONUtil.createObj()
                        .set("storyboardItemId", itemId)
                        .set("videoUrl", videoUrl)
                        .set("videoPrompt", prompt)
                        .set("coverUrl", generated.getStr("coverUrl"))
                        .toString(), context));
        if (!"success".equalsIgnoreCase(updated.getStr("status"))) {
            return error("视频已生成，但保存到分镜失败：" + updated.getStr("message"));
        }

        return JSONUtil.createObj()
                .set("status", "success")
                .set("storyboardItemId", itemId)
                .set("videoUrl", videoUrl)
                .set("message", "分镜视频已生成并保存")
                .toString();
    }

    private AiModel resolveVideoModel() {
        AiModel model = aiModelService.getDefaultByType(VIDEO_MODEL_TYPE);
        if (model != null) return model;
        List<AiModel> models = aiModelService.getListByType(VIDEO_MODEL_TYPE);
        if (!models.isEmpty()) return models.get(0);
        throw new IllegalStateException("未配置可用的视频生成模型");
    }

    private List<String> collectAssetImages(StoryboardItem item) {
        Set<String> result = new LinkedHashSet<>();
        addAssetImages(result, item.getCharacterIds());
        if (item.getSceneAssetItemId() != null) addAssetImage(result, item.getSceneAssetItemId());
        addAssetImages(result, item.getPropIds());
        return new ArrayList<>(result);
    }

    private void addAssetImages(Set<String> result, String idsJson) {
        if (StrUtil.isBlank(idsJson)) return;
        try {
            JSONArray ids = JSONUtil.parseArray(idsJson);
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.getLong(i);
                if (id != null) addAssetImage(result, id);
            }
        } catch (Exception ignored) {
            // Optional asset references must not block video generation.
        }
    }

    private void addAssetImage(Set<String> result, Long id) {
        try {
            AssetItem assetItem = assetService.getItemById(id);
            if (assetItem != null && StrUtil.isNotBlank(assetItem.getImageUrl())) {
                result.add(assetItem.getImageUrl());
            }
        } catch (Exception ignored) {
            // Ignore a missing optional asset reference.
        }
    }

    private String buildPrompt(Project project, StoryboardItem item) {
        String style = firstNonBlank(project.getArtStyleDescription(), project.getArtStyleImagePrompt(),
                "国漫半写实漫剧风格，清晰线稿与细腻厚涂，统一角色设计，插画动画质感");
        String content = firstNonBlank(item.getContent(), item.getSceneExpectation(), "按照分镜画面自然演绎");
        String movement = firstNonBlank(item.getCameraMovement(), "自然稳定");
        String sound = firstNonBlank(item.getSound(), item.getSoundEffect(), "自然环境音");
        String dialogue = StrUtil.blankToDefault(item.getDialogue(), "");
        return style + "。保持参考画面中的人物、服装、场景和道具造型一致。"
                + "分镜内容：" + content + "。"
                + "镜头运动：" + movement + "；景别：" + firstNonBlank(item.getShotType(), "中景")
                + "；机位：" + firstNonBlank(item.getCameraAngle(), "平视") + "。"
                + (StrUtil.isBlank(dialogue) ? "人物不说台词。" : "对白必须清晰可听，人物必须清晰张嘴并连续口型同步：" + dialogue + "。")
                + "声音：" + sound + "。无背景音乐。画面绝对禁止字幕、对白文字、翻译字幕、内嵌文字、"
                + "水印、Logo、UI、角标和任何可读文字；禁止真人摄影质感、现代物品和白色参考图背景。";
    }

    private int resolveDuration(StoryboardItem item) {
        if (item.getDuration() == null) return 5;
        return Math.max(1, item.getDuration().setScale(0, java.math.RoundingMode.HALF_UP).intValue());
    }

    private Long extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (StrUtil.isNotBlank(value)) return value.trim();
        return "";
    }

    private String error(String message) {
        return JSONUtil.createObj().set("status", "error").set("message", message).toString();
    }
}

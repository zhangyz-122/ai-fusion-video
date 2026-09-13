package com.stonewu.fusion.service.ai.run;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.ai.tool.generation.GenerateImageToolExecutor;
import com.stonewu.fusion.service.ai.tool.storyboard.UpdateStoryboardItemFrameToolExecutor;
import com.stonewu.fusion.service.asset.AssetService;
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
 * Deterministic pipeline for one storyboard first/last frame.
 *
 * The parent/child agents can still organize the task, but they must not be
 * able to report success before an image was generated and written back.
 */
@Service
@RequiredArgsConstructor
public class DirectStoryboardFrameGenerationService {

    private static final Pattern ITEM_ID = Pattern.compile("storyboardItemId\\s*[:=]\\s*(\\d+)");
    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*[:=]\\s*(\\d+)");
    private static final Pattern FRAME_TYPE = Pattern.compile("frameType\\s*[:=]\\s*(first|last)");
    private static final Pattern FRAME_PROMPT = Pattern.compile("(?s)framePrompt\\s*[:=]\\s*(.+)");

    private final StoryboardService storyboardService;
    private final ProjectService projectService;
    private final AssetService assetService;
    private final ToolResourceAccessGuard accessGuard;
    private final GenerateImageToolExecutor generateImageTool;
    private final UpdateStoryboardItemFrameToolExecutor updateFrameTool;

    public String execute(String message, ToolExecutionContext context) {
        Long itemId = extract(ITEM_ID, message);
        Long projectId = extract(PROJECT_ID, message);
        String frameType = extractText(FRAME_TYPE, message);
        String framePrompt = extractFramePrompt(message);
        if (itemId == null || projectId == null || StrUtil.isBlank(frameType)
                || StrUtil.isBlank(framePrompt)) {
            return error("缺少 storyboardItemId、projectId、frameType 或 framePrompt");
        }

        StoryboardItem item = accessGuard.requireStoryboardItem(itemId, context.getUserId());
        Project project = accessGuard.requireProject(projectId, context.getUserId());
        if (item.getStoryboardId() == null
                || !projectId.equals(storyboardService.getById(item.getStoryboardId()).getProjectId())) {
            return error("分镜镜头不属于指定项目");
        }

        List<String> referenceImages = collectReferenceImages(item);
        String prompt = buildPrompt(project, item, frameType, framePrompt);
        JSONObject params = JSONUtil.createObj().set("prompt", prompt);
        if (!referenceImages.isEmpty()) {
            params.set("imageUrls", referenceImages);
        }

        JSONObject generated = JSONUtil.parseObj(generateImageTool.execute(
                params.toString(), context));
        if (!"success".equalsIgnoreCase(generated.getStr("status"))) {
            return generated.toString();
        }
        String imageUrl = generated.getStr("imageUrl");
        if (StrUtil.isBlank(imageUrl)) {
            return error("生图成功但没有返回图片地址");
        }

        JSONObject update = JSONUtil.parseObj(updateFrameTool.execute(
                JSONUtil.createObj()
                        .set("storyboardItemId", itemId)
                        .set("frameType", frameType)
                        .set("imageUrl", imageUrl)
                        .set("framePrompt", framePrompt)
                        .toString(), context));
        if (!"success".equalsIgnoreCase(update.getStr("status"))) {
            return error("图片已生成，但保存到分镜失败：" + update.getStr("message"));
        }

        return JSONUtil.createObj()
                .set("status", "success")
                .set("storyboardItemId", itemId)
                .set("frameType", frameType)
                .set("imageUrl", imageUrl)
                .set("message", "分镜" + ("first".equals(frameType) ? "首帧" : "尾帧") + "已生成并保存")
                .toString();
    }

    private List<String> collectReferenceImages(StoryboardItem item) {
        Set<String> result = new LinkedHashSet<>();
        addAssetImages(result, item.getCharacterIds());
        if (item.getSceneAssetItemId() != null) {
            addAssetImage(result, item.getSceneAssetItemId());
        }
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
            // Older rows may contain malformed optional asset JSON.
        }
    }

    private void addAssetImage(Set<String> result, Long itemId) {
        try {
            AssetItem assetItem = assetService.getItemById(itemId);
            if (assetItem != null && StrUtil.isNotBlank(assetItem.getImageUrl())) {
                result.add(assetItem.getImageUrl());
            }
        } catch (Exception ignored) {
            // A missing optional reference must not prevent the frame itself from generating.
        }
    }

    private String buildPrompt(Project project, StoryboardItem item, String frameType, String framePrompt) {
        String style = firstNonBlank(project.getArtStyleDescription(), project.getArtStyleImagePrompt(),
                "国漫半写实漫剧风格，清晰线稿与细腻厚涂，统一角色设计，真实但明显为插画动画质感");
        String content = firstNonBlank(item.getContent(), item.getSceneExpectation(), "按照镜头设定表现画面");
        String expectation = firstNonBlank(item.getSceneExpectation(), item.getContent(), "自然、连贯的镜头状态");
        String state = "first".equals(frameType)
                ? "视频开始时的定格状态，动作刚开始或尚未完成"
                : "视频结束时的定格状态，动作已经完成，能自然作为视频最后一帧";
        return "项目画风：" + style + "。"
                + "生成一张单一连续的分镜" + ("first".equals(frameType) ? "首" : "尾") + "帧画面。"
                + "帧状态：" + state + "。"
                + "用户确认的帧要求：" + framePrompt + "。"
                + "镜头画面内容：" + content + "。"
                + "画面期望：" + expectation + "。"
                + "景别：" + firstNonBlank(item.getShotType(), "中景") + "；机位："
                + firstNonBlank(item.getCameraAngle(), "平视") + "；运镜参考："
                + firstNonBlank(item.getCameraMovement(), "自然稳定") + "。"
                + "保持国漫角色和场景造型统一，画面不要出现字幕、对白文字、翻译字幕、内嵌文字、可读文字、"
                + "水印、Logo、UI、角标、现代物品或真人摄影质感；不要把参考图做成拼图、白底目录或多格分屏。";
    }

    private Long extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String extractText(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractFramePrompt(String message) {
        if (message == null) return null;
        Matcher matcher = FRAME_PROMPT.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) return value.trim();
        }
        return "";
    }

    private String error(String message) {
        return JSONUtil.createObj().set("status", "error").set("message", message).toString();
    }
}

package com.stonewu.fusion.service.ai.run;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.asset.UpdateAssetImageToolExecutor;
import com.stonewu.fusion.service.ai.tool.generation.GenerateImageToolExecutor;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic asset-image pipeline used by the platform asset-image sub-agent.
 * The language model may describe the image, but it must not decide whether a
 * variant keeps the initial asset reference: that decision belongs here.
 */
@Service
@RequiredArgsConstructor
public class DirectAssetImageGenerationService {

    private static final Pattern ASSET_ID = Pattern.compile("assetId\\s*[:=]\\s*(\\d+)");
    private static final Pattern ITEM_ID = Pattern.compile("itemId\\s*[:=]\\s*(\\d+)");
    private static final Pattern PROJECT_ID = Pattern.compile("projectId\\s*[:=]\\s*(\\d+)");

    private final AssetService assetService;
    private final ProjectService projectService;
    private final GenerateImageToolExecutor generateImageTool;
    private final UpdateAssetImageToolExecutor updateAssetImageTool;

    public String execute(String message, ToolExecutionContext context) {
        Long assetId = extract(ASSET_ID, message);
        Long itemId = extract(ITEM_ID, message);
        Long projectId = extract(PROJECT_ID, message);
        if (assetId == null || itemId == null || projectId == null) {
            return error("缺少 assetId、itemId 或 projectId");
        }

        Asset asset = assetService.getById(assetId);
        if (!assetService.canAccessAsset(asset, context.getUserId())) {
            return error("无权访问指定资产");
        }
        Project project = projectService.getById(projectId);
        List<AssetItem> items = assetService.listItems(assetId);
        AssetItem target = items.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return error("子资产ID " + itemId + " 不属于资产ID " + assetId);
        }

        AssetItem initial = items.stream()
                .filter(item -> "initial".equals(item.getItemType()))
                .findFirst()
                .orElse(items.isEmpty() ? null : items.get(0));
        boolean variant = !"initial".equalsIgnoreCase(StrUtil.blankToDefault(
                target.getItemType(), "variant"));
        boolean hasReference = variant && initial != null
                && StrUtil.isNotBlank(initial.getImageUrl());
        boolean environmentVariant = hasReference
                && "prop".equalsIgnoreCase(asset.getType())
                && isEnvironmentVariant(target);

        String prompt = buildPrompt(project, asset, target, hasReference, environmentVariant);
        JSONObject generateParams = JSONUtil.createObj().set("prompt", prompt);
        if (hasReference) {
            // Use a plain Java list here. Hutool's nested JSONArray wrapper is
            // not read back consistently by the tool adapter.
            generateParams.set("imageUrls", List.of(initial.getImageUrl()));
        }

        JSONObject generated = JSONUtil.parseObj(generateImageTool.execute(
                generateParams.toString(), context));
        if (!"success".equalsIgnoreCase(generated.getStr("status"))) {
            return generated.toString();
        }
        String imageUrl = generated.getStr("imageUrl");
        if (StrUtil.isBlank(imageUrl)) {
            return error("生图成功但没有返回图片地址");
        }

        JSONObject updateParams = JSONUtil.createObj()
                .set("assetId", assetId)
                .set("itemId", itemId)
                .set("imageUrl", imageUrl)
                .set("aiPrompt", prompt);
        JSONObject updated = JSONUtil.parseObj(updateAssetImageTool.execute(
                updateParams.toString(), context));
        if (updated.getStr("message") != null && updated.getStr("itemId") != null) {
            return JSONUtil.createObj()
                    .set("status", "success")
                    .set("assetId", assetId)
                    .set("itemId", itemId)
                    .set("imageUrl", imageUrl)
                    .set("message", "子资产图片已生成并保存")
                    .toString();
        }
        return updated.toString();
    }

    private String buildPrompt(Project project, Asset asset, AssetItem target,
                               boolean hasReference, boolean environmentVariant) {
        String style = "2.5D国漫半写实漫剧风格，清晰线稿，细腻厚涂，自然体积光，真实材质但明显为插画动画质感";
        String assetDescription = StrUtil.blankToDefault(asset.getDescription(), "古代农村生活道具");
        if (environmentVariant) {
            return "使用图片1作为目标道具的唯一主体与形制参考，保留原图中的道具种类、数量、结构、主要颜色和材质。"
                    + style + "。一个单一连续的古代农村室内场景概念图：粗糙土墙、旧木桌或木窗，暖黄色油灯光线，"
                    + "把参考图中的道具自然摆放在同一个室内空间，保留磨损、烟熏和生活使用痕迹。"
                    + "主资产设定：" + assetDescription + "。"
                    + "不是白底产品图，不是物品目录，不要孤立摆拍，不要复制，不要重复排列，不要拼图，不要分屏；"
                    + "每种目标道具只出现一次。无人物、无人脸、无手、无现代物品、无字幕、无文字、无水印、无Logo。";
        }
        if (hasReference) {
            String subjectType = "character".equalsIgnoreCase(asset.getType()) ? "角色"
                    : "scene".equalsIgnoreCase(asset.getType()) ? "场景" : "资产主体";
            String variantDescription = extractAppearanceDescription(target);
            String sceneConstraint = "character".equalsIgnoreCase(asset.getType())
                    ? "必须出现可见的古代农村环境背景，角色只出现一个、只有一个姿态，不能是白底角色展示板。"
                    : "必须是一个完整连续的环境画面，不得使用白底目录式构图。";
            return "使用图片1作为" + subjectType + "外观与构图参考，保持主体身份、主要轮廓、比例和画风统一。"
                    + style + "。生成一张单一连续的剧情画面，不是角色三视图，不是多姿态展示，不是白底设定板，"
                    + "不要把同一主体复制成多个，不要拼图、分屏或产品目录式排版。"
                    + "主资产设定：" + assetDescription + "。"
                    + "子资产场景与外观要求：" + variantDescription + "。"
                    + sceneConstraint
                    + "画面只保留一个主要主体和一个连贯场景，优先表现子资产要求中的动作、环境、道具和情绪。"
                    + "不要复制、重复、拼图、分屏、海报标题、标签或任何可读文字；无字幕、无水印、无Logo、无现代物品。";
        }
        return style + "，一个道具设定图，纯白背景，画面只呈现对应的古代农村生活道具，"
                + "材质、结构、颜色和磨损细节清晰可见。主资产设定：" + assetDescription + "。"
                + "无人物、无人脸、无手、无人体、无动物、无房屋、无道路、无院落、无风景、无拼图、无分屏、无文字。";
    }

    private boolean isEnvironmentVariant(AssetItem item) {
        String text = (StrUtil.blankToDefault(item.getName(), "") + " "
                + StrUtil.blankToDefault(item.getProperties(), "")).toLowerCase();
        return text.contains("屋内") || text.contains("屋外") || text.contains("院落")
                || text.contains("桌面") || text.contains("夜间") || text.contains("仓房")
                || text.contains("室内") || text.contains("室外");
    }

    private String extractAppearanceDescription(AssetItem item) {
        String raw = StrUtil.blankToDefault(item.getProperties(), "").trim();
        if (StrUtil.isBlank(raw)) {
            return StrUtil.blankToDefault(item.getName(), "当前子资产");
        }
        try {
            JSONObject properties = JSONUtil.parseObj(raw);
            String appearance = properties.getStr("appearanceDescription");
            if (StrUtil.isNotBlank(appearance)) {
                return appearance.trim();
            }
        } catch (Exception ignored) {
            // Some older rows store plain text instead of JSON.
        }
        return raw;
    }

    private Long extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String error(String message) {
        return JSONUtil.createObj().set("status", "error").set("message", message).toString();
    }
}

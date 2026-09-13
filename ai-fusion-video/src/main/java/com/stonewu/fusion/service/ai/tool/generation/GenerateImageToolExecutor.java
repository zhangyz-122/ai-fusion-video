package com.stonewu.fusion.service.ai.tool.generation;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.generation.ImageItem;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.ToolExecutor;
import com.stonewu.fusion.service.generation.GenerationModelCapabilityService;
import com.stonewu.fusion.service.generation.image.ImageGenerationService;
import com.stonewu.fusion.service.generation.image.consumer.ImageGenerationConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 生图工具（generate_image）
 * <p>
 * 职责：解析参数 → 构建 ImageTask → 提交到队列并同步等待结果。
 * <p>
 * 排队、并发控制、策略路由等全部由 {@link ImageGenerationConsumer} 统一处理，
 * 本工具通过 {@link ImageGenerationConsumer#submitAndWait} 复用其完整流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateImageToolExecutor implements ToolExecutor {

    /** 模型类型常量：图片生成 */
    private static final int MODEL_TYPE_IMAGE = 2;

    /** 有参考图时优先使用的本地图生图模型。 */
    private static final String REFERENCE_IMAGE_MODEL_CODE =
            "minimax_h3_t8_donghua_edit_model";

    /** 同步等待超时时间（30 分钟） */
    private static final long WAIT_TIMEOUT_MS = 30 * 60 * 1000L;

    private final AiModelService aiModelService;
    private final ImageGenerationService imageGenerationService;
    private final ImageGenerationConsumer imageGenerationConsumer;
    private final GenerationModelCapabilityService generationModelCapabilityService;

    @Override
    public String getToolName() {
        return "generate_image";
    }

    @Override
    public String getDisplayName() {
        return "AI 生成图片";
    }

    @Override
    public String getToolDescription() {
        return """
                生成AI图片。此工具仅负责生图，不会自动保存到资产库。

                适用场景：
                1. 为角色生成立绘：根据角色的外貌、性格描述生成设定图
                2. 为分镜生成画面：根据分镜的场景、内容描述生成画面图
                3. 生成场景图、道具图等创意素材

                重要提示：
                - 生成完成后，如需将图片保存到角色/场景/道具等资产中，请使用 update_asset_image 工具
                - 提示词要详细具体，包含画面的主体、风格、视角等信息
                - 可以使用中文提示词，系统会自动处理
                - 如果你打算传 imageUrls，或不确定当前默认模型是否支持参考图，请先调用 get_generation_model_capabilities
                
                %s
                """.formatted(describeCurrentModelCapability());
    }

    @Override
    public String getParametersSchema() {
            AiModel model = resolvePreferredModelOrNull();
            GenerationModelCapabilityService.ImageModelCapability capability = model != null
                ? generationModelCapabilityService.resolveImageCapability(model)
                : null;
            String imageUrlDescription = resolveReferenceImageModel() != null
                ? "参考图片 URL 列表（传入后自动切换到专用图生图模型，文生图时不传）"
                : capability != null && !capability.supportsReferenceImages()
                    ? "当前默认模型不支持参考图，请不要传该字段"
                    : "参考图片 URL 列表（用于图生图，文生图时不传）";

            return JSONUtil.createObj()
                .set("type", "object")
                .set("properties", JSONUtil.createObj()
                    .set("prompt", JSONUtil.createObj()
                        .set("type", "string")
                        .set("description", "图片生成提示词（英文效果更佳）"))
                    .set("negativePrompt", JSONUtil.createObj()
                        .set("type", "string")
                        .set("description", "反向提示词，描述不希望出现的内容"))
                    .set("width", JSONUtil.createObj()
                        .set("type", "number")
                        .set("description", "图片宽度（默认使用模型配置中的默认宽度）"))
                    .set("height", JSONUtil.createObj()
                        .set("type", "number")
                        .set("description", "图片高度（默认使用模型配置中的默认高度）"))
                    .set("style", JSONUtil.createObj()
                        .set("type", "string")
                        .set("description", "风格（如 realistic, anime, watercolor 等）"))
                    .set("imageUrls", JSONUtil.createObj()
                        .set("type", "array")
                        .set("items", JSONUtil.createObj().set("type", "string"))
                        .set("description", imageUrlDescription)))
                .set("required", JSONUtil.parseArray("[\"prompt\"]"))
                .toString();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String execute(String toolInput, ToolExecutionContext context) {
        try {
            JSONObject params = JSONUtil.parseObj(toolInput);
            String prompt = params.getStr("prompt");
            if (StrUtil.isBlank(prompt)) {
                return errorResult("缺少 prompt");
            }
            int width = params.getInt("width", 0);
            int height = params.getInt("height", 0);

            // 参考图片（图生图）。先解析模型能力，再限制输入数量，避免首尾帧
            // Agent 把镜头关联的多张资产图原样传给只支持单图的本地图生图模型。
            List<String> imageUrls = List.of();
            if (params.containsKey("imageUrls")) {
                List<String> requestedImages = params.getJSONArray("imageUrls").toList(String.class);
                imageUrls = requestedImages.stream()
                        .filter(StrUtil::isNotBlank)
                        .toList();
            }
            AiModel model = resolvePreferredModel(!imageUrls.isEmpty());
            GenerationModelCapabilityService.ImageModelCapability capability =
                    generationModelCapabilityService.resolveImageCapability(model);
            if (!imageUrls.isEmpty() && !capability.supportsReferenceImages()) {
                log.warn("[generate_image] 模型不支持参考图，已忽略 {} 张参考图: modelCode={}",
                        imageUrls.size(), model.getCode());
                imageUrls = List.of();
                model = resolvePreferredModel(false);
            } else if (capability.maxReferenceImages() != null
                    && imageUrls.size() > capability.maxReferenceImages()) {
                int allowed = Math.max(capability.maxReferenceImages(), 0);
                log.warn("[generate_image] 参考图超出模型上限，自动保留前 {} 张（原 {} 张）: modelCode={}",
                        allowed, imageUrls.size(), model.getCode());
                imageUrls = imageUrls.subList(0, allowed);
            }
            String refImageUrls = imageUrls.isEmpty() ? null : JSONUtil.toJsonStr(imageUrls);

            // 参考图请求统一经过一次冲突清洗，避免子 Agent 把初始道具图的
            // 白底产品图约束拼进屋内/屋外环境变体。
            prompt = normalizeContextVariantPrompt(prompt, refImageUrls != null);

            // 构建生图任务
            ImageTask task = ImageTask.builder()
                    .prompt(prompt)
                    .width(width > 0 ? width : null)
                    .height(height > 0 ? height : null)
                    .refImageUrls(refImageUrls)
                    .modelId(model.getId())
                    .count(1)
                    .userId(context.getUserId())
                    .build();

                generationModelCapabilityService.validateImageTask(model, task);

                log.info("[generate_image] 提交生图任务: prompt={}, size={}x{}, modelId={}, modelCode={}, 参考图: {}",
                    prompt, width, height, model.getId(), model.getCode(), refImageUrls != null ? "有" : "无");

            // 提交到队列并同步等待结果
            ImageTask completed = imageGenerationConsumer.submitAndWait(task, WAIT_TIMEOUT_MS);

            // 从完成的任务中获取生成的图片 URL
            List<ImageItem> items = imageGenerationService.listItems(completed.getId());
            String imageUrl = items.stream()
                    .filter(item -> StrUtil.isNotBlank(item.getImageUrl()))
                    .map(ImageItem::getImageUrl)
                    .findFirst()
                    .orElse(null);

            if (imageUrl == null) {
                return errorResult("生成完成但未获取到图片 URL");
            }

            log.info("[generate_image] 生成成功: url={}", imageUrl);

            return JSONUtil.createObj()
                    .set("status", "success")
                    .set("imageUrl", imageUrl)
                    .set("prompt", prompt)
                    .toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("生成任务被中断");
        } catch (Exception e) {
            log.error("[generate_image] 生成图片失败", e);
            return errorResult("生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认图片生成模型的 ID
     */
    private AiModel resolvePreferredModel(boolean hasReferenceImage) {
        AiModel defaultModel = aiModelService.getDefaultByType(MODEL_TYPE_IMAGE);

        // 融光的通用图片工具只有一个模型选择入口。为了让初始资产继续走
        // 文生图、让变体资产真正走图生图，这里按是否有参考图做一次明确路由。
        // 两个模型应当挂在同一个 ComfyUI API 配置下。
        if (hasReferenceImage) {
            AiModel referenceModel = resolveReferenceImageModel();
            if (referenceModel != null) return referenceModel;
        }

        if (defaultModel != null) {
            return defaultModel;
        }
        List<AiModel> imageModels = aiModelService.getListByType(MODEL_TYPE_IMAGE);
        if (!imageModels.isEmpty()) {
            return imageModels.get(0);
        }
        throw new IllegalStateException("未配置可用的图片生成模型");
    }

    private AiModel resolvePreferredModelOrNull() {
        try {
            return resolvePreferredModel(false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String describeCurrentModelCapability() {
        AiModel defaultModel = resolvePreferredModelOrNull();
        AiModel referenceModel = resolveReferenceImageModel();
        if (referenceModel != null && defaultModel != null
                && !referenceModel.getId().equals(defaultModel.getId())) {
            return "无参考图时使用默认图片模型 " + defaultModel.getName()
                    + "；传入 imageUrls 时自动切换到图生图模型 "
                    + referenceModel.getName() + "，最多支持 1 张参考图。";
        }
        return generationModelCapabilityService.describeImageCapability(defaultModel);
    }

    private AiModel resolveReferenceImageModel() {
        AiModel defaultModel = aiModelService.getDefaultByType(MODEL_TYPE_IMAGE);
        if (defaultModel == null || defaultModel.getApiConfigId() == null) {
            return null;
        }
        AiModel referenceModel = aiModelService.getByCodeAndApiConfig(
                REFERENCE_IMAGE_MODEL_CODE, defaultModel.getApiConfigId());
        if (referenceModel == null
                || !generationModelCapabilityService.resolveImageCapability(referenceModel)
                .supportsReferenceImages()) {
            return null;
        }
        return referenceModel;
    }

    private String errorResult(String message) {
        return JSONUtil.createObj().set("status", "error").set("message", message).toString();
    }

    /**
     * 环境变体不能同时携带初始道具的白底隔离规则。子 Agent 偶尔会把
     * initial 模板的尾部约束拼进屋内/屋外变体，这里做一次确定性的冲突清洗，
     * 避免已经明确要求室内场景却仍生成白底产品目录图。
     */
    private String normalizeContextVariantPrompt(String prompt, boolean hasReferenceImage) {
        boolean environmentPrompt = prompt.contains("环境变体图生图")
                || prompt.contains("单一连续室内场景")
                || prompt.contains("单一连续的古代农村室内场景")
                || prompt.contains("屋内")
                || prompt.contains("屋外")
                || prompt.contains("院落")
                || prompt.contains("桌面")
                || prompt.contains("室内场景")
                || prompt.contains("室外场景");
        if (!environmentPrompt) {
            return prompt;
        }
        if (!hasReferenceImage && !prompt.contains("不是白底产品图")
                && !prompt.contains("屋内")
                && !prompt.contains("屋外")
                && !prompt.contains("院落")
                && !prompt.contains("桌面")) {
            return prompt;
        }

        String normalized = prompt
                .replace("产品目录式纯白背景", "单一连续环境场景")
                .replace("纯白背景", "自然环境背景")
                .replace("完全干净、无任何阴影和杂质的纯无瑕白色背景", "真实室内环境背景")
                .replace("纯 solid white background", "")
                .replace("isolated on white background", "")
                .replace("no shadows", "")
                .replace("no gradient", "")
                .replace("画面只有目标道具本体", "画面呈现目标道具及其所在环境")
                .replace("无房屋、无道路、无院落、无风景、", "")
                .replace("无房屋、无道路、无院落、无风景", "")
                .replace("无道路、无院落、无风景、", "")
                .replace("无道路、无院落、无风景", "");

        if (!normalized.contains("每件器物只出现一次")) {
            normalized = normalized + " 单一连续室内场景，必须看到土墙、木桌或木窗等室内结构；每件目标器物只出现一次，禁止复制、重复排列、拼贴、分屏和产品目录式白底构图。";
        }
        if (hasReferenceImage) {
            normalized = normalized
                    .replace("纯白", "")
                    .replace("白底", "")
                    + " 这是环境变体图生图，不是白底产品设定图；参考图中的每种道具在室内场景中只出现一次，禁止白色背景、孤立摆拍、重复复制和多格拼贴。";
        }
        return normalized;
    }
}

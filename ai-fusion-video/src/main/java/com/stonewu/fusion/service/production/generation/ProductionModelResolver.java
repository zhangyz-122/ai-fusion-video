package com.stonewu.fusion.service.production.generation;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.service.ai.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * PR-010: WorkflowProfile 中的模型 code → afv_ai_model 主键。
 * 现有 VideoTask/队列按 AiModel.id 调度，Production 不允许另建一套模型引用。
 */
@Service
@RequiredArgsConstructor
public class ProductionModelResolver {

    private static final int MODEL_TYPE_VIDEO = 3;

    private final AiModelService aiModelService;

    @Cacheable(value = "productionModel", key = "#modelCode", unless = "#result == null")
    public Long resolveVideoModelId(String modelCode) {
        if (StrUtil.isBlank(modelCode)) {
            throw new IllegalStateException("WorkflowProfile 未配置 defaultModelId");
        }
        return aiModelService.getListByType(MODEL_TYPE_VIDEO).stream()
                .filter(model -> modelCode.equals(model.getCode()))
                .map(AiModel::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到已启用的视频模型 " + modelCode + "，请先在模型设置中添加并启用"));
    }
}

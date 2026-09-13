package com.stonewu.fusion.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.controller.ai.vo.CapabilityCatalogItemVO;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.mapper.ai.ComfyUiWorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共能力目录：面向所有登录用户聚合"已启用模型"与"待接入工作流"，
 * 输出字段最小化，不包含 API 配置、绑定、哈希等管理信息。
 */
@Service
@RequiredArgsConstructor
public class CapabilityCatalogService {

    private final AiModelService aiModelService;
    private final ComfyUiWorkflowMapper workflowMapper;

    public List<CapabilityCatalogItemVO> getCatalog(Integer modelType) {
        List<CapabilityCatalogItemVO> catalog = new ArrayList<>();
        for (AiModel model : aiModelService.getListByType(modelType)) {
            catalog.add(CapabilityCatalogItemVO.builder()
                    .id(model.getId())
                    .name(model.getName())
                    .description(model.getDescription())
                    .source(CapabilityCatalogItemVO.SOURCE_MODEL)
                    .enabled(true)
                    .defaultCapability(Boolean.TRUE.equals(model.getDefaultModel()))
                    .build());
        }
        LambdaQueryWrapper<ComfyUiWorkflow> query = new LambdaQueryWrapper<ComfyUiWorkflow>()
                .eq(ComfyUiWorkflow::getModelType, modelType)
                .orderByAsc(ComfyUiWorkflow::getId);
        for (ComfyUiWorkflow workflow : workflowMapper.selectList(query)) {
            boolean published = workflow.getActiveVersionId() != null;
            boolean enabled = published && Integer.valueOf(1).equals(workflow.getStatus());
            if (enabled) {
                // 已发布且启用的工作流由其对应模型条目代表，不在目录中重复展示
                continue;
            }
            catalog.add(CapabilityCatalogItemVO.builder()
                    .id(workflow.getId())
                    .name(workflow.getName())
                    .description(workflow.getDescription())
                    .source(CapabilityCatalogItemVO.SOURCE_WORKFLOW)
                    .enabled(false)
                    .defaultCapability(false)
                    .pendingReason(published ? "已禁用" : "未发布 · 待接入")
                    .build());
        }
        return catalog;
    }
}

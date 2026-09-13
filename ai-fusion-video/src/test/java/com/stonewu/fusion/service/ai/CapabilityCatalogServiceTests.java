package com.stonewu.fusion.service.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.controller.ai.vo.CapabilityCatalogItemVO;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.mapper.ai.ComfyUiWorkflowMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityCatalogServiceTests {

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ComfyUiWorkflow.class);
    }

    @Mock
    private AiModelService aiModelService;
    @Mock
    private ComfyUiWorkflowMapper workflowMapper;

    private CapabilityCatalogService service() {
        return new CapabilityCatalogService(aiModelService, workflowMapper);
    }

    @Test
    void catalogListsEnabledModelsAndPendingWorkflowsWithoutAdminFields() {
        AiModel enabled = new AiModel();
        enabled.setId(14L);
        enabled.setName("MiniMax H3 T8 文戏生图");
        enabled.setDescription("生成单张动画关键帧");
        enabled.setDefaultModel(true);
        when(aiModelService.getListByType(2)).thenReturn(List.of(enabled));

        ComfyUiWorkflow unpublished = new ComfyUiWorkflow();
        unpublished.setId(101L);
        unpublished.setName("Flux2 Klein 图像编辑");
        unpublished.setDescription("Klein 编辑工作流");
        unpublished.setApiConfigId(77L);
        unpublished.setActiveVersionId(null);
        unpublished.setStatus(1);

        ComfyUiWorkflow disabledPublished = new ComfyUiWorkflow();
        disabledPublished.setId(102L);
        disabledPublished.setName("旧版工作流");
        disabledPublished.setActiveVersionId(9L);
        disabledPublished.setStatus(0);

        ComfyUiWorkflow enabledPublished = new ComfyUiWorkflow();
        enabledPublished.setId(103L);
        enabledPublished.setName("已启用工作流");
        enabledPublished.setActiveVersionId(10L);
        enabledPublished.setStatus(1);

        when(workflowMapper.selectList(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(unpublished, disabledPublished, enabledPublished));

        List<CapabilityCatalogItemVO> catalog = service().getCatalog(2);

        CapabilityCatalogItemVO modelEntry = catalog.get(0);
        assertThat(modelEntry.getSource()).isEqualTo("MODEL");
        assertThat(modelEntry.getId()).isEqualTo(14L);
        assertThat(modelEntry.getEnabled()).isTrue();
        assertThat(modelEntry.getDefaultCapability()).isTrue();

        CapabilityCatalogItemVO pending = catalog.get(1);
        assertThat(pending.getSource()).isEqualTo("WORKFLOW");
        assertThat(pending.getName()).isEqualTo("Flux2 Klein 图像编辑");
        assertThat(pending.getPendingReason()).isEqualTo("未发布 · 待接入");
        assertThat(pending.getEnabled()).isFalse();

        CapabilityCatalogItemVO disabled = catalog.get(2);
        assertThat(disabled.getPendingReason()).isEqualTo("已禁用");

        // 已发布且启用的工作流由模型条目代表，不重复展示
        assertThat(catalog).hasSize(3);
        // 脱敏：目录条目不携带 apiConfigId 等管理信息
        assertThat(catalog).allSatisfy(item -> {
            assertThat(item.getClass().getDeclaredFields())
                    .noneMatch(field -> field.getName().toLowerCase().contains("apiconfig")
                            || field.getName().toLowerCase().contains("binding"));
        });
    }
}

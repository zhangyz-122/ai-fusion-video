package com.stonewu.fusion.service.ai.comfyui;

import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.WorkflowProfile;
import com.stonewu.fusion.mapper.ai.WorkflowProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowProfileServiceTests {

    @Mock
    private WorkflowProfileMapper profileMapper;

    @Mock
    private ComfyUiWorkflowService workflowService;

    private WorkflowProfileService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowProfileService(profileMapper, workflowService);
    }

    @Test
    void createProfileStoresMetadataAndReferencesExistingWorkflow() {
        when(workflowService.requireWorkflow(21L)).thenReturn(
                ComfyUiWorkflow.builder().id(21L).modelType(3).build());
        when(profileMapper.exists(any())).thenReturn(false);
        doAnswer(invocation -> {
            WorkflowProfile profile = invocation.getArgument(0);
            profile.setId(31L);
            return 1;
        }).when(profileMapper).insert(any(WorkflowProfile.class));

        Long id = service.createProfile(
                " wan i2v standard ",
                "Wan I2V",
                21L,
                "i2v",
                "{\"firstFrame\":true}",
                "{\"prompt\":\"required\"}",
                "{\"video\":\"one\"}",
                "{\"nodes\":[\"WanVideo\"]}",
                "{\"vramGb\":16}",
                null);

        assertThat(id).isEqualTo(31L);
        ArgumentCaptor<WorkflowProfile> captor = ArgumentCaptor.forClass(WorkflowProfile.class);
        verify(profileMapper).insert(captor.capture());
        WorkflowProfile profile = captor.getValue();
        assertThat(profile.getCode()).isEqualTo("WAN_I2V_STANDARD");
        assertThat(profile.getPurpose()).isEqualTo("I2V");
        assertThat(profile.getWorkflowId()).isEqualTo(21L);
        assertThat(profile.getStatus()).isEqualTo(1);
        assertThat(profile.getInputContractJson()).contains("prompt");
    }

    @Test
    void createProfileRejectsDuplicateStableCode() {
        when(workflowService.requireWorkflow(21L)).thenReturn(
                ComfyUiWorkflow.builder().id(21L).build());
        when(profileMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(
                "WAN_I2V_STANDARD", "Wan I2V", 21L, "I2V",
                null, null, null, null, null, 1))
                .hasMessage("WorkflowProfile code 已存在");
    }

    @Test
    void resolveVideoExecutionPinsPublishedVersionForMatchingModel() {
        WorkflowProfile profile = WorkflowProfile.builder()
                .id(31L)
                .workflowId(21L)
                .status(1)
                .build();
        ComfyUiWorkflow workflow = ComfyUiWorkflow.builder()
                .id(21L)
                .activeVersionId(41L)
                .status(1)
                .build();
        ComfyUiWorkflowVersion version = ComfyUiWorkflowVersion.builder()
                .id(41L)
                .workflowId(21L)
                .published(true)
                .build();
        when(profileMapper.selectById(31L)).thenReturn(profile);
        when(workflowService.requireWorkflow(21L)).thenReturn(workflow);
        when(workflowService.requireVersion(41L)).thenReturn(version);

        WorkflowProfileService.VideoExecutionResolution resolution =
                service.resolveVideoExecution(31L, AiModel.builder()
                        .id(51L)
                        .modelType(3)
                        .status(1)
                        .comfyuiWorkflowId(21L)
                        .build());

        assertThat(resolution.profile()).isSameAs(profile);
        assertThat(resolution.workflow()).isSameAs(workflow);
        assertThat(resolution.version()).isSameAs(version);
    }

    @Test
    void resolveVideoExecutionRejectsWorkflowMismatch() {
        when(profileMapper.selectById(31L)).thenReturn(WorkflowProfile.builder()
                .id(31L).workflowId(21L).status(1).build());
        when(workflowService.requireWorkflow(21L)).thenReturn(ComfyUiWorkflow.builder()
                .id(21L).activeVersionId(41L).status(1).build());

        assertThatThrownBy(() -> service.resolveVideoExecution(31L, AiModel.builder()
                .modelType(3).status(1).comfyuiWorkflowId(99L).build()))
                .hasMessage("WorkflowProfile 与视频模型绑定的工作流不匹配");
    }
}

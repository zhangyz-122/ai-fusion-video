package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiNativeClient;
import com.stonewu.fusion.service.storage.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComfyUiGenerationExecutorTests {

    @Mock
    private ApiConfigService apiConfigService;
    @Mock
    private ComfyUiWorkflowService workflowService;
    @Mock
    private ComfyUiWorkflowDocumentService documentService;
    @Mock
    private ComfyUiWorkflowRenderer renderer;
    @Mock
    private ComfyUiInputResourceService inputResourceService;
    @Mock
    private ComfyUiOutputResolver outputResolver;
    @Mock
    private ComfyUiNativeClient nativeClient;
    @Mock
    private MediaStorageService mediaStorageService;
    @Mock
    private ComfyUiWorkflowVersion version;

    private ComfyUiGenerationExecutor executor;
    private ComfyUiExecutionContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        executor = new ComfyUiGenerationExecutor(apiConfigService, workflowService,
                documentService, renderer, inputResourceService, outputResolver,
                nativeClient, mediaStorageService);
        AiModel model = AiModel.builder().id(6L).modelType(3).build();
        context = new ComfyUiExecutionContext(model,
                ApiConfig.builder().id(7L).build(), null, version);
    }

    @Test
    void prepareUploadsBoundAudioAndFillsWorkflowValue() {
        when(version.getApiWorkflowJson()).thenReturn("{}");
        when(version.getInputBindingsJson()).thenReturn("{}");
        when(documentService.parseInputBindings(eq(3), anyString(), anyString()))
                .thenReturn(List.of(new ComfyUiInputBinding(
                        "referenceAudios", "12", "audio", "uploaded_audio", null)));
        when(inputResourceService.uploadAudios(any(), anyString(), eq("referenceAudios"),
                eq(List.of("https://cdn.example.com/voice.mp3"))))
                .thenReturn(List.of("ai-fusion-video/task-referenceAudios-0.mp3"));
        when(renderer.render(eq(3), eq(version), any()))
                .thenReturn(objectMapper.createObjectNode());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("referenceAudios", List.of("https://cdn.example.com/voice.mp3"));

        ComfyUiPreparedSubmission submission = executor.prepare(context, "task-key", values);

        assertThat(submission.promptId()).isNotBlank();
        // 请求侧音频引用已替换为 ComfyUI 上传返回的 input 描述
        ArgumentCaptor<Map<String, Object>> rendered = renderValuesCaptor();
        verify(renderer).render(eq(3), eq(version), rendered.capture());
        assertThat(rendered.getValue().get("referenceAudios"))
                .isEqualTo(List.of("ai-fusion-video/task-referenceAudios-0.mp3"));
    }

    @Test
    void prepareUploadsMixedImageAndAudioBindings() {
        when(version.getApiWorkflowJson()).thenReturn("{}");
        when(version.getInputBindingsJson()).thenReturn("{}");
        when(documentService.parseInputBindings(eq(3), anyString(), anyString()))
                .thenReturn(List.of(
                        new ComfyUiInputBinding(
                                "firstFrame", "9", "image", "uploaded_image", null),
                        new ComfyUiInputBinding(
                                "referenceAudios", "12", "audio", "uploaded_audio", null)));
        when(inputResourceService.uploadImages(any(), anyString(), eq("firstFrame"),
                eq(List.of("https://cdn.example.com/frame.png"))))
                .thenReturn(List.of("ai-fusion-video/task-firstFrame-0.png"));
        when(inputResourceService.uploadAudios(any(), anyString(), eq("referenceAudios"),
                eq(List.of("data:audio/wav;base64,QUJDRA=="))))
                .thenReturn(List.of("ai-fusion-video/task-referenceAudios-0.wav"));
        when(renderer.render(eq(3), eq(version), any()))
                .thenReturn(objectMapper.createObjectNode());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("firstFrame", List.of("https://cdn.example.com/frame.png"));
        values.put("referenceAudios", List.of("data:audio/wav;base64,QUJDRA=="));

        executor.prepare(context, "task-key", values);

        ArgumentCaptor<Map<String, Object>> rendered = renderValuesCaptor();
        verify(renderer).render(eq(3), eq(version), rendered.capture());
        assertThat(rendered.getValue().get("firstFrame"))
                .isEqualTo(List.of("ai-fusion-video/task-firstFrame-0.png"));
        assertThat(rendered.getValue().get("referenceAudios"))
                .isEqualTo(List.of("ai-fusion-video/task-referenceAudios-0.wav"));
    }

    @Test
    void prepareSkipsAudioUploadWhenRequestHasNoAudio() {
        when(version.getApiWorkflowJson()).thenReturn("{}");
        when(version.getInputBindingsJson()).thenReturn("{}");
        when(documentService.parseInputBindings(eq(3), anyString(), anyString()))
                .thenReturn(List.of(new ComfyUiInputBinding(
                        "referenceAudios", "12", "audio", "uploaded_audio", null)));
        when(renderer.render(eq(3), eq(version), any()))
                .thenReturn(objectMapper.createObjectNode());

        executor.prepare(context, "task-key", new LinkedHashMap<>());

        verifyNoInteractions(inputResourceService);
    }

    @Test
    void prepareRejectsBlankAudioValue() {
        when(version.getApiWorkflowJson()).thenReturn("{}");
        when(version.getInputBindingsJson()).thenReturn("{}");
        when(documentService.parseInputBindings(eq(3), anyString(), anyString()))
                .thenReturn(List.of(new ComfyUiInputBinding(
                        "referenceAudios", "12", "audio", "uploaded_audio", null)));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("referenceAudios", List.of("  "));

        assertThatThrownBy(() -> executor.prepare(context, "task-key", values))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("音频输入包含空值");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> renderValuesCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}

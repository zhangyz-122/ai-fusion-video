package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComfyUiWorkflowRendererTests {

    private ComfyUiWorkflowRenderer renderer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        renderer = new ComfyUiWorkflowRenderer(
                objectMapper,
                new ComfyUiWorkflowDocumentService(objectMapper));
    }

    @Test
    void renderWritesTypedValuesToExplicitTargets() {
        ComfyUiWorkflowVersion version = version(
                """
                        {
                          "1":{"class_type":"Text","inputs":{"text":"old"}},
                          "2":{"class_type":"Sampler","inputs":{"seed":0,"batch_size":1}},
                          "3":{"class_type":"LoadImage","inputs":{"image":"old.png"}},
                          "9":{"class_type":"SaveImage","inputs":{"images":["2",0]}}
                        }
                        """,
                """
                        {
                          "prompt":[{"nodeId":"1","inputName":"text","valueType":"string"}],
                          "seed":[{"nodeId":"2","inputName":"seed","valueType":"integer"}],
                          "count":[{"nodeId":"2","inputName":"batch_size","valueType":"integer"}],
                          "referenceImages":[{"nodeId":"3","inputName":"image","valueType":"uploaded_image","index":1}]
                        }
                        """);

        ObjectNode result = renderer.render(2, version, Map.of(
                "prompt", "new prompt",
                "seed", 42,
                "count", 3,
                "referenceImages", List.of("first.png", "second.png")));

        assertThat(result.at("/1/inputs/text").asText()).isEqualTo("new prompt");
        assertThat(result.at("/2/inputs/seed").asLong()).isEqualTo(42L);
        assertThat(result.at("/2/inputs/batch_size").asInt()).isEqualTo(3);
        assertThat(result.at("/3/inputs/image").asText()).isEqualTo("second.png");
    }

    @Test
    void renderDisconnectsUnusedIndexedReferenceBranch() {
        ComfyUiWorkflowVersion version = version(
                """
                        {
                          "3":{"class_type":"LoadImage","inputs":{"image":"old.png"}},
                          "9":{"class_type":"SaveImage","inputs":{"images":["3",0]}}
                        }
                        """,
                """
                        {"referenceImages":[{"nodeId":"3","inputName":"image","valueType":"uploaded_image","index":1}]}
                        """);

        ObjectNode result = renderer.render(
                2, version, Map.of("referenceImages", List.of("only.png")));

        assertThat(result.at("/3/inputs/image").asText()).isEqualTo("old.png");
        assertThat(result.at("/9/inputs/images").isMissingNode()).isTrue();
    }

    @Test
    void renderDerivesNumFramesFromDurationAndWorkflowFps() {
        ComfyUiWorkflowVersion version = version(
                """
                        {
                          "212":{"class_type":"WanVideoImageToVideoEncode","inputs":{"num_frames":17}},
                          "215":{"class_type":"VHS_VideoCombine","inputs":{"frame_rate":16}}
                        }
                        """,
                "{}");

        ObjectNode result = renderer.render(3, version, Map.of("duration", 5));

        assertThat(result.at("/212/inputs/num_frames").asLong()).isEqualTo(81L);
        assertThat(result.at("/215/inputs/frame_rate").asInt()).isEqualTo(16);
    }

    @Test
    void renderKeepsTemplateFramesWithoutDuration() {
        ComfyUiWorkflowVersion version = version(
                """
                        {"212":{"class_type":"WanVideoImageToVideoEncode","inputs":{"num_frames":17}}}
                        """,
                "{}");

        ObjectNode result = renderer.render(3, version, Map.of("prompt", "only text"));

        assertThat(result.at("/212/inputs/num_frames").asInt()).isEqualTo(17);
    }

    @Test
    void renderPrefersExplicitNumFramesOverDuration() {
        ComfyUiWorkflowVersion version = version(
                """
                        {"212":{"class_type":"WanVideoImageToVideoEncode","inputs":{"num_frames":17}}}
                        """,
                "{}");

        ObjectNode result = renderer.render(
                3, version, Map.of("duration", 5, "numFrames", 49));

        assertThat(result.at("/212/inputs/num_frames").asLong()).isEqualTo(49L);
    }

    @Test
    void renderIgnoresLinkedNumFramesInputs() {
        ComfyUiWorkflowVersion version = version(
                """
                        {"212":{"class_type":"WanVideoImageToVideoEncode","inputs":{"num_frames":["8",0]}}}
                        """,
                "{}");

        ObjectNode result = renderer.render(3, version, Map.of("duration", 5));

        // 链接型输入不是模板帧数，交给工作流自身逻辑，渲染器不改写
        assertThat(result.at("/212/inputs/num_frames").isArray()).isTrue();
    }

    private ComfyUiWorkflowVersion version(String apiJson, String inputBindingsJson) {
        return ComfyUiWorkflowVersion.builder()
                .apiWorkflowJson(apiJson)
                .inputBindingsJson(inputBindingsJson)
                .outputBindingsJson(
                        "[{\"nodeId\":\"9\",\"mediaType\":\"image\",\"role\":\"primary\"}]")
                .build();
    }
}

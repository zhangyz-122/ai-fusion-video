package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComfyUiWorkflowDocumentServiceTests {

    private ComfyUiWorkflowDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new ComfyUiWorkflowDocumentService(new ObjectMapper());
    }

    @Test
    void normalizeCanonicalizesApiWorkflowAndBindings() {
        ComfyUiWorkflowDefinition definition = documentService.normalize(
                2,
                null,
                """
                        {
                          "9": {"class_type":"SaveImage","inputs":{"images":["8",0]}},
                          "1": {"class_type":"CLIPTextEncode","inputs":{"text":"old"}}
                        }
                        """,
                """
                        {"prompt":[{"nodeId":"1","inputName":"text","valueType":"string"}]}
                        """,
                """
                        [{"nodeId":"9","mediaType":"image","role":"primary"}]
                        """);

        assertThat(definition.apiWorkflowJson()).startsWith("{\"1\":");
        assertThat(definition.requiredNodesJson()).isEqualTo("[\"CLIPTextEncode\",\"SaveImage\"]");
        assertThat(definition.workflowHash()).hasSize(64);
        assertThat(definition.inputBindings()).containsExactly(
                new ComfyUiInputBinding("prompt", "1", "text", "string", null));
        assertThat(definition.outputBindings()).containsExactly(
                new ComfyUiOutputBinding("9", "image", "primary"));
    }

    @Test
    void normalizeRejectsUiFormatInsteadOfGuessing() {
        assertThatThrownBy(() -> documentService.normalize(
                2, null, "{\"nodes\":[],\"links\":[]}", "{}", "[]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UI-format");
    }

    @Test
    void normalizeRejectsEmbeddedPlaintextSecret() {
        assertThatThrownBy(() -> documentService.normalize(
                2,
                null,
                """
                        {"1":{"class_type":"RemoteNode","inputs":{"api_key":"secret"}}}
                        """,
                "{}",
                "[{\"nodeId\":\"1\",\"mediaType\":\"image\",\"role\":\"primary\"}]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明文密钥");
    }

    @Test
    void normalizeRequiresPrimaryOutputMatchingModelType() {
        assertThatThrownBy(() -> documentService.normalize(
                3,
                null,
                "{\"1\":{\"class_type\":\"SaveVideo\",\"inputs\":{}}}",
                "{}",
                "[{\"nodeId\":\"1\",\"mediaType\":\"image\",\"role\":\"primary\"}]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("primary video");
    }

    @Test
    void normalizeAcceptsWorkflowAtNodeLimit() {
        ComfyUiWorkflowDefinition definition = documentService.normalize(
                2,
                null,
                apiWorkflowWithNodes(500),
                "{\"prompt\":[{\"nodeId\":\"1\",\"inputName\":\"text\",\"valueType\":\"string\"}]}",
                "[{\"nodeId\":\"500\",\"mediaType\":\"image\",\"role\":\"primary\"}]");

        assertThat(definition.workflowHash()).hasSize(64);
        assertThat(definition.requiredNodesJson()).isEqualTo("[\"CLIPTextEncode\"]");
    }

    @Test
    void normalizeRejectsWorkflowBeyondNodeLimit() {
        assertThatThrownBy(() -> documentService.normalize(
                2,
                null,
                apiWorkflowWithNodes(501),
                "{}",
                "[{\"nodeId\":\"1\",\"mediaType\":\"image\",\"role\":\"primary\"}]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流节点数量不能超过 500");
    }

    @Test
    void parseInputBindingsRejectsWorkflowBeyondNodeLimit() {
        assertThatThrownBy(() -> documentService.parseInputBindings(
                2,
                apiWorkflowWithNodes(501),
                "{\"prompt\":[{\"nodeId\":\"1\",\"inputName\":\"text\",\"valueType\":\"string\"}]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作流节点数量不能超过 500");
    }

    @Test
    void rejectsApiWorkflowBeyondNestingDepthLimit() {
        String deepWorkflow = "{\"1\":{\"class_type\":\"Deep\",\"inputs\":"
                + nestedObject(62) + "}}";

        assertThatThrownBy(() -> documentService.parseApiWorkflow(deepWorkflow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("嵌套深度不能超过 64");
    }

    @Test
    void acceptsApiWorkflowAtNestingDepthLimit() {
        String workflow = "{\"1\":{\"class_type\":\"Deep\",\"inputs\":"
                + nestedObject(61) + "}}";

        assertThat(documentService.parseApiWorkflow(workflow)).isNotEmpty();
    }

    @Test
    void rejectsDeeplyNestedInputBindings() {
        String workflow = "{\"1\":{\"class_type\":\"CLIPTextEncode\",\"inputs\":{\"text\":\"x\"}}}";
        String bindings = "{\"prompt\":[{\"nodeId\":\"1\",\"inputName\":\"text\","
                + "\"valueType\":\"string\",\"payload\":" + nestedObject(70) + "}]}";

        assertThatThrownBy(() -> documentService.parseInputBindings(2, workflow, bindings))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("输入绑定嵌套深度不能超过 64");
    }

    private String apiWorkflowWithNodes(int count) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 1; i <= count; i++) {
            if (i > 1) sb.append(',');
            sb.append('"').append(i).append("\":{\"class_type\":\"CLIPTextEncode\",\"inputs\":{\"text\":\"x\"}}");
        }
        return sb.append('}').toString();
    }

    private String nestedObject(int levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            sb.append("{\"x\":");
        }
        sb.append('1');
        for (int i = 0; i < levels; i++) {
            sb.append('}');
        }
        return sb.toString();
    }
}

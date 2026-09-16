package com.stonewu.fusion.service.ai.tool.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.config.AgentScopeV2Properties;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.script.ScriptService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadScriptSegmentToolExecutorTests {

    private static final String RAW = "甲".repeat(600) + "\n\n" + "乙".repeat(600);

    private final ScriptService scriptService = mock(ScriptService.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
    private final AgentScopeV2Properties properties = new AgentScopeV2Properties();
    private final ReadScriptSegmentToolExecutor executor = new ReadScriptSegmentToolExecutor(
            scriptService, projectService, accessGuard, properties);

    @Test
    void returnsRequestedSegmentWithOffsetsAndContinuationHint() {
        properties.getScript().setSegmentChars(1000);
        stubProjectScript(script());

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segment\":1}", context()));

        assertThat(payload.getStr("status")).isEqualTo("success");
        assertThat(payload.getInt("segment")).isEqualTo(1);
        assertThat(payload.getInt("totalSegments")).isEqualTo(2);
        assertThat(payload.getInt("totalChars")).isEqualTo(RAW.length());
        assertThat(payload.getStr("content")).isEqualTo(RAW.substring(0, 602));
        assertThat(payload.getStr("hint"))
                .contains("read_script_segment(segment=2)")
                .contains("还有 1 段");
    }

    @Test
    void lastSegmentTellsModelToStopPaging() {
        properties.getScript().setSegmentChars(1000);
        stubProjectScript(script());

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segment\":2}", context()));

        assertThat(payload.getInt("segment")).isEqualTo(2);
        assertThat(payload.getStr("content")).isEqualTo("乙".repeat(600));
        assertThat(payload.getStr("hint")).contains("最后一段");
    }

    @Test
    void acceptsSegmentIndexAlias() {
        properties.getScript().setSegmentChars(1000);
        stubProjectScript(script());

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segmentIndex\":2}", context()));

        assertThat(payload.getInt("segment")).isEqualTo(2);
    }

    @Test
    void missingSegmentParameterReturnsExplicitError() {
        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("segment");
    }

    @Test
    void outOfRangeSegmentReturnsExplicitErrorWithTotalSegments() {
        properties.getScript().setSegmentChars(1000);
        stubProjectScript(script());

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segment\":99}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message"))
                .contains("越界")
                .contains("2");
    }

    @Test
    void emptyRawContentRejectsAnySegment() {
        stubProjectScript(script(null));

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segment\":1}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("0");
    }

    @Test
    void fallsBackToScriptIdWithOwnershipGuard() {
        properties.getScript().setSegmentChars(1000);
        Script script = script();
        when(accessGuard.requireScript(5L, 7L)).thenReturn(script);

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"scriptId\":5,\"segment\":1}", context()));

        assertThat(payload.getStr("status")).isEqualTo("success");
        assertThat(payload.getLong("scriptId")).isEqualTo(5L);
    }

    @Test
    void missingIdentifierFailsFast() {
        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"segment\":1}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("projectId");
    }

    @Test
    void deniedProjectFailsFast() {
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9,\"segment\":1}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("无权访问");
    }

    @Test
    void schemaDocumentsSegmentAndAlias() {
        String schema = executor.getParametersSchema();
        assertThat(schema).contains("segment").contains("segmentIndex").contains("projectId");
    }

    private Script script() {
        return script(RAW);
    }

    private Script script(String rawContent) {
        return Script.builder()
                .id(5L)
                .projectId(9L)
                .title("测试剧本")
                .rawContent(rawContent)
                .build();
    }

    private void stubProjectScript(Script script) {
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);
        when(scriptService.getByProjectId(9L)).thenReturn(script);
    }

    private ToolExecutionContext context() {
        return ToolExecutionContext.builder().userId(7L).build();
    }
}

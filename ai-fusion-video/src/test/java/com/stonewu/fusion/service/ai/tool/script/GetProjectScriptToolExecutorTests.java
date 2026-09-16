package com.stonewu.fusion.service.ai.tool.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.config.AgentScopeV2Properties;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.script.ScriptService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetProjectScriptToolExecutorTests {

    private final ScriptService scriptService = mock(ScriptService.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final AgentScopeV2Properties properties = new AgentScopeV2Properties();
    private final GetProjectScriptToolExecutor executor = new GetProjectScriptToolExecutor(
            scriptService, projectService, properties);

    @Test
    void longScriptReturnsFirstSegmentWithPaginationMetaInsteadOfFullContent() {
        properties.getScript().setSegmentChars(1000);
        String raw = "甲".repeat(2500);
        stubAccess(script(raw));

        String result = executor.execute("{\"projectId\":9}", context());
        JSONObject payload = JSONUtil.parseObj(result);

        assertThat(payload.getInt("totalChars")).isEqualTo(2500);
        assertThat(payload.getInt("totalSegments")).isEqualTo(3);
        assertThat(payload.getInt("segment")).isEqualTo(1);
        assertThat(payload.getStr("content")).hasSize(1000).isEqualTo(raw.substring(0, 1000));
        assertThat(payload.getStr("hint"))
                .contains("read_script_segment")
                .contains("3")
                .contains("边读边落库");
        // 关键回归断言：整本原文绝不能再作为 rawContent 键出现在工具结果里
        assertThat(payload.containsKey("rawContent")).isFalse();
    }

    @Test
    void shortScriptReturnsSingleSegmentWithoutPagingHint() {
        String raw = "第一集\n短剧本";
        stubAccess(script(raw));

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getInt("totalSegments")).isEqualTo(1);
        assertThat(payload.getInt("segment")).isEqualTo(1);
        assertThat(payload.getStr("content")).isEqualTo(raw);
        assertThat(payload.containsKey("hint")).isFalse();
        assertThat(payload.containsKey("rawContent")).isFalse();
    }

    @Test
    void emptyRawContentReportsZeroSegments() {
        stubAccess(script(null));

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getInt("totalChars")).isZero();
        assertThat(payload.getInt("totalSegments")).isZero();
        assertThat(payload.getInt("segment")).isZero();
        assertThat(payload.getStr("content")).isEmpty();
        assertThat(payload.getStr("message")).contains("原文为空");
    }

    @Test
    void keepsMetadataAndEpisodeOverviewAlongsideSegment() {
        Script script = script("第一集内容");
        script.setTitle("测试剧本");
        script.setParsingStatus(2);
        script.setTotalEpisodes(1);
        when(scriptService.listEpisodes(script.getId())).thenReturn(java.util.List.of());
        stubAccess(script);

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getLong("scriptId")).isEqualTo(5L);
        assertThat(payload.getStr("title")).isEqualTo("测试剧本");
        assertThat(payload.getInt("parsingStatus")).isEqualTo(2);
        assertThat(payload.getInt("totalEpisodes")).isEqualTo(1);
        assertThat(payload.getJSONArray("episodes")).isEmpty();
    }

    @Test
    void missingProjectIdFailsFast() {
        JSONObject payload = JSONUtil.parseObj(executor.execute("{}", context()));
        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("projectId");
    }

    @Test
    void deniedProjectFailsFast() {
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getStr("status")).isEqualTo("error");
        assertThat(payload.getStr("message")).contains("无权访问");
    }

    @Test
    void missingScriptReportsEmptyStatus() {
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);
        when(scriptService.getByProjectId(9L)).thenReturn(null);

        JSONObject payload = JSONUtil.parseObj(
                executor.execute("{\"projectId\":9}", context()));

        assertThat(payload.getStr("status")).isEqualTo("empty");
    }

    private Script script(String rawContent) {
        return Script.builder()
                .id(5L)
                .projectId(9L)
                .title("测试剧本")
                .rawContent(rawContent)
                .build();
    }

    private void stubAccess(Script script) {
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);
        when(scriptService.getByProjectId(9L)).thenReturn(script);
        when(scriptService.listEpisodes(script.getId())).thenReturn(java.util.List.of());
    }

    private ToolExecutionContext context() {
        return ToolExecutionContext.builder().userId(7L).build();
    }
}

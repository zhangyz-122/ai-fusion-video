package com.stonewu.fusion.service.ai.tool;

import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.project.ProjectQueryToolExecutor;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import com.stonewu.fusion.service.system.SystemConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectQueryToolExecutorTests {

    @Test
    void resolvesStoryboardIdWhenModelUsesItForProjectQuery() {
        ProjectService projectService = mock(ProjectService.class);
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        StoryboardService storyboardService = mock(StoryboardService.class);
        ProjectQueryToolExecutor executor = new ProjectQueryToolExecutor(
                projectService, systemConfigService, storyboardService);

        when(storyboardService.getById(81L))
                .thenReturn(Storyboard.builder().id(81L).projectId(1L).build());
        Project project = Project.builder().id(1L).name("测试项目").build();
        when(projectService.getById(1L)).thenReturn(project);
        when(projectService.canAccessProject(project, 7L)).thenReturn(true);

        String result = executor.execute(
                "{\"storyboardId\":81}",
                ToolExecutionContext.builder().userId(7L).build());

        assertThat(JSONUtil.parseObj(result).getLong("projectId")).isEqualTo(1L);
        assertThat(JSONUtil.parseObj(result).getStr("name")).isEqualTo("测试项目");
    }

    @Test
    void schemaKeepsProjectIdAsPreferredParameterAndAcceptsLegacyAlias() {
        ProjectQueryToolExecutor executor = new ProjectQueryToolExecutor(
                mock(ProjectService.class), mock(SystemConfigService.class), mock(StoryboardService.class));

        String schema = executor.getParametersSchema();

        assertThat(schema).contains("projectId").contains("storyboardId");
        assertThat(schema).doesNotContain("\"required\"");
    }
}

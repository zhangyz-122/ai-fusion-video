package com.stonewu.fusion.service.ai.tool;

import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.storyboard.UpdateStoryboardItemToolExecutor;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StoryboardOwnershipGuardTests {

    @Test
    void agentCannotWriteProductionOwnedSelectedTake() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        ProjectService projectService = mock(ProjectService.class);
        UpdateStoryboardItemToolExecutor executor =
                new UpdateStoryboardItemToolExecutor(storyboardService, projectService);

        String result = executor.execute(
                "{\"storyboardItemId\":51,\"selectedTakeId\":701}",
                ToolExecutionContext.builder().userId(7L).build());

        assertThat(JSONUtil.parseObj(result).getStr("status")).isEqualTo("error");
        assertThat(JSONUtil.parseObj(result).getStr("message"))
                .isEqualTo("selectedTakeId 仅允许由 Production 选择接口更新");
        verifyNoInteractions(storyboardService, projectService);
    }
}

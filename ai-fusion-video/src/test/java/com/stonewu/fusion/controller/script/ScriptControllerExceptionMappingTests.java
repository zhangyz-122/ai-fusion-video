package com.stonewu.fusion.controller.script;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.GlobalExceptionHandler;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import com.stonewu.fusion.service.script.ScriptAutoSplitService;
import com.stonewu.fusion.service.script.ScriptService;
import com.stonewu.fusion.service.script.SubtitleExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SW-T06-02 / SW-T06-03 的 HTTP 语义回归：
 * 越权 → 403、资源不存在 → 404、查询参数类型不匹配 → 400，业务文案保持不变。
 */
class ScriptControllerExceptionMappingTests {

    private static final long EPISODE_ID = 210L;

    private ProjectAccessGuard accessGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ProjectAccessGuard.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScriptController(
                        mock(ScriptService.class), accessGuard,
                        mock(ScriptAutoSplitService.class), mock(SubtitleExportService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void subtitleOfInaccessibleEpisodeRespondsWith403AndUnchangedMessage() throws Exception {
        doThrow(new BusinessException(403, "无权访问该项目内容"))
                .when(accessGuard).assertScriptEpisode(EPISODE_ID);

        mockMvc.perform(get("/api/script/episode/{id}/subtitle.srt", EPISODE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("无权访问该项目内容"));
    }

    @Test
    void subtitleOfMissingEpisodeRespondsWith404AndUnchangedMessage() throws Exception {
        doThrow(new BusinessException(404, "剧本分集不存在: 999999"))
                .when(accessGuard).assertScriptEpisode(999999L);

        mockMvc.perform(get("/api/script/episode/{id}/subtitle.srt", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("剧本分集不存在: 999999"));
    }

    @Test
    void subtitleWithTypeMismatchedSecondsPerLineRespondsWith400() throws Exception {
        mockMvc.perform(get("/api/script/episode/{id}/subtitle.srt", EPISODE_ID)
                        .param("secondsPerLine", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数类型不匹配: secondsPerLine"));
    }
}

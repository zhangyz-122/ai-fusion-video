package com.stonewu.fusion.controller.script;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import com.stonewu.fusion.service.script.ScriptAutoSplitService;
import com.stonewu.fusion.service.script.ScriptService;
import com.stonewu.fusion.service.script.SubtitleExportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptControllerSubtitleExportTests {

    private final ScriptService scriptService = mock(ScriptService.class);
    private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
    private final ScriptAutoSplitService scriptAutoSplitService = mock(ScriptAutoSplitService.class);
    private final SubtitleExportService subtitleExportService = mock(SubtitleExportService.class);

    private final ScriptController controller = new ScriptController(
            scriptService, accessGuard, scriptAutoSplitService, subtitleExportService);

    @Test
    void downloadEpisodeSubtitleReturnsSrtAttachmentWithUtf8Filename() {
        ScriptEpisode episode = ScriptEpisode.builder()
                .id(7L).episodeNumber(3).title("疑云夜行").build();
        String srt = "1\n00:00:00,000 --> 00:00:03,000\n张三：你好\n\n";
        when(scriptService.getEpisodeById(7L)).thenReturn(episode);
        when(subtitleExportService.buildEpisodeSrt(7L, null)).thenReturn(srt);
        when(subtitleExportService.buildSubtitleFilename(episode)).thenReturn("第3集_疑云夜行.srt");

        ResponseEntity<byte[]> response = controller.downloadEpisodeSubtitle(7L, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("application/x-subrip"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains("filename*=UTF-8''" + java.net.URLEncoder.encode("第3集_疑云夜行.srt",
                        StandardCharsets.UTF_8));
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo(srt);
        verify(accessGuard).assertScriptEpisode(7L);
    }

    @Test
    void downloadEpisodeSubtitlePropagatesSecondsPerLine() {
        ScriptEpisode episode = ScriptEpisode.builder().id(7L).episodeNumber(1).build();
        when(scriptService.getEpisodeById(7L)).thenReturn(episode);
        when(subtitleExportService.buildEpisodeSrt(7L, 5)).thenReturn("");
        when(subtitleExportService.buildSubtitleFilename(episode)).thenReturn("第1集.srt");

        ResponseEntity<byte[]> response = controller.downloadEpisodeSubtitle(7L, 5);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(subtitleExportService).buildEpisodeSrt(7L, 5);
    }

    @Test
    void downloadEpisodeSubtitleRejectsEpisodeWithoutDialogue() {
        ScriptEpisode episode = ScriptEpisode.builder().id(7L).episodeNumber(2).build();
        when(scriptService.getEpisodeById(7L)).thenReturn(episode);
        when(subtitleExportService.buildEpisodeSrt(7L, null))
                .thenThrow(new BusinessException(400, "该分集暂无可导出的对白"));

        assertThatThrownBy(() -> controller.downloadEpisodeSubtitle(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该分集暂无可导出的对白");
        verify(accessGuard).assertScriptEpisode(7L);
    }
}

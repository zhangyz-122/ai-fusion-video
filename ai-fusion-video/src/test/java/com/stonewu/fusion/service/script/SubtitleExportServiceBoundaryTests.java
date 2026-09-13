package com.stonewu.fusion.service.script;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.service.script.SubtitleExportService.SubtitleLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * SW-T06 边界测试：字幕导出的参数边界、空对白拒绝与 SRT 结构不变量。
 */
@ExtendWith(MockitoExtension.class)
class SubtitleExportServiceBoundaryTests {

    @Mock
    private ScriptService scriptService;

    @InjectMocks
    private SubtitleExportService subtitleExportService;

    private static final Pattern TIMESTAMP = Pattern.compile("\\d{2}:\\d{2}:\\d{2},\\d{3}");

    // ========== secondsPerLine 参数边界 ==========

    @Test
    void secondsPerLineNullDefaultsToThree() {
        assertThat(subtitleExportService.normalizeSecondsPerLine(null)).isEqualTo(3);
    }

    @Test
    void secondsPerLineValidBoundariesAccepted() {
        assertThat(subtitleExportService.normalizeSecondsPerLine(1)).isEqualTo(1);
        assertThat(subtitleExportService.normalizeSecondsPerLine(60)).isEqualTo(60);
    }

    @Test
    void secondsPerLineOutOfRangeRejectedWith400() {
        for (Integer invalid : new Integer[]{0, -1, 61, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> subtitleExportService.normalizeSecondsPerLine(invalid))
                    .as("secondsPerLine=%s 应拒绝", invalid)
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(400);
                        assertThat(e.getMessage()).contains("1-60");
                    });
        }
    }

    // ========== 空对白拒绝 ==========

    @Test
    void episodeWithoutScenesRejectedWith400() {
        when(scriptService.listScenesByEpisode(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> subtitleExportService.buildEpisodeSrt(1L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("该分集暂无可导出的对白");
                });
    }

    @Test
    void episodeWithOnlyProductionInstructionsRejectedWith400() {
        ScriptSceneItem scene = ScriptSceneItem.builder()
                .sceneDescription("**画面：**\n城市夜景\n\n**生成提示词：**\ncinematic night")
                .build();
        when(scriptService.listScenesByEpisode(2L)).thenReturn(List.of(scene));

        assertThatThrownBy(() -> subtitleExportService.buildEpisodeSrt(2L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该分集暂无可导出的对白");
    }

    @Test
    void buildSrtWithNoLinesReturnsEmptyString() {
        assertThat(subtitleExportService.buildSrt(List.of(), 3)).isEmpty();
        assertThat(subtitleExportService.buildSrt(null, 3)).isEmpty();
    }

    // ========== SRT 结构不变量：序号连续、时间轴单调 ==========

    /** 解析生成的 SRT 并断言：块数、序号从 1 连续、时间轴严格递增且每条时长=secondsPerLine */
    private static void assertSrtInvariants(String srt, int expectedCues, int secondsPerLine) {
        String[] blocks = srt.strip().split("\n\n");
        assertThat(blocks).hasSize(expectedCues);
        long prevEndMillis = -1;
        for (int i = 0; i < blocks.length; i++) {
            String[] lines = blocks[i].split("\n");
            assertThat(lines).hasSize(3);
            assertThat(lines[0]).as("第 %d 条序号", i + 1).isEqualTo(String.valueOf(i + 1));
            String[] range = lines[1].split(" --> ");
            assertThat(range).hasSize(2);
            assertThat(TIMESTAMP.matcher(range[0]).matches()).isTrue();
            assertThat(TIMESTAMP.matcher(range[1]).matches()).isTrue();
            long start = toMillis(range[0]);
            long end = toMillis(range[1]);
            assertThat(start).as("第 %d 条开始不早于上一条结束", i + 1).isGreaterThanOrEqualTo(prevEndMillis);
            assertThat(end - start).as("第 %d 条时长", i + 1).isEqualTo(secondsPerLine * 1000L);
            assertThat(lines[2]).isNotBlank();
            prevEndMillis = end;
        }
    }

    private static long toMillis(String timestamp) {
        String[] parts = timestamp.split("[:,]");
        return Long.parseLong(parts[0]) * 3_600_000
                + Long.parseLong(parts[1]) * 60_000
                + Long.parseLong(parts[2]) * 1000
                + Long.parseLong(parts[3]);
    }

    @Test
    void srtTimelineMonotonicWithDefaultSeconds() {
        String srt = subtitleExportService.buildSrt(List.of(
                new SubtitleLine("甲", "第一句"),
                new SubtitleLine("乙", "第二句"),
                new SubtitleLine(null, "旁白"),
                new SubtitleLine("丙", "第三句")), 3);
        assertSrtInvariants(srt, 4, 3);
        assertThat(srt).startsWith("1\n00:00:00,000 --> 00:00:03,000\n甲：第一句");
    }

    @Test
    void srtTimelineMonotonicAtMinSeconds() {
        String srt = subtitleExportService.buildSrt(List.of(
                new SubtitleLine("甲", "一"),
                new SubtitleLine("乙", "二"),
                new SubtitleLine("丙", "三")), 1);
        assertSrtInvariants(srt, 3, 1);
    }

    @Test
    void srtTimelineMonotonicAtMaxSeconds() {
        String srt = subtitleExportService.buildSrt(List.of(
                new SubtitleLine("甲", "一"),
                new SubtitleLine("乙", "二")), 60);
        assertSrtInvariants(srt, 2, 60);
        assertThat(srt).contains("00:01:00,000 --> 00:02:00,000");
    }

    @Test
    void buildEpisodeSrtEndToEndRespectsSecondsPerLine() {
        ScriptSceneItem scene = ScriptSceneItem.builder()
                .dialogues("[{\"speaker\":\"陈砚\",\"line\":\"走吧。\"},{\"speaker\":\"林晚\",\"line\":\"等等我。\"}]")
                .build();
        when(scriptService.listScenesByEpisode(9L)).thenReturn(List.of(scene));

        String srt = subtitleExportService.buildEpisodeSrt(9L, 5);
        assertSrtInvariants(srt, 2, 5);
    }

    // ========== 对白抽取边界：type 过滤与来源回退 ==========

    @Test
    void dialogueTypeNonSpeechSkippedButMissingTypeKept() {
        List<SubtitleLine> lines = subtitleExportService.extractFromDialogues(
                "[{\"speaker\":\"甲\",\"line\":\"保留\",\"type\":\"1\"},"
                        + "{\"speaker\":\"乙\",\"line\":\"舞台指示\",\"type\":\"2\"},"
                        + "{\"speaker\":\"丙\",\"line\":\"画外音保留\",\"type\":\"3\"},"
                        + "{\"speaker\":\"丁\",\"line\":\"无type按对白\"}]");
        assertThat(lines).extracting(SubtitleLine::content)
                .containsExactly("保留", "画外音保留", "无type按对白");
    }

    @Test
    void invalidDialoguesJsonFallsBackToDescription() {
        ScriptSceneItem scene = ScriptSceneItem.builder()
                .dialogues("{不是数组")
                .sceneDescription("张三：你好。\n李四：你好啊。")
                .build();
        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(scene));
        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("张三：你好。", "李四：你好啊。");
    }

    // ========== 文件名边界 ==========

    @Test
    void filenameSanitizesIllegalCharsAndTruncatesLongTitle() {
        ScriptEpisode episode = ScriptEpisode.builder()
                .id(1L).episodeNumber(7).title("标题:含/非法*字符?").build();
        assertThat(subtitleExportService.buildSubtitleFilename(episode))
                .isEqualTo("第7集_标题含非法字符.srt");

        ScriptEpisode longTitle = ScriptEpisode.builder()
                .id(2L).episodeNumber(8).title("长".repeat(80)).build();
        assertThat(subtitleExportService.buildSubtitleFilename(longTitle))
                .isEqualTo("第8集_" + "长".repeat(60) + ".srt");

        ScriptEpisode blank = ScriptEpisode.builder().id(3L).episodeNumber(null).title("  ").build();
        assertThat(subtitleExportService.buildSubtitleFilename(blank)).isEqualTo("第3集.srt");
    }
}

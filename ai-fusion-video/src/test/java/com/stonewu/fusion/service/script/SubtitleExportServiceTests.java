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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubtitleExportServiceTests {

    @Mock
    private ScriptService scriptService;

    @InjectMocks
    private SubtitleExportService subtitleExportService;

    private static ScriptSceneItem scene(String dialogues, String description) {
        return ScriptSceneItem.builder()
                .dialogues(dialogues)
                .sceneDescription(description)
                .build();
    }

    // ========== SRT 渲染 ==========

    @Test
    void buildSrtRendersSequentialCuesWithChineseText() {
        String srt = subtitleExportService.buildSrt(List.of(
                new SubtitleLine("张三", "你好，好久不见。"),
                new SubtitleLine("李四", "是啊，请进。")), 3);

        assertThat(srt).isEqualTo("""
                1
                00:00:00,000 --> 00:00:03,000
                张三：你好，好久不见。

                2
                00:00:03,000 --> 00:00:06,000
                李四：是啊，请进。

                """);
    }

    @Test
    void buildSrtSupportsCustomSecondsPerLineAcrossMinutes() {
        String srt = subtitleExportService.buildSrt(List.of(
                new SubtitleLine(null, "旁白内容"),
                new SubtitleLine("张三", "第二句")), 60);

        assertThat(srt).isEqualTo("""
                1
                00:00:00,000 --> 00:01:00,000
                旁白内容

                2
                00:01:00,000 --> 00:02:00,000
                张三：第二句

                """);
    }

    @Test
    void buildSrtWithoutSpeakerKeepsRawContent() {
        String srt = subtitleExportService.buildSrt(List.of(new SubtitleLine(null, "雨夜街道")), 3);

        assertThat(srt).isEqualTo("""
                1
                00:00:00,000 --> 00:00:03,000
                雨夜街道

                """);
    }

    @Test
    void buildSrtReturnsEmptyStringWhenNoLines() {
        assertThat(subtitleExportService.buildSrt(List.of(), 3)).isEmpty();
        assertThat(subtitleExportService.buildSrt(null, 3)).isEmpty();
    }

    @Test
    void buildSrtKeepsOverlongTextIntact() {
        String longText = "非常长的台词".repeat(2000);
        String srt = subtitleExportService.buildSrt(List.of(new SubtitleLine("张三", longText)), 3);

        String[] lines = srt.split("\n", -1);
        assertThat(lines).hasSize(5);
        assertThat(lines[0]).isEqualTo("1");
        assertThat(lines[1]).isEqualTo("00:00:00,000 --> 00:00:03,000");
        assertThat(lines[2]).isEqualTo("张三：" + longText);
        assertThat(lines[3]).isEmpty();
        assertThat(lines[4]).isEmpty();
    }

    // ========== 对白抽取 ==========

    @Test
    void extractLinesPrefersDialoguesJsonAndSkipsNonSpeechTypes() {
        ScriptSceneItem item = scene(
                "[{\"type\":1,\"character_name\":\"张三\",\"content\":\"你好\"},"
                        + "{\"type\":2,\"content\":\"动作描写\"},"
                        + "{\"type\":5,\"content\":\"环境描写\"},"
                        + "{\"type\":1,\"content\":\"无说话人台词\"}]",
                "王五：不应取用的描述行");

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).displayText()).isEqualTo("张三：你好");
        assertThat(lines.get(1).displayText()).isEqualTo("无说话人台词");
    }

    @Test
    void extractLinesReadsSpeakerLineJsonWithoutType() {
        ScriptSceneItem item = scene(
                "[{\"speaker\":\"张三\",\"line\":\"你好\"},{\"speaker\":\"李四\",\"line\":\"请坐\"}]",
                null);

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("张三：你好", "李四：请坐");
    }

    @Test
    void extractLinesTreatsVoiceOverAsSpeech() {
        ScriptSceneItem item = scene(
                "[{\"type\":3,\"character_name\":\"张三\",\"content\":\"内心独白\"}]",
                null);

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText).containsExactly("张三：内心独白");
    }

    @Test
    void extractLinesFallsBackToDescriptionDialogueLines() {
        ScriptSceneItem item = scene(null, """
                内景 张三家客厅 夜
                屋内灯光昏暗。
                张三：你好。
                李四：请坐。
                场景：城市夜景
                """);

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("张三：你好。", "李四：请坐。");
    }

    @Test
    void extractLinesUsesDescriptionWhenDialoguesJsonInvalid() {
        ScriptSceneItem item = scene("不是 JSON", "张三：你好。");

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText).containsExactly("张三：你好。");
    }

    @Test
    void extractLinesParsesMarkdownSpeakerLabelsInDescription() {
        ScriptSceneItem item = scene(null, """
                **画面：**
                货架另一端突然传来声响。

                **台词：**
                （无）

                **米糯：**
                “别过来…… 我只有这一包吃的……”

                **陈砚：**
                “你一个人？”

                **生成提示词：**
                3D 国漫漫剧风格。
                """);

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("米糯：别过来…… 我只有这一包吃的……", "陈砚：你一个人？");
    }

    @Test
    void extractLinesKeepsNarrationLabelWithoutSpeakerPrefix() {
        ScriptSceneItem item = scene(null, """
                **画面：**
                城市空镜。

                **字幕 / 旁白：**
                “病毒爆发后的第八个月，城市停在了寂静里。”
                """);

        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(item));

        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("病毒爆发后的第八个月，城市停在了寂静里。");
    }

    @Test
    void extractLinesSkipsPlaceholderDialogueBlock() {
        ScriptSceneItem item = scene(null, """
                **陈砚：**
                （无）

                **动作提示词：**
                陈砚半蹲在货架前。
                """);

        assertThat(subtitleExportService.extractLines(List.of(item))).isEmpty();
    }

    @Test
    void extractLinesIgnoresProseLinesEndingWithColon() {
        ScriptSceneItem item = scene(null, """
                ## 建议生成顺序

                可以按这个节奏做：

                1. 初遇组队
                2. 安全屋囤货
                """);

        assertThat(subtitleExportService.extractLines(List.of(item))).isEmpty();
    }

    @Test
    void extractLinesCombinesScenesInOrderAndSkipsBlankOnes() {
        List<SubtitleLine> lines = subtitleExportService.extractLines(List.of(
                scene("[{\"speaker\":\"张三\",\"line\":\"第一场\"}]", null),
                scene(null, null),
                scene(null, "李四：第二场")));

        assertThat(lines).extracting(SubtitleLine::displayText)
                .containsExactly("张三：第一场", "李四：第二场");
    }

    @Test
    void extractLinesReturnsEmptyWhenNothingToExport() {
        assertThat(subtitleExportService.extractLines(List.of())).isEmpty();
        assertThat(subtitleExportService.extractLines(List.of(scene(null, "没有台词的描述")))).isEmpty();
    }

    // ========== 整集导出 ==========

    @Test
    void buildEpisodeSrtCombinesScenesWithDefaultDuration() {
        when(scriptService.listScenesByEpisode(7L)).thenReturn(List.of(
                scene("[{\"speaker\":\"张三\",\"line\":\"你好\"}]", null),
                scene(null, "李四：请坐。")));

        String srt = subtitleExportService.buildEpisodeSrt(7L, null);

        assertThat(srt).isEqualTo("""
                1
                00:00:00,000 --> 00:00:03,000
                张三：你好

                2
                00:00:03,000 --> 00:00:06,000
                李四：请坐。

                """);
    }

    @Test
    void buildEpisodeSrtRejectsEpisodeWithoutDialogue() {
        when(scriptService.listScenesByEpisode(7L)).thenReturn(List.of(scene(null, "仅环境描述")));

        assertThatThrownBy(() -> subtitleExportService.buildEpisodeSrt(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该分集暂无可导出的对白");
    }

    @Test
    void normalizeSecondsPerLineValidatesRange() {
        assertThat(subtitleExportService.normalizeSecondsPerLine(null)).isEqualTo(3);
        assertThat(subtitleExportService.normalizeSecondsPerLine(1)).isEqualTo(1);
        assertThat(subtitleExportService.normalizeSecondsPerLine(60)).isEqualTo(60);
        assertThatThrownBy(() -> subtitleExportService.normalizeSecondsPerLine(0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> subtitleExportService.normalizeSecondsPerLine(61))
                .isInstanceOf(BusinessException.class);
    }

    // ========== 下载文件名 ==========

    @Test
    void buildSubtitleFilenameSanitizesTitle() {
        ScriptEpisode episode = ScriptEpisode.builder()
                .id(9L).episodeNumber(3).title("夜行/疑云:第一幕?").build();

        assertThat(subtitleExportService.buildSubtitleFilename(episode))
                .isEqualTo("第3集_夜行疑云第一幕.srt");
    }

    @Test
    void buildSubtitleFilenameFallsBackToEpisodeNumberWithoutTitle() {
        assertThat(subtitleExportService.buildSubtitleFilename(
                ScriptEpisode.builder().id(9L).episodeNumber(2).title("  ").build()))
                .isEqualTo("第2集.srt");
        assertThat(subtitleExportService.buildSubtitleFilename(
                ScriptEpisode.builder().id(9L).build()))
                .isEqualTo("第9集.srt");
    }
}

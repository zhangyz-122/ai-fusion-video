package com.stonewu.fusion.service.ai.tool.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptSegmentSplitterTests {

    @Test
    void emptyRawContentHasZeroSegments() {
        assertThat(ScriptSegmentSplitter.segmentCount(null, 100)).isZero();
        assertThat(ScriptSegmentSplitter.segmentCount("", 100)).isZero();
    }

    @Test
    void shortRawContentIsSingleSegmentEqualToRaw() {
        String raw = "第一集\n短剧本内容";
        ScriptSegmentSplitter.Segment segment =
                ScriptSegmentSplitter.segment(raw, 100, 1);
        assertThat(segment.index()).isEqualTo(1);
        assertThat(segment.content()).isEqualTo(raw);
        assertThat(ScriptSegmentSplitter.segmentCount(raw, 100)).isEqualTo(1);
    }

    @Test
    void splitsAtParagraphBoundaryNearSegmentLimit() {
        String paragraphA = "甲".repeat(600);
        String paragraphB = "乙".repeat(600);
        String raw = paragraphA + "\n\n" + paragraphB;
        ScriptSegmentSplitter.Segment first =
                ScriptSegmentSplitter.segment(raw, 1000, 1);
        // 优先在空行边界收刀：第一段恰好装下段落 A（含分隔符），第二段装下段落 B
        assertThat(first.content()).isEqualTo(paragraphA + "\n\n");
        ScriptSegmentSplitter.Segment second =
                ScriptSegmentSplitter.segment(raw, 1000, 2);
        assertThat(second.content()).isEqualTo(paragraphB);
    }

    @Test
    void fallsBackToLineBreakWhenNoParagraphBoundary() {
        String lineA = "甲".repeat(600);
        String lineB = "乙".repeat(600);
        String raw = lineA + "\n" + lineB;
        ScriptSegmentSplitter.Segment first =
                ScriptSegmentSplitter.segment(raw, 1000, 1);
        assertThat(first.content()).isEqualTo(lineA + "\n");
    }

    @Test
    void hardCutsWhenNoBoundaryWithinLimit() {
        String raw = "甲".repeat(2500);
        ScriptSegmentSplitter.Segment first =
                ScriptSegmentSplitter.segment(raw, 1000, 1);
        assertThat(first.content()).hasSize(1000);
        assertThat(ScriptSegmentSplitter.segmentCount(raw, 1000)).isEqualTo(3);
    }

    @Test
    void segmentsConcatenateBackToTheWholeRawContent() {
        String raw = ("段落内容".repeat(700) + "\n\n" + "第二段落".repeat(400)
                + "\n" + "第三段落".repeat(300) + "\n\n结尾");
        int segmentChars = 1000;
        int total = ScriptSegmentSplitter.segmentCount(raw, segmentChars);
        StringBuilder joined = new StringBuilder();
        for (int i = 1; i <= total; i++) {
            ScriptSegmentSplitter.Segment segment =
                    ScriptSegmentSplitter.segment(raw, segmentChars, i);
            assertThat(segment.index()).isEqualTo(i);
            assertThat(segment.fromChar()).isLessThan(segment.toChar());
            joined.append(segment.content());
        }
        assertThat(joined.toString()).isEqualTo(raw);
    }

    @Test
    void outOfRangeSegmentIndexFailsWithTotalCountMessage() {
        String raw = "内容".repeat(2000);
        int total = ScriptSegmentSplitter.segmentCount(raw, 1000);
        assertThat(total).isGreaterThan(1);
        for (int invalid : List.of(0, -1, total + 1)) {
            assertThatThrownBy(() -> ScriptSegmentSplitter.segment(raw, 1000, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("总分段数")
                    .hasMessageContaining(String.valueOf(total));
        }
    }
}

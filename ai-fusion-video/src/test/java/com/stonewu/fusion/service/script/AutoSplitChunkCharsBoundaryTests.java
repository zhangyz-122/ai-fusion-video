package com.stonewu.fusion.service.script;

import com.stonewu.fusion.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SW-T06 边界测试：chunkChars 归一化与分块切分的边界行为。
 * 只覆盖纯函数（normalizeChunkChars / splitIntoChunks），不依赖数据库与模型。
 */
class AutoSplitChunkCharsBoundaryTests {

    private static final int MIN = 2000;
    private static final int MAX = 12000;

    // ========== normalizeChunkChars：钳制边界 ==========

    @Test
    void normalizeChunkCharsNullFallsBackToDefault() {
        assertThat(ScriptAutoSplitService.normalizeChunkChars(null)).isEqualTo(6000);
    }

    @Test
    void normalizeChunkCharsBelowMinClampsUpToMin() {
        assertThat(ScriptAutoSplitService.normalizeChunkChars(MIN - 1)).isEqualTo(MIN);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(0)).isEqualTo(MIN);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(-1)).isEqualTo(MIN);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(Integer.MIN_VALUE)).isEqualTo(MIN);
    }

    @Test
    void normalizeChunkCharsAboveMaxClampsDownToMax() {
        assertThat(ScriptAutoSplitService.normalizeChunkChars(MAX + 1)).isEqualTo(MAX);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(Integer.MAX_VALUE)).isEqualTo(MAX);
    }

    @Test
    void normalizeChunkCharsInsideRangeKeptAsIs() {
        assertThat(ScriptAutoSplitService.normalizeChunkChars(MIN)).isEqualTo(MIN);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(MAX)).isEqualTo(MAX);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(6000)).isEqualTo(6000);
    }

    // ========== splitIntoChunks：切分尺寸与钳制一致性 ==========

    private final ScriptAutoSplitService service = new ScriptAutoSplitService(
            null, null, null, null, null, null, null);

    /** 生成 count 个无换行的段落，每段 length 字符，段落间以单个换行分隔 */
    private static String paragraphs(int count, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            for (int j = 0; j < length; j++) {
                builder.append((char) ('a' + (i + j) % 26));
            }
        }
        return builder.toString();
    }

    /** 拼回全部分块并去除聚合时插入的空白，用于丢字校验 */
    private static String flatten(List<String> chunks) {
        return String.join("", chunks).replaceAll("\\s+", "");
    }

    private static String flatten(String text) {
        return text.replaceAll("\\s+", "");
    }

    @Test
    void splitRespectsClampedMinWhenNegativePassed() {
        // 25 段 × 500 字 = 12500 字；chunkChars=-1 应钳制为 2000（而非默认 6000 或原值 -1）
        String raw = paragraphs(25, 500);
        List<String> chunks = service.splitIntoChunks(raw, -1);

        assertThat(chunks).as("钳制为 2000 后的块数").hasSize(9);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(MIN));
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitRespectsClampedMaxWhenOversizedPassed() {
        // 25 段 × 500 字；chunkChars=999999 应钳制为 12000（若不钳制则为单块）
        String raw = paragraphs(25, 500);
        List<String> chunks = service.splitIntoChunks(raw, 999_999);

        assertThat(chunks).as("钳制为 12000 后应切为 2 块").hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(MAX));
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitBoundaryValuesPassThroughUnchanged() {
        String raw = paragraphs(25, 500);

        List<String> atMin = service.splitIntoChunks(raw, MIN);
        List<String> belowMin = service.splitIntoChunks(raw, MIN - 1);
        assertThat(atMin).as("MIN-1 与 MIN 钳制结果一致").isEqualTo(belowMin);
        assertThat(atMin).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(MIN));

        List<String> atMax = service.splitIntoChunks(raw, MAX);
        List<String> aboveMax = service.splitIntoChunks(raw, MAX + 1);
        assertThat(atMax).as("MAX+1 与 MAX 钳制结果一致").isEqualTo(aboveMax);
    }

    @Test
    void splitHardCutsParagraphLongerThanChunkSize() {
        // 单段 5000 字无换行，无法在段落边界切分，必须硬切。
        // 已知行为（见 BUGS SW-T06-01）：聚合时块尾追加 "\n\n"，
        // 恰好命中 target 的单片段块长度为 chunkChars+2（超出部分仅为尾部空白分隔符，
        // convertChunk 的 inputLimit 截断只削去这 2 个空白字符，不丢正文）。
        String raw = paragraphs(1, 5000);
        List<String> chunks = service.splitIntoChunks(raw, MIN);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isEqualTo(raw.substring(0, MIN) + "\n\n");
        assertThat(chunks.get(1)).isEqualTo(raw.substring(MIN, 2 * MIN) + "\n\n");
        assertThat(chunks.get(2)).isEqualTo(raw.substring(2 * MIN) + "\n\n");
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitKeepsChapterHeadingsAtChunkStart() {
        String chapter1 = "第一章 风起\n\n" + paragraphs(3, 200) + "\n\n";
        String chapter2 = "第二章 云涌\n\n" + paragraphs(3, 200);
        List<String> chunks = service.splitIntoChunks(chapter1 + chapter2, MIN);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).startsWith("第一章 风起");
        assertThat(chunks.get(0)).contains("第二章 云涌");
    }

    @Test
    void splitRejectsTextProducingMoreThanMaxChunks() {
        // 500 段 × 2000 字在 2000 上限下每段恰好独立成块，超过 400 块上限必须拒绝
        String raw = paragraphs(500, MIN);
        assertThatThrownBy(() -> service.splitIntoChunks(raw, MIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("400");
    }

    @Test
    void splitDefaultChunkSizeMatchesNullContract() {
        String raw = paragraphs(25, 500);
        assertThat(service.splitIntoChunks(raw))
                .as("单参重载等价于传 null（默认 6000）")
                .isEqualTo(service.splitIntoChunks(raw, null));
        assertThat(service.splitIntoChunks(raw)).hasSize(3);
    }
}

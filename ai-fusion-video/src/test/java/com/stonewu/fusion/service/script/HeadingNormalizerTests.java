package com.stonewu.fusion.service.script;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link HeadingNormalizer} 标头清洗纯函数的单测 */
class HeadingNormalizerTests {

    // ========== 时间词归一 ==========

    @Test
    void normalize_mapsDayTimeWordsToDay() {
        assertThat(HeadingNormalizer.normalize("外景 学校操场 白天")).isEqualTo("外景 学校操场 日");
        assertThat(HeadingNormalizer.normalize("内景 厨房 傍晚")).isEqualTo("内景 厨房 日");
        assertThat(HeadingNormalizer.normalize("内景 办公室 下午")).isEqualTo("内景 办公室 日");
        assertThat(HeadingNormalizer.normalize("内景 天台 午后")).isEqualTo("内景 天台 日");
    }

    @Test
    void normalize_mapsNightTimeWordsToNight() {
        assertThat(HeadingNormalizer.normalize("内景 卧室 晚上")).isEqualTo("内景 卧室 夜");
        assertThat(HeadingNormalizer.normalize("外景 街道 深夜")).isEqualTo("外景 街道 夜");
        assertThat(HeadingNormalizer.normalize("内景 实验室 午夜")).isEqualTo("内景 实验室 夜");
        assertThat(HeadingNormalizer.normalize("内景 厨房 晚")).isEqualTo("内景 厨房 夜");
    }

    @Test
    void normalize_mapsDawnTimeWordsToMorning() {
        assertThat(HeadingNormalizer.normalize("外景 码头 凌晨")).isEqualTo("外景 码头 晨");
        assertThat(HeadingNormalizer.normalize("内景 厨房 清晨")).isEqualTo("内景 厨房 晨");
        assertThat(HeadingNormalizer.normalize("内景 公园 早晨")).isEqualTo("内景 公园 晨");
    }

    @Test
    void normalize_keepsStandardAndUncommonTimeWords() {
        // 标准时间词保持不变
        assertThat(HeadingNormalizer.normalize("内景 厨房 夜")).isEqualTo("内景 厨房 夜");
        assertThat(HeadingNormalizer.normalize("外景 山顶 日")).isEqualTo("外景 山顶 日");
        assertThat(HeadingNormalizer.normalize("内景 病房 晨")).isEqualTo("内景 病房 晨");
        // 昼等枚举外生僻时间词原样保留
        assertThat(HeadingNormalizer.normalize("内景 战场 昼")).isEqualTo("内景 战场 昼");
        assertThat(HeadingNormalizer.normalize("外景 海边 黄昏")).isEqualTo("外景 海边 黄昏");
    }

    // ========== 冗余后缀去重 ==========

    @Test
    void normalize_stripsSuffixDuplicatingLocationLastChar() {
        assertThat(HeadingNormalizer.normalize("内景 103室 室")).isEqualTo("内景 103室");
    }

    @Test
    void normalize_stripsSuffixDuplicatingSceneMarker() {
        assertThat(HeadingNormalizer.normalize("内景 机甲竞技场 内")).isEqualTo("内景 机甲竞技场");
        assertThat(HeadingNormalizer.normalize("外景 训练场 外")).isEqualTo("外景 训练场");
    }

    @Test
    void normalize_stripsRedundantSuffixBeforeTrailingTimeWord() {
        // 冗余后缀位于时间词之前时同样剔除，且时间词先归一
        assertThat(HeadingNormalizer.normalize("内景 103室 室 白天")).isEqualTo("内景 103室 日");
        assertThat(HeadingNormalizer.normalize("内景 机甲竞技场 内 夜")).isEqualTo("内景 机甲竞技场 夜");
    }

    @Test
    void normalize_neverStripsBelowSceneTypeAndLocation() {
        // 至少保留 景别+地点 两个 token，不会把地点本身削掉
        assertThat(HeadingNormalizer.normalize("内景 室")).isEqualTo("内景 室");
        assertThat(HeadingNormalizer.normalize("内景 内")).isEqualTo("内景 内");
    }

    @Test
    void normalize_doesNotTouchNonRedundantTail() {
        assertThat(HeadingNormalizer.normalize("内景 103室")).isEqualTo("内景 103室");
        assertThat(HeadingNormalizer.normalize("内景 机甲竞技场")).isEqualTo("内景 机甲竞技场");
    }

    // ========== 结构与空白 ==========

    @Test
    void normalize_collapsesWhitespace() {
        assertThat(HeadingNormalizer.normalize("内景　厨房　夜")).isEqualTo("内景 厨房 夜");
        assertThat(HeadingNormalizer.normalize("  内景  厨房   夜  ")).isEqualTo("内景 厨房 夜");
    }

    @Test
    void normalize_blankHeadingReturnsAsIs() {
        assertThat(HeadingNormalizer.normalize(null)).isNull();
        assertThat(HeadingNormalizer.normalize("")).isEmpty();
        assertThat(HeadingNormalizer.normalize("   ")).isEqualTo("   ");
    }
}

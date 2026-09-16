package com.stonewu.fusion.service.ai.tool.script;

/**
 * 剧本原文分段切分纯函数（无状态、可单测）。
 * <p>
 * get_project_script 与 read_script_segment 共用同一套切分规则，
 * 保证两个工具对"第 N 段"的理解完全一致。
 * 切分只决定读取窗口，不修改原文本身。
 */
public final class ScriptSegmentSplitter {

    /**
     * 段内至少要装满 segmentChars 的一半才允许提前在边界收刀，
     * 防止连续空行导致出现极短分段甚至死循环。
     */
    private static final int MIN_SEGMENT_CHARS_RATIO = 2;

    private ScriptSegmentSplitter() {
    }

    /**
     * 单个分段。index 从 1 开始；[fromChar, toChar) 是该段在原文中的字符区间。
     */
    public record Segment(int index, int fromChar, int toChar, String content) {
    }

    /**
     * 总分段数。原文为 null 或空串时为 0。
     */
    public static int segmentCount(String raw, int segmentChars) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (from < raw.length()) {
            from = segmentEnd(raw, from, segmentChars);
            count++;
        }
        return count;
    }

    /**
     * 取第 segmentIndex 段（1 开始）。
     * 越界时抛出 IllegalArgumentException，错误消息包含总分段数，
     * 供工具执行器原样转成模型可读的错误 JSON。
     */
    public static Segment segment(String raw, int segmentChars, int segmentIndex) {
        int totalSegments = segmentCount(raw, segmentChars);
        if (segmentIndex < 1 || segmentIndex > totalSegments) {
            throw new IllegalArgumentException(
                    "segment 越界：收到 " + segmentIndex + "，但本书总分段数为 " + totalSegments
                            + "，合法范围是 1 到 " + totalSegments);
        }
        int from = 0;
        for (int i = 1; i < segmentIndex; i++) {
            from = segmentEnd(raw, from, segmentChars);
        }
        int to = segmentEnd(raw, from, segmentChars);
        return new Segment(segmentIndex, from, to, raw.substring(from, to));
    }

    /**
     * 计算从 from 开始的单段结束位置（不含）。
     * 优先在段落空行处收刀，其次换行符；附近没有可用边界时按 segmentChars 硬切。
     */
    static int segmentEnd(String raw, int from, int segmentChars) {
        int hardEnd = Math.min(from + segmentChars, raw.length());
        if (hardEnd >= raw.length()) {
            return raw.length();
        }
        int earliest = from + Math.max(1, segmentChars / MIN_SEGMENT_CHARS_RATIO);
        int paragraph = raw.lastIndexOf("\n\n", hardEnd - 1);
        if (paragraph >= earliest) {
            return Math.min(paragraph + 2, hardEnd);
        }
        int lineBreak = raw.lastIndexOf('\n', hardEnd - 1);
        if (lineBreak >= earliest) {
            return Math.min(lineBreak + 1, hardEnd);
        }
        return hardEnd;
    }
}

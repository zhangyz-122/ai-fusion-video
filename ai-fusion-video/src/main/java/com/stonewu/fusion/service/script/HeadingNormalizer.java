package com.stonewu.fusion.service.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场次标头清洗（归一化纯函数，落库前套用）。
 * <p>
 * 实测小模型标头存在两类噪音：
 * 1. 时间词超出 日/夜 枚举（白天/午后/下午/傍晚/晚上/深夜/午夜/晚/凌晨/清晨/早晨），
 *    按映射归一到标准位次的 日/夜/晨，其余生僻时间词原样保留；
 * 2. 冗余后缀（「内景 103室 室」「内景 机甲竞技场 内」），地点末字或景别字
 *    在标头末尾重复出现时去重。
 * 标头整体保持「内景/外景 地点 时间」结构与空格分隔。
 */
final class HeadingNormalizer {

    /** 标准时间词映射：非标准时间词 → 标准时间词（仅作用于标头末位） */
    private static final Map<String, String> TIME_WORD_MAP = Map.ofEntries(
            Map.entry("白天", "日"),
            Map.entry("午后", "日"),
            Map.entry("下午", "日"),
            Map.entry("傍晚", "日"),
            Map.entry("晚上", "夜"),
            Map.entry("深夜", "夜"),
            Map.entry("午夜", "夜"),
            Map.entry("晚", "夜"),
            Map.entry("凌晨", "晨"),
            Map.entry("清晨", "晨"),
            Map.entry("早晨", "晨"));

    /** 映射后的标准时间词集合 */
    private static final Set<String> STANDARD_TIME_WORDS = Set.of("日", "夜", "晨");

    /** 景别字：标头末尾与其重复时视为冗余后缀 */
    private static final Set<String> SCENE_MARKERS = Set.of("内", "外");

    private HeadingNormalizer() {
    }

    /** 清洗标头：空白归一 → 末位时间词映射 → 去冗余后缀；空标头原样返回 */
    static String normalize(String heading) {
        if (heading == null || heading.isBlank()) {
            return heading;
        }
        // 空白归一：全角/连续空白折叠为单个半角空格，保证按空格切分稳定
        List<String> tokens = new ArrayList<>(Arrays.asList(
                heading.strip().replaceAll("[\\s　]+", " ").split(" ")));
        mapTrailingTimeWord(tokens);
        stripRedundantSuffix(tokens);
        return String.join(" ", tokens);
    }

    /** 末位时间词映射：白天→日、晚上→夜、清晨→晨 等；映射表之外的时间词原样保留 */
    private static void mapTrailingTimeWord(List<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        int last = tokens.size() - 1;
        String mapped = TIME_WORD_MAP.get(tokens.get(last));
        if (mapped != null) {
            tokens.set(last, mapped);
        }
    }

    /**
     * 去冗余后缀：标头末尾（时间词之前）反复出现「单字且与景别字重复」或
     * 「单字且与前一个地点 token 末字重复」的 token 时移除，
     * 且至少保留 景别+地点 两个 token，防止把地点本身削掉。
     */
    private static void stripRedundantSuffix(List<String> tokens) {
        // 末位是标准时间词时保留时间位，只清洗其前缀；endIndex 始终指向待清洗范围的末尾
        int endIndex = tokens.size();
        if (endIndex > 0 && STANDARD_TIME_WORDS.contains(tokens.get(endIndex - 1))) {
            endIndex -= 1;
        }
        // 至少保留 景别+地点 两个 token（endIndex >= 3 才存在可剔除的后缀）
        while (endIndex >= 3) {
            String tail = tokens.get(endIndex - 1);
            String prev = tokens.get(endIndex - 2);
            if (!isRedundantSuffix(tail, prev)) {
                break;
            }
            tokens.remove(endIndex - 1);
            endIndex -= 1;
        }
    }

    /** 冗余后缀判定：单字 token，重复景别字（内/外）或与前一个地点 token 末字重复 */
    private static boolean isRedundantSuffix(String tail, String prev) {
        if (tail == null || tail.length() != 1 || prev == null || prev.isEmpty()) {
            return false;
        }
        return SCENE_MARKERS.contains(tail) || tail.charAt(0) == prev.charAt(prev.length() - 1);
    }
}

package com.stonewu.fusion.service.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * 收尾合成阶段：所有分块解析完成后，用两次纯 JSON 调用合成剧本级元数据。
 * <p>
 * 调用 1 输入全部分集标题+概要合成故事梗概与题材标签，超长时先分批总结、
 * 再合并为最终结果；调用 2 输入剧本简介+角色出场统计合成人物表。
 * 输出 schema 与 Agent 链路写入的 characters_json 完全一致：
 * [{name, importance(主角/配角/反派/龙套), description}]。
 * 每次调用输出不合规时重试一次，仍失败由调用方显式降级（记 warn 并在完成日志注明），
 * 不写入对应字段，也不导致整个解析任务失败。
 */
@Component
@Slf4j
public class ScriptMetadataSynthesizer {

    /** 单次合成调用的输入上限：分集信息超过该长度时分批总结后合并 */
    static final int SYNTHESIS_INPUT_MAX_CHARS = 30_000;
    /** 故事梗概硬约束：与提示词要求一致，防止模型照抄长文 */
    static final int SYNOPSIS_MAX_CHARS = 300;
    /** 人物设定一句话硬约束 */
    static final int CHARACTER_DESCRIPTION_MAX_CHARS = 60;
    /** 对白样例单条截断长度：控制调用 2 的输入总量 */
    static final int SAMPLE_DIALOGUE_MAX_CHARS = 50;
    /** 每个角色抽取的对白样例条数 */
    static final int SAMPLE_DIALOGUES_PER_CHARACTER = 2;
    /** importance 的合法取值：与 Agent 链路参考数据一致 */
    static final Set<String> IMPORTANCES = Set.of("主角", "配角", "反派", "龙套");
    /** importance 非法或缺失时的归一化默认值 */
    static final String DEFAULT_IMPORTANCE = "配角";
    /** 人物表输入的固定标头“角色出场统计：\n”占用字符数（取自 ScriptFinalizePrompts，避免漂移） */
    private static final int CHARACTER_STATS_HEADER_CHARS =
            ScriptFinalizePrompts.characterUserMessage(null, "").length();

    /** 分集摘要：标题 + 一句话概要（原文兜底的集没有概要） */
    public record EpisodeDigest(String title, String synopsis) {
    }

    /** 角色出场统计：按出现顺序累计频次并保留最多两条对白样例 */
    public static final class CharacterStat {

        private final String name;
        private int count;
        private final List<String> samples = new ArrayList<>();

        public CharacterStat(String name) {
            this.name = name;
        }

        /** 记一次 type=1 对白：频次 +1，未满样例数时保留该条原文（截断到样例上限） */
        public void record(String dialogueContent) {
            count += 1;
            if (samples.size() >= SAMPLE_DIALOGUES_PER_CHARACTER) {
                return;
            }
            String sample = dialogueContent == null ? "" : dialogueContent.strip();
            if (sample.isEmpty()) {
                return;
            }
            samples.add(truncate(sample, SAMPLE_DIALOGUE_MAX_CHARS));
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public List<String> getSamples() {
            return samples;
        }
    }

    /**
     * 合成结果：success=false 时 failures 注明缺失项（故事梗概/人物表），
     * 已成功的字段仍正常返回供调用方持久化（显式降级，不整体失败）。
     */
    public record MetadataResult(String storySynopsis, String genre,
                                 String charactersJson, List<String> failures) {

        public boolean success() {
            return failures.isEmpty();
        }
    }

    /** 故事梗概+题材标签的合成产物（包内可见，供单测断言） */
    record StoryMeta(String storySynopsis, String genre) {
    }

    /**
     * 依次合成故事梗概与人物表。
     *
     * @param episodes   全部分集摘要（含原文兜底集，概要可为 null）
     * @param characters 全部出场角色统计（按出现顺序）
     */
    public MetadataResult synthesize(ChatModel chatModel,
                                     List<EpisodeDigest> episodes,
                                     Collection<CharacterStat> characters) {
        List<String> failures = new ArrayList<>();

        StoryMeta story = synthesizeStory(chatModel, episodes);
        if (story == null) {
            failures.add("故事梗概");
        }

        String charactersJson = synthesizeCharacters(chatModel,
                story == null ? null : story.storySynopsis(), characters);
        if (charactersJson == null) {
            failures.add("人物表");
        }

        return new MetadataResult(
                story == null ? null : story.storySynopsis(),
                story == null ? null : story.genre(),
                charactersJson,
                List.copyOf(failures));
    }

    // ========== 调用 1：故事梗概 + 题材标签 ==========

    /**
     * 合成故事梗概与题材标签：输入不超过 {@link #SYNTHESIS_INPUT_MAX_CHARS} 时
     * 单次调用；超长时按行分批调用得到分段梗概，再一次合并调用产出最终结果。
     * 任一调用重试一次后仍不合规即返回 null，由调用方显式降级。
     */
    private StoryMeta synthesizeStory(ChatModel chatModel, List<EpisodeDigest> episodes) {
        List<String> lines = new ArrayList<>();
        int index = 0;
        for (EpisodeDigest digest : episodes) {
            index += 1;
            String title = digest.title() == null || digest.title().isBlank()
                    ? "第" + index + "集" : digest.title();
            String line = "第" + index + "集《" + title + "》";
            if (digest.synopsis() != null && !digest.synopsis().isBlank()) {
                line += "：" + digest.synopsis();
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }

        List<String> batches = splitByBudget(lines, SYNTHESIS_INPUT_MAX_CHARS);
        if (batches.size() == 1) {
            return callForStory(chatModel, ScriptFinalizePrompts.storyUserMessage(batches.get(0)));
        }
        // 超长输入：先分批总结，再一次合并调用产出最终结果
        List<String> partSummaries = new ArrayList<>();
        for (String batch : batches) {
            StoryMeta part = callForStory(chatModel, ScriptFinalizePrompts.storyUserMessage(batch));
            if (part == null) {
                return null;
            }
            partSummaries.add("分段梗概" + (partSummaries.size() + 1)
                    + "（题材：" + part.genre() + "）：" + part.storySynopsis());
        }
        return callForStory(chatModel,
                ScriptFinalizePrompts.storyMergeMessage(String.join("\n", partSummaries)));
    }

    /** 单次故事梗概调用：输出不合规时携带修复指令重试一次，仍失败返回 null */
    private StoryMeta callForStory(ChatModel chatModel, String userMessage) {
        StoryMeta meta = parseStoryMeta(callOnce(chatModel,
                ScriptFinalizePrompts.STORY_SYSTEM_PROMPT, userMessage));
        if (meta != null) {
            return meta;
        }
        log.warn("[AutoSplit] 故事梗概合成输出不合规，携带修复指令重试一次");
        return parseStoryMeta(callOnce(chatModel,
                ScriptFinalizePrompts.STORY_SYSTEM_PROMPT,
                ScriptFinalizePrompts.repairMessage(userMessage, "未输出符合格式的 storySynopsis/genre JSON")));
    }

    /** 解析故事梗概输出：storySynopsis 与 genre 均非空才有效，梗概截断到 300 字 */
    static StoryMeta parseStoryMeta(String modelText) {
        JSONObject json;
        try {
            json = ScriptChunkValidator.extractJsonObject(modelText);
        } catch (RuntimeException e) {
            return null;
        }
        String synopsis = trimToNull(json.getStr("storySynopsis"));
        String genre = trimToNull(json.getStr("genre"));
        if (synopsis == null || genre == null) {
            return null;
        }
        return new StoryMeta(truncate(synopsis, SYNOPSIS_MAX_CHARS), genre);
    }

    // ========== 调用 2：人物表 ==========

    /**
     * 合成人物表：输入为角色出场统计（名字+频次+对白样例，超预算后只保留名字以
     * 控制总量）。角色为空时无需调用，直接返回空表。重试一次仍失败返回 null。
     */
    private String synthesizeCharacters(ChatModel chatModel,
                                        String storySynopsis,
                                        Collection<CharacterStat> characters) {
        if (characters.isEmpty()) {
            return "[]";
        }
        // 剧本简介前缀与固定标头先计入输入预算，角色统计只在剩余预算内构造，
        // 保证最终用户消息整体不超过 SYNTHESIS_INPUT_MAX_CHARS
        int reservedPrefixChars = ScriptFinalizePrompts
                .characterUserMessage(storySynopsis, "").length();
        String userMessage = ScriptFinalizePrompts.characterUserMessage(
                storySynopsis, buildCharacterStatsInput(characters, reservedPrefixChars));
        return callForCharacters(chatModel, userMessage);
    }

    /** 单次人物表调用：输出不合规时携带修复指令重试一次，仍失败返回 null */
    private String callForCharacters(ChatModel chatModel, String userMessage) {
        String charactersJson = parseCharactersJson(callOnce(chatModel,
                ScriptFinalizePrompts.CHARACTER_SYSTEM_PROMPT, userMessage));
        if (charactersJson != null) {
            return charactersJson;
        }
        log.warn("[AutoSplit] 人物表合成输出不合规，携带修复指令重试一次");
        return parseCharactersJson(callOnce(chatModel,
                ScriptFinalizePrompts.CHARACTER_SYSTEM_PROMPT,
                ScriptFinalizePrompts.repairMessage(userMessage, "未输出符合格式的 characters JSON 数组")));
    }

    /**
     * 构造角色统计输入：逐行「- 名字（出现N次）对白样例：…」，与剧本简介前缀共享
     * {@link #SYNTHESIS_INPUT_MAX_CHARS} 预算——样例超出剩余预算时退化为仅名字+频次，
     * 名字行也放不进剩余预算（预算耗尽）后停止追加，保留已入的部分，
     * 确保「前缀 + 角色统计」整体不超过输入上限。
     *
     * @param reservedPrefixChars 剧本简介前缀与固定标头已占用的字符数
     */
    static String buildCharacterStatsInput(Collection<CharacterStat> characters,
                                           int reservedPrefixChars) {
        // 固定标头“角色出场统计：\n”与 reservedPrefixChars 一起先行扣除，预算不为负
        int budget = SYNTHESIS_INPUT_MAX_CHARS
                - Math.max(0, reservedPrefixChars)
                - CHARACTER_STATS_HEADER_CHARS;
        StringBuilder stats = new StringBuilder();
        for (CharacterStat stat : characters) {
            String line = characterStatLine(stat, true);
            if (line.length() > budget) {
                // 样例超出剩余预算：退化为仅名字+频次
                line = characterStatLine(stat, false);
            }
            // 预算耗尽：停止追加名字行（保留已入部分），不产生负预算
            if (line.length() > budget) {
                break;
            }
            if (stats.length() > 0) {
                stats.append('\n');
                budget -= 1;
            }
            budget -= line.length();
            stats.append(line);
        }
        return stats.toString();
    }

    /** 兼容旧调用的重载：无预留前缀（保留供单测与外部直查使用） */
    static String buildCharacterStatsInput(Collection<CharacterStat> characters) {
        return buildCharacterStatsInput(characters, 0);
    }

    /** 角色统计单行：withSamples=false 时只保留名字与频次 */
    private static String characterStatLine(CharacterStat stat, boolean withSamples) {
        String line = "- " + stat.getName() + "（出现" + stat.getCount() + "次）";
        if (withSamples && !stat.getSamples().isEmpty()) {
            StringBuilder samples = new StringBuilder();
            for (String sample : stat.getSamples()) {
                samples.append(samples.isEmpty() ? "" : "；").append("“").append(sample).append("”");
            }
            line += "对白样例：" + samples;
        }
        return line;
    }

    /**
     * 解析人物表输出并归一化为标准 schema [{name, importance, description}]：
     * 无 name 的条目剔除、name 去重、importance 钳制到合法枚举（非法时归一为配角）、
     * description 截断；characters 缺失或归一化后为空即无效。
     */
    static String parseCharactersJson(String modelText) {
        JSONObject json;
        try {
            json = ScriptChunkValidator.extractJsonObject(modelText);
        } catch (RuntimeException e) {
            return null;
        }
        Object raw = json.get("characters");
        if (!(raw instanceof JSONArray rawArray) || rawArray.isEmpty()) {
            return null;
        }
        JSONArray characters = new JSONArray();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Object item : rawArray) {
            if (!(item instanceof JSONObject character)) {
                continue;
            }
            String name = trimToNull(character.getStr("name"));
            if (name == null || seen.containsKey(name)) {
                continue;
            }
            seen.put(name, Boolean.TRUE);
            String importance = trimToNull(character.getStr("importance"));
            JSONObject normalized = new JSONObject();
            normalized.set("name", name);
            normalized.set("importance",
                    importance != null && IMPORTANCES.contains(importance) ? importance : DEFAULT_IMPORTANCE);
            String description = trimToNull(character.getStr("description"));
            if (description != null) {
                normalized.set("description", truncate(description, CHARACTER_DESCRIPTION_MAX_CHARS));
            }
            characters.add(normalized);
        }
        return characters.isEmpty() ? null : characters.toString();
    }

    // ========== 调用与文本处理辅助 ==========

    /** 调用一次纯 JSON 模型并返回文本 */
    private String callOnce(ChatModel chatModel, String systemPrompt, String userMessage) {
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage))));
        return response.getResult().getOutput().getText();
    }

    /** 按输入预算把行聚合为若干批：单行超预算时独立成批，保证不丢行 */
    static List<String> splitByBudget(List<String> lines, int budget) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (!current.isEmpty() && current.length() + line.length() + 1 > budget) {
                batches.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            batches.add(current.toString());
        }
        return batches;
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

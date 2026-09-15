package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动分块解析的模型输出校验与归一化（纯函数，便于单测）。
 * <p>
 * 校验规则：JSON 可解析、scenes 为非空数组、每一场都有非空 sceneHeading；
 * 原文含对白引号（「」『』“”‘’）但输出没有任何 type=1 对白时判为对白漏抽。
 * 以上为硬性问题，任一不满足即返回问题清单，供调用方带着修复指令重试一次，
 * 重试仍不通过则整块判失败（上层对半再切/原文兜底）。
 * 软性问题（type=1 对白缺说话人、标头缺内景/外景前缀）同样进入问题清单触发
 * 修复重试，但重试后仍存在时随归一化结果一并交付，由调用方接受现状，
 * 不做无限重试，也不做兜底改写。
 * 归一化规则：dialogues 统一为与 Agent 链路一致的结构
 * {type:整数, character_name, content, parenthetical, sortOrder}，
 * 出场角色从对白讲者去重推导（不额外要求模型输出 characters，降低小模型漂移），
 * sceneHeading 经 {@link HeadingNormalizer} 清洗后落库。
 */
final class ScriptChunkValidator {

    /** 原文对白引号：块原文包含任一即视为含对白（用于对白漏抽校验，避免误伤纯叙事块） */
    private static final String DIALOGUE_QUOTES = "「」『』“”‘’";

    /**
     * 校验+归一化结果：valid=false 时 problems 携带可直接回传给模型的修复要点；
     * valid=true 且 problems 非空表示软性问题（对白缺说话人/标头缺景别前缀），
     * 归一化结果仍可交付，供调用方在修复重试仍不通过时接受现状。
     */
    record Result(boolean valid, List<String> problems, JSONObject normalized) {

        static Result ok(JSONObject normalized) {
            return new Result(true, List.of(), normalized);
        }

        /** 软性问题交付：携带修复要点与归一化结果，重试仍存在时接受现状 */
        static Result acceptable(List<String> problems, JSONObject normalized) {
            return new Result(true, problems, normalized);
        }

        static Result fail(List<String> problems) {
            return new Result(false, problems, null);
        }
    }

    private ScriptChunkValidator() {
    }

    /** 解析并校验模型输出（不带原文，跳过对白漏抽校验）；通过校验时返回归一化后的 JSON */
    static Result parseAndNormalize(String modelText) {
        return parseAndNormalize(modelText, null);
    }

    /**
     * 解析并校验模型输出；sourceText 为块原文，用于对白漏抽校验：
     * 原文含对白引号但输出场次中没有任何 type=1 对白时判为无效，
     * 触发调用方带着「原文中有对白未提取」修复指令重试。
     */
    static Result parseAndNormalize(String modelText, String sourceText) {
        if (modelText == null || modelText.isBlank()) {
            return Result.fail(List.of("模型返回为空"));
        }
        JSONObject json;
        try {
            json = extractJsonObject(modelText);
        } catch (BusinessException e) {
            return Result.fail(List.of(e.getMessage()));
        }
        List<String> problems = findProblems(json);
        if (!problems.isEmpty()) {
            return Result.fail(problems);
        }
        JSONObject normalized = normalize(json);
        String missingDialogueProblem = findMissingDialogueProblem(normalized, sourceText);
        if (missingDialogueProblem != null) {
            return Result.fail(List.of(missingDialogueProblem));
        }
        List<String> acceptableProblems = findAcceptableProblems(normalized);
        if (!acceptableProblems.isEmpty()) {
            return Result.acceptable(acceptableProblems, normalized);
        }
        return Result.ok(normalized);
    }

    /**
     * 对白漏抽校验：只有原文确实含对白引号才触发，避免误伤真·纯叙事块；
     * 归一化后统计（兼容 type 缺失时按讲者推断为对白），行为 0 才判失败。
     *
     * @return 问题描述；无问题时返回 null
     */
    static String findMissingDialogueProblem(JSONObject normalized, String sourceText) {
        if (sourceText == null || !containsDialogueQuote(sourceText)) {
            return null;
        }
        JSONArray scenes = normalized.getJSONArray("scenes");
        if (scenes == null) {
            return null;
        }
        for (Object obj : scenes) {
            if (!(obj instanceof JSONObject scene)) {
                continue;
            }
            JSONArray dialogues = scene.getJSONArray("dialogues");
            if (dialogues == null) {
                continue;
            }
            for (Object dialogueObj : dialogues) {
                if (dialogueObj instanceof JSONObject dialogue
                        && Integer.valueOf(1).equals(dialogue.getInt("type", 0))) {
                    return null;
                }
            }
        }
        return "原文中有对白未提取：原文含对白引号但输出中没有任何 type=1 对白，"
                + "请把引号内的台词逐条抽取为 type=1 对白并标注 character_name";
    }

    /**
     * 软性校验问题清单：type=1 对白缺说话人、标头缺内景/外景前缀。
     * 与硬性校验不同，重试一次后仍存在时由调用方接受现状，不整块判失败。
     * 与讲者推断协同：归一化时 type 缺失但有讲者的条目已推断为 type=1 且带讲者，
     * 因此能触发缺说话人规则的只有模型显式输出 type=1 却未给讲者的条目；
     * {@link HeadingNormalizer} 只做清洗不补写景别前缀，缺前缀的标头原样交付待模型自修。
     */
    static List<String> findAcceptableProblems(JSONObject normalized) {
        List<String> problems = new ArrayList<>();
        JSONArray scenes = normalized.getJSONArray("scenes");
        if (scenes == null) {
            return problems;
        }
        int index = 0;
        for (Object obj : scenes) {
            index += 1;
            if (!(obj instanceof JSONObject scene)) {
                continue;
            }
            if (hasDialogueMissingSpeaker(scene.getJSONArray("dialogues"))) {
                problems.add("第" + index + "场有对白缺少说话人");
            }
            // 硬性校验已保证标头非空，HeadingNormalizer 清洗不改变景别前缀
            String heading = scene.getStr("sceneHeading");
            if (!heading.startsWith("内景") && !heading.startsWith("外景")) {
                problems.add("场次" + index + "标头缺少内景/外景前缀");
            }
        }
        return problems;
    }

    /** 该场是否存在 type=1 却缺说话人的对白条目 */
    private static boolean hasDialogueMissingSpeaker(JSONArray dialogues) {
        if (dialogues == null) {
            return false;
        }
        for (Object obj : dialogues) {
            if (obj instanceof JSONObject dialogue
                    && Integer.valueOf(1).equals(dialogue.getInt("type", 0))
                    && dialogue.getStr("character_name", "").isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** 原文是否包含任一对白引号 */
    static boolean containsDialogueQuote(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (DIALOGUE_QUOTES.indexOf(text.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从模型文本中提取 JSON 对象：容忍代码块标记与前后缀文本。
     * 原 {@code ScriptAutoSplitService#extractJsonObject} 的纯函数版本，
     * 非法 JSON 统一转成 BusinessException 交由上层转问题清单。
     */
    static JSONObject extractJsonObject(String text) {
        if (text == null) {
            throw new BusinessException("模型返回为空");
        }
        String cleaned = text.strip()
                .replaceAll("^```(json)?", "")
                .replaceAll("```$", "")
                .strip();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("模型未返回 JSON");
        }
        try {
            return JSONUtil.parseObj(cleaned.substring(start, end + 1));
        } catch (RuntimeException e) {
            throw new BusinessException("模型返回的 JSON 无法解析");
        }
    }

    /** 校验问题清单：缺 scenes、scenes 空数组、单场缺 sceneHeading 等 */
    static List<String> findProblems(JSONObject json) {
        List<String> problems = new ArrayList<>();
        Object rawScenes = json.get("scenes");
        if (rawScenes == null) {
            problems.add("缺少 scenes 字段");
            return problems;
        }
        if (!(rawScenes instanceof JSONArray scenes)) {
            problems.add("scenes 不是数组");
            return problems;
        }
        if (scenes.isEmpty()) {
            problems.add("scenes 为空数组");
            return problems;
        }
        int index = 0;
        for (Object obj : scenes) {
            index += 1;
            if (!(obj instanceof JSONObject scene)) {
                problems.add("scenes 第" + index + "项不是对象");
                continue;
            }
            String heading = scene.getStr("sceneHeading");
            if (heading == null || heading.isBlank()) {
                problems.add("scenes 第" + index + "场缺少 sceneHeading");
            }
        }
        return problems;
    }

    /** 归一化整块输出：episodeTitle/episodeSynopsis 去空白，scenes 逐场归一化 */
    static JSONObject normalize(JSONObject json) {
        JSONArray scenes = new JSONArray();
        Object rawScenes = json.get("scenes");
        if (rawScenes instanceof JSONArray array) {
            for (Object obj : array) {
                if (obj instanceof JSONObject scene) {
                    scenes.add(normalizeScene(scene));
                }
            }
        }
        JSONObject normalized = new JSONObject();
        normalized.set("episodeTitle", trimToNull(json.getStr("episodeTitle")));
        normalized.set("episodeSynopsis", trimToNull(json.getStr("episodeSynopsis")));
        normalized.set("scenes", scenes);
        return normalized;
    }

    /** 归一化单场：标头清洗与去空白、描述限长、dialogues 结构化、characters 从讲者去重 */
    private static JSONObject normalizeScene(JSONObject scene) {
        JSONArray dialogues = normalizeDialogues(scene.get("dialogues"));
        JSONObject normalized = new JSONObject();
        // 标头经 HeadingNormalizer 清洗：时间词归一 + 去冗余后缀
        normalized.set("sceneHeading", HeadingNormalizer.normalize(scene.getStr("sceneHeading")));
        normalized.set("sceneDescription", truncate(scene.getStr("sceneDescription", "")));
        normalized.set("dialogues", dialogues);
        // 出场角色从对白/旁白讲者去重推导，避免小模型额外输出 characters 造成与 dialogues 漂移。
        // 口径说明（有意与 ScriptAutoSplitService.collectEpisodeDigestAndCharacters 的
        // 剧本级人物表不同）：场次级 characters 收录对白与旁白(type=3)讲者，
        // 便于前端按场次定位需要配音的角色；剧本级人物表只收对白(type=1)讲者，
        // 避免「旁白」污染角色卡。
        Set<String> characters = new LinkedHashSet<>();
        for (Object obj : dialogues) {
            JSONObject dialogue = (JSONObject) obj;
            String name = dialogue.getStr("character_name", "");
            int type = dialogue.getInt("type", 0);
            if (!name.isBlank() && (type == 1 || type == 3)) {
                characters.add(name.strip());
            }
        }
        JSONArray characterArray = new JSONArray();
        characters.forEach(characterArray::add);
        normalized.set("characters", characterArray);
        return normalized;
    }

    /**
     * 把模型输出的 dialogues 归一化为标准 DialogueElement 结构。
     * 兼容小模型的字段漂移：type 兼容数字与数字字符串，
     * 说话人兼容 character_name/character/speaker，文本兼容 content/line；
     * type 缺失或非法时按有无讲者推断：有讲者为对白(1)，否则为动作描写(2)。
     */
    static JSONArray normalizeDialogues(Object rawDialogues) {
        JSONArray dialogues = new JSONArray();
        if (!(rawDialogues instanceof JSONArray raw)) {
            return dialogues;
        }
        int order = 0;
        for (Object obj : raw) {
            if (!(obj instanceof JSONObject item)) {
                continue;
            }
            String content = firstNonBlank(item.getStr("content"), item.getStr("line"));
            if (content == null) {
                continue;
            }
            JSONObject dialogue = new JSONObject();
            dialogue.set("type", parseType(item));
            String character = firstNonBlank(
                    item.getStr("character_name"),
                    item.getStr("character"),
                    item.getStr("speaker"));
            if (character != null) {
                dialogue.set("character_name", character.strip());
            }
            dialogue.set("content", content.strip());
            String parenthetical = trimToNull(item.getStr("parenthetical"));
            if (parenthetical != null) {
                dialogue.set("parenthetical", parenthetical);
            }
            dialogue.set("sortOrder", order);
            order += 1;
            dialogues.add(dialogue);
        }
        return dialogues;
    }

    /** type 解析：数字或数字字符串直接取值；缺失或非法时按有无讲者推断 1/2 */
    private static int parseType(JSONObject item) {
        Object raw = item.get("type");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().strip());
            } catch (NumberFormatException ignored) {
                // 非数字字符串，落到下方按字段推断
            }
        }
        return firstNonBlank(
                item.getStr("character_name"),
                item.getStr("character"),
                item.getStr("speaker")) == null ? 2 : 1;
    }

    /** 描述限长：提示词已要求 300 字以内，此处做硬约束防止模型照抄原文 */
    private static String truncate(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.length() <= ScriptAutoSplitPrompts.DESCRIPTION_MAX_CHARS
                ? stripped
                : stripped.substring(0, ScriptAutoSplitPrompts.DESCRIPTION_MAX_CHARS);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

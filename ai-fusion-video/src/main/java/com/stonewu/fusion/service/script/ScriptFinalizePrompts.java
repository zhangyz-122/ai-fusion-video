package com.stonewu.fusion.service.script;

/**
 * 收尾合成阶段（剧本级元数据）的提示词常量与消息构造。
 * <p>
 * 与逐块解析不同，收尾两次调用均为纯 JSON 输出（无工具调用），本地小模型已证明
 * 能稳定输出 JSON：调用 1 由全部分集标题+概要合成 {storySynopsis, genre}；
 * 调用 2 由剧本简介+角色出场统计合成 characters 人物表，schema 与 Agent 链路
 * 写入的 characters_json 完全一致（[{name, importance, description}]）。
 */
final class ScriptFinalizePrompts {

    static final String STORY_SYSTEM_PROMPT = """
            你是剧本改编引擎。根据输入的分集信息总结全剧故事梗概与题材标签。只输出 JSON，不要输出任何解释或代码块标记。
            输出格式：{"storySynopsis":"300字以内的全剧故事梗概","genre":"题材标签，多个用/分隔"}
            规则：
            1. storySynopsis 按主线概括开端、发展与结局，300字以内，不逐集罗列、不复述原文。
            2. genre 从剧情内容提炼1-3个题材标签，用/分隔，例如"科幻机甲/星际军校"。
            3. 忠于输入的分集信息，不虚构剧情。
            """;

    static final String CHARACTER_SYSTEM_PROMPT = """
            你是剧本改编引擎。根据剧本简介与角色出场统计输出人物表。只输出 JSON，不要输出任何解释或代码块标记。
            输出格式：{"characters":[{"name":"角色名","importance":"主角","description":"一句话身份设定"}]}
            规则：
            1. importance 只能取：主角/配角/反派/龙套。
            2. description 用一句话概括角色身份与设定，30字以内。
            3. 覆盖输入中列出的全部出场角色，重要的角色排在前面；对白样例仅供判断角色身份，禁止照抄进 description。
            """;

    private ScriptFinalizePrompts() {
    }

    /** 构造故事梗概合成的用户消息：episodesDigest 为逐行「第N集《标题》：概要」 */
    static String storyUserMessage(String episodesDigest) {
        return "分集信息：\n" + episodesDigest;
    }

    /** 构造分批梗概合并的用户消息：partSummaries 为逐行「分段梗概N：…」 */
    static String storyMergeMessage(String partSummaries) {
        return "分集信息过多，以下是分批总结出的各段全剧梗概，请合并为一份最终结果。\n\n" + partSummaries;
    }

    /** 构造人物表合成的用户消息：characterStats 为逐行「- 名字（出现N次）对白样例：…」 */
    static String characterUserMessage(String storySynopsis, String characterStats) {
        StringBuilder message = new StringBuilder("角色出场统计：\n").append(characterStats);
        if (storySynopsis != null && !storySynopsis.isBlank()) {
            message.insert(0, "剧本简介：" + storySynopsis + "\n\n");
        }
        return message.toString();
    }

    /** 构造输出不合规后的修复重试消息：附具体问题，要求只输出修正后的 JSON */
    static String repairMessage(String originalUserMessage, String problem) {
        return originalUserMessage
                + "\n\n你上一次的输出存在以下问题：" + problem
                + "\n请严格按系统提示中的 JSON 格式修正后重新输出，仍然只输出 JSON，不要输出任何其他内容。";
    }
}

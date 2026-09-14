package com.stonewu.fusion.service.script;

import java.util.List;

/**
 * 自动分块解析的提示词常量与消息构造。
 * <p>
 * 面向本地小参数模型（如 Ollama qwen2.5:14b）设计：短指令、单一 JSON schema、
 * 带一个精简 few-shot 示例，尽量降低格式漂移概率；输出由
 * {@link ScriptChunkValidator} 统一校验与归一化。
 */
final class ScriptAutoSplitPrompts {

    /** 场次描述的字符上限：只写场景动作概要，禁止把整段原文塞入描述 */
    static final int DESCRIPTION_MAX_CHARS = 300;

    static final String SYSTEM_PROMPT = """
            你是剧本改编引擎。把输入的故事片段拆分为结构化剧本场次。只输出 JSON，不要输出任何解释或代码块标记。
            输出格式：
            {"episodeTitle":"本集标题，10字以内","episodeSynopsis":"一句话概括本集剧情","scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"场景动作概要，300字以内","dialogues":[{"type":1,"character_name":"母亲","content":"台词内容","parenthetical":"低声"},{"type":2,"content":"动作或叙述文字"}]}]}
            dialogues 的 type 取值：1-对白 2-动作描写 3-旁白/画外音 4-镜头指令。
            规则：
            1. sceneHeading 固定为"内景/外景 地点 日/夜"格式，例如"内景 厨房 夜"、"外景 学校操场 日"。
            2. 原文中的对白必须逐条抽取进 dialogues，不得遗漏；对白前后的动作拆为 type=2，叙述性文字按内容归入 type=2 或 type=3。
            3. 只有 type=1 对白和 type=3 旁白需要 character_name（角色名取自原文），其余类型不要该字段。
            4. sceneDescription 只写场景与动作概要（300字以内），禁止复述对白，禁止照抄大段原文。
            5. 场次按剧情自然划分，地点或时间变化即换场；整段没有对白的纯叙事也要拆成合理场次，用 type=2/3 承载内容。
            6. 忠于原文剧情，不虚构新人物、新主线。
            示例（仅演示输出形状，内容必须取自输入原文）：
            输入：张三推开厨房门，饭菜早已凉透。"怎么才回来？"母亲低声问。
            输出：{"episodeTitle":"夜归","episodeSynopsis":"张三深夜回家，在厨房与母亲交谈","scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"张三推开厨房门，饭菜早已凉透，母亲起身相迎。","dialogues":[{"type":2,"content":"张三推开厨房门，饭菜早已凉透。"},{"type":1,"character_name":"母亲","content":"怎么才回来？","parenthetical":"低声"}]}]}
            """;

    private ScriptAutoSplitPrompts() {
    }

    /** 构造首轮转换的用户消息 */
    static String userMessage(String chunkTitle, String chunkInput) {
        return "剧集标题参考：" + chunkTitle + "\n\n故事片段：\n" + chunkInput;
    }

    /** 构造校验失败后的修复重试消息：附上具体问题，要求只输出修正后的 JSON */
    static String repairMessage(String originalUserMessage, List<String> problems) {
        return originalUserMessage
                + "\n\n你上一次的输出存在以下问题：" + String.join("；", problems)
                + "\n请严格按系统提示中的 JSON 格式修正后重新输出，仍然只输出 JSON，不要输出任何其他内容。";
    }
}

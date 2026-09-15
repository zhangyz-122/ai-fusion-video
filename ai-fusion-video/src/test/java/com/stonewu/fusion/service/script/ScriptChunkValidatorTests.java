package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ScriptChunkValidator} 校验与归一化纯函数的单测 */
class ScriptChunkValidatorTests {

    private static String validEpisodeJson() {
        return """
                {"episodeTitle":"夜归","episodeSynopsis":"张三深夜回家，在厨房与母亲交谈",\
                "scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"张三推开厨房门，饭菜早已凉透。",\
                "dialogues":[{"type":2,"content":"张三推开厨房门，饭菜早已凉透。"},\
                {"type":1,"character_name":"母亲","content":"怎么才回来？","parenthetical":"低声"}]}]}\
                """;
    }

    // ========== 正常解析与归一化 ==========

    @Test
    void parseAndNormalize_validOutput_returnsNormalizedJson() {
        ScriptChunkValidator.Result result =
                ScriptChunkValidator.parseAndNormalize(validEpisodeJson());

        assertThat(result.valid()).isTrue();
        JSONObject normalized = result.normalized();
        assertThat(normalized.getStr("episodeTitle")).isEqualTo("夜归");
        assertThat(normalized.getStr("episodeSynopsis")).contains("张三深夜回家");

        JSONArray scenes = normalized.getJSONArray("scenes");
        assertThat(scenes).hasSize(1);
        JSONObject scene = scenes.getJSONObject(0);
        assertThat(scene.getStr("sceneHeading")).isEqualTo("内景 厨房 夜");
        assertThat(scene.getStr("sceneDescription")).contains("张三推开厨房门");
    }

    @Test
    void parseAndNormalize_fencedCodeBlockAndSurroundingText_extractsJson() {
        String text = "好的，以下是解析结果：\n```json\n" + validEpisodeJson() + "\n```\n以上。";

        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize(text);

        assertThat(result.valid()).isTrue();
        assertThat(result.normalized().getStr("episodeTitle")).isEqualTo("夜归");
    }

    @Test
    void normalizeDialogues_coercesStringTypeAndSpeakerDrift() {
        // type 为数字字符串、说话人漂移到 speaker 字段、文本漂移到 line 字段
        String sceneJson = """
                {"sceneHeading":"内景 厨房 夜","sceneDescription":"描述",\
                "dialogues":[{"type":"1","speaker":"母亲","content":"怎么才回来？","parenthetical":"低声"},\
                {"type":2,"line":"张三推开厨房门。"},\
                {"content":"无类型但有讲者","character":"张三"}]}\
                """;
        JSONObject scene = new JSONObject(sceneJson);

        JSONArray dialogues = ScriptChunkValidator.normalizeDialogues(scene.get("dialogues"));

        assertThat(dialogues).hasSize(3);
        JSONObject first = dialogues.getJSONObject(0);
        assertThat(first.getInt("type")).isEqualTo(1);
        assertThat(first.getStr("character_name")).isEqualTo("母亲");
        assertThat(first.getStr("parenthetical")).isEqualTo("低声");
        assertThat(first.getInt("sortOrder")).isZero();

        JSONObject second = dialogues.getJSONObject(1);
        assertThat(second.getInt("type")).isEqualTo(2);
        assertThat(second.getStr("content")).isEqualTo("张三推开厨房门。");
        assertThat(second.containsKey("character_name")).isFalse();
        assertThat(second.getInt("sortOrder")).isEqualTo(1);

        // 缺失 type 时按有无讲者推断：有讲者为对白
        JSONObject third = dialogues.getJSONObject(2);
        assertThat(third.getInt("type")).isEqualTo(1);
        assertThat(third.getStr("character_name")).isEqualTo("张三");
    }

    @Test
    void parseAndNormalize_collectsDistinctCharactersFromDialogueSpeakers() {
        String text = """
                {"scenes":[\
                {"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[\
                {"type":1,"character_name":"母亲","content":"怎么才回来？"},\
                {"type":3,"character_name":"张三","content":"我回来了。"},\
                {"type":1,"character_name":"母亲","content":"吃饭吧"},\
                {"type":2,"content":"母亲端起碗。"}]}]}\
                """;

        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize(text);

        assertThat(result.valid()).isTrue();
        JSONArray characters = result.normalized().getJSONArray("scenes").getJSONObject(0)
                .getJSONArray("characters");
        // 按讲者去重且保持出现顺序，type=2 动作不产生角色
        assertThat(characters.toList(String.class)).containsExactly("母亲", "张三");
    }

    @Test
    void parseAndNormalize_blankContentDialogueAndLongDescription_normalized() {
        String longDescription = "描".repeat(400);
        String text = """
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"%s",\
                "dialogues":[{"type":1,"character_name":"母亲","content":"  "},\
                {"type":1,"character_name":"母亲","content":"怎么才回来？"}]}]}\
                """.formatted(longDescription);

        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize(text);

        assertThat(result.valid()).isTrue();
        JSONObject scene = result.normalized().getJSONArray("scenes").getJSONObject(0);
        // 描述硬约束在 300 字以内，防止模型照抄原文
        assertThat(scene.getStr("sceneDescription")).hasSize(
                ScriptAutoSplitPrompts.DESCRIPTION_MAX_CHARS);
        // 空白内容条目被剔除
        assertThat(scene.getJSONArray("dialogues")).hasSize(1);
    }

    @Test
    void parseAndNormalize_blankSynopsisAndTitle_normalizedToNull() {
        String text = """
                {"episodeTitle":"  ","episodeSynopsis":" ",\
                "scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[]}]}\
                """;

        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize(text);

        assertThat(result.valid()).isTrue();
        assertThat(result.normalized().getStr("episodeTitle")).isNull();
        assertThat(result.normalized().getStr("episodeSynopsis")).isNull();
    }

    // ========== 校验失败（触发重试）==========

    @Test
    void parseAndNormalize_blankText_reportsProblem() {
        assertThat(ScriptChunkValidator.parseAndNormalize(null).valid()).isFalse();
        assertThat(ScriptChunkValidator.parseAndNormalize("   ").valid()).isFalse();
        assertThat(ScriptChunkValidator.parseAndNormalize(null).problems()).containsExactly("模型返回为空");
    }

    @Test
    void parseAndNormalize_nonJsonText_reportsProblem() {
        ScriptChunkValidator.Result result =
                ScriptChunkValidator.parseAndNormalize("抱歉，我无法完成这个任务。");

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("模型未返回 JSON");
    }

    @Test
    void parseAndNormalize_brokenJson_reportsProblem() {
        // 括号不配对：hutool 宽松解析也无法还原为合法 JSON
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize(
                "{\"scenes\": [{\"sceneHeading\": \"内景 厨房 夜\"}");

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("模型返回的 JSON 无法解析");
    }

    @Test
    void parseAndNormalize_missingScenes_reportsProblem() {
        ScriptChunkValidator.Result result =
                ScriptChunkValidator.parseAndNormalize("{\"episodeTitle\":\"夜归\"}");

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("缺少 scenes 字段");
    }

    @Test
    void parseAndNormalize_emptyScenes_reportsProblem() {
        ScriptChunkValidator.Result result =
                ScriptChunkValidator.parseAndNormalize("{\"scenes\":[]}");

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("scenes 为空数组");
    }

    @Test
    void parseAndNormalize_sceneWithoutHeading_reportsProblemWithIndex() {
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[\
                {"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[]},\
                {"sceneDescription":"缺标头","dialogues":[]}]}\
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("scenes 第2场缺少 sceneHeading");
    }

    // ========== 对白漏抽校验（源文含引号但输出无 type=1 对白）==========

    @Test
    void parseAndNormalize_sourceHasQuotesButNoDialogue_reportsMissingDialogue() {
        String source = "张三推开厨房门。「怎么才回来？」母亲低声问。";

        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"只有动作","dialogues":[\
                {"type":2,"content":"张三推开厨房门，母亲低声问。"}]}]}\
                """, source);

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0))
                .contains("原文中有对白未提取")
                .contains("type=1");
    }

    @Test
    void parseAndNormalize_sourceWithAllQuoteStyles_triggersMissingDialogueCheck() {
        String output = """
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[\
                {"type":2,"content":"叙述"}]}]}\
                """;
        // 『』与 ‘’ 引号同样触发
        assertThat(ScriptChunkValidator.parseAndNormalize(output, "『进来吧。』他说").valid()).isFalse();
        assertThat(ScriptChunkValidator.parseAndNormalize(output, "她低声说‘好’。").valid()).isFalse();
        assertThat(ScriptChunkValidator.parseAndNormalize(output, "他喊道“站住”！").valid()).isFalse();
    }

    @Test
    void parseAndNormalize_sourceHasQuotesAndDialogueExtracted_passes() {
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[\
                {"type":1,"character_name":"母亲","content":"怎么才回来？"}]}]}\
                """, "「怎么才回来？」母亲低声问。");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void parseAndNormalize_dialogueTypeMissingButSpeakerPresent_passes() {
        // type 缺失但带讲者，归一化推断为 type=1，不误报对白漏抽
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[\
                {"character_name":"母亲","content":"怎么才回来？"}]}]}\
                """, "「怎么才回来？」母亲低声问。");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void parseAndNormalize_pureNarrativeSourceWithoutQuotes_passes() {
        // 真·纯叙事块：原文没有对白引号，输出全为动作/旁白也不判失败
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[{"sceneHeading":"外景 山顶 日","sceneDescription":"纯叙事","dialogues":[\
                {"type":2,"content":"他独自站在山顶。"},\
                {"type":3,"character_name":"旁白","content":"多年以后。"}]}]}\
                """, "他独自站在山顶。多年以后，人们仍记得这一天。");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void parseAndNormalize_withoutSourceText_skipsMissingDialogueCheck() {
        // 单参重载不带原文，跳过对白漏抽校验（兼容旧调用）
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"描述","dialogues":[\
                {"type":2,"content":"叙述"}]}]}\
                """);

        assertThat(result.valid()).isTrue();
    }

    // ========== 标头清洗（落库前经 HeadingNormalizer）==========

    @Test
    void parseAndNormalize_cleansNoisySceneHeading() {
        ScriptChunkValidator.Result result = ScriptChunkValidator.parseAndNormalize("""
                {"scenes":[\
                {"sceneHeading":"内景 103室 室","sceneDescription":"a","dialogues":[]},\
                {"sceneHeading":"外景 学校操场 白天","sceneDescription":"b","dialogues":[]}]}\
                """);

        assertThat(result.valid()).isTrue();
        JSONArray scenes = result.normalized().getJSONArray("scenes");
        assertThat(scenes.getJSONObject(0).getStr("sceneHeading")).isEqualTo("内景 103室");
        assertThat(scenes.getJSONObject(1).getStr("sceneHeading")).isEqualTo("外景 学校操场 日");
    }
}

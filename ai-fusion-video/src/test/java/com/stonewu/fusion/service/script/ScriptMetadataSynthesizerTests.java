package com.stonewu.fusion.service.script;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.stonewu.fusion.service.script.ScriptMetadataSynthesizer.CharacterStat;
import com.stonewu.fusion.service.script.ScriptMetadataSynthesizer.EpisodeDigest;
import com.stonewu.fusion.service.script.ScriptMetadataSynthesizer.MetadataResult;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link ScriptMetadataSynthesizer} 收尾合成（分批/合并/重试/降级）的单测 */
class ScriptMetadataSynthesizerTests {

    private final ScriptMetadataSynthesizer synthesizer = new ScriptMetadataSynthesizer();

    private static EpisodeDigest digest(String title, String synopsis) {
        return new EpisodeDigest(title, synopsis);
    }

    private static CharacterStat stat(String name, int count, String... samples) {
        CharacterStat characterStat = new CharacterStat(name);
        for (int i = 0; i < count; i++) {
            // 未显式给出样例的次数传 null：仅累计频次，不保留样例
            characterStat.record(i < samples.length ? samples[i] : null);
        }
        return characterStat;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static String systemPromptOf(Prompt prompt) {
        return ((SystemMessage) prompt.getInstructions().get(0)).getText();
    }

    private static String userPromptOf(Prompt prompt) {
        return prompt.getInstructions().get(1).getText();
    }

    // ========== 正常合成：两次纯 JSON 调用 ==========

    @Test
    void synthesize_smallInput_callsStoryAndCharacterOnceEach() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(
                        "{\"storySynopsis\":\"张三深夜回家与母亲和解\",\"genre\":\"家庭/剧情\"}"))
                .thenReturn(response(
                        "{\"characters\":[{\"name\":\"母亲\",\"importance\":\"主角\",\"description\":\"张三的母亲\"},"
                                + "{\"name\":\"张三\",\"importance\":\"主要\",\"description\":\"夜归的青年\"}]}"));

        MetadataResult result = synthesizer.synthesize(chatModel,
                List.of(digest("夜归", "张三深夜回家")),
                List.of(stat("母亲", 3, "怎么才回来？"), stat("张三", 2, "我回来了。")));

        assertThat(result.success()).isTrue();
        assertThat(result.storySynopsis()).isEqualTo("张三深夜回家与母亲和解");
        assertThat(result.genre()).isEqualTo("家庭/剧情");
        // 人物表归一化为标准 schema：importance 漂移值钳制为配角
        List<JSONObject> characters = JSONUtil.parseArray(result.charactersJson())
                .toList(JSONObject.class);
        assertThat(characters).hasSize(2);
        assertThat(characters.get(0).getStr("name")).isEqualTo("母亲");
        assertThat(characters.get(0).getStr("importance")).isEqualTo("主角");
        assertThat(characters.get(0).getStr("description")).isEqualTo("张三的母亲");
        assertThat(characters.get(1).getStr("importance")).isEqualTo("配角");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();
        assertThat(systemPromptOf(prompts.get(0))).contains("storySynopsis");
        assertThat(userPromptOf(prompts.get(0))).contains("第1集《夜归》：张三深夜回家");
        assertThat(systemPromptOf(prompts.get(1))).contains("characters");
        assertThat(userPromptOf(prompts.get(1)))
                .contains("剧本简介：张三深夜回家与母亲和解")
                .contains("- 母亲（出现3次）对白样例：“怎么才回来？”");
    }

    @Test
    void synthesize_withoutCharacters_returnsEmptyTableWithoutModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"storySynopsis\":\"梗概\",\"genre\":\"悬疑\"}"));

        MetadataResult result = synthesizer.synthesize(chatModel,
                List.of(digest("夜归", null)), List.of());

        assertThat(result.success()).isTrue();
        assertThat(result.charactersJson()).isEqualTo("[]");
        // 无角色时跳过人物表调用，仅故事梗概一次调用
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    // ========== 分批与合并 ==========

    @Test
    void synthesize_oversizedEpisodeInput_batchesThenMerges() {
        // 60 个约 800 字的概要（共约 4.9 万字符）超过 3 万阈值 → 分批总结后合并
        List<EpisodeDigest> episodes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            episodes.add(digest("第" + i + "集", ("剧情" + i).repeat(200)));
        }
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"storySynopsis\":\"上半段梗概\",\"genre\":\"科幻\"}"))
                .thenReturn(response("{\"storySynopsis\":\"下半段梗概\",\"genre\":\"机甲\"}"))
                .thenReturn(response("{\"storySynopsis\":\"合并后的全剧梗概\",\"genre\":\"科幻/机甲\"}"))
                .thenReturn(response("{\"characters\":[{\"name\":\"母亲\",\"importance\":\"主角\"}]}"));

        MetadataResult result = synthesizer.synthesize(chatModel, episodes,
                List.of(stat("母亲", 1, "台词")));

        assertThat(result.success()).isTrue();
        assertThat(result.storySynopsis()).isEqualTo("合并后的全剧梗概");
        assertThat(result.genre()).isEqualTo("科幻/机甲");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(4)).call(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();
        // 前两次为分批总结，第三次为合并调用；合并输入携带各段梗概
        assertThat(userPromptOf(prompts.get(0))).contains("分集信息：");
        assertThat(userPromptOf(prompts.get(1))).contains("分集信息：");
        assertThat(userPromptOf(prompts.get(2))).contains("请合并为一份最终结果")
                .contains("分段梗概1（题材：科幻）：上半段梗概")
                .contains("分段梗概2（题材：机甲）：下半段梗概");
        for (Prompt prompt : prompts.subList(0, 2)) {
            assertThat(userPromptOf(prompt).length())
                    .isLessThanOrEqualTo(ScriptMetadataSynthesizer.SYNTHESIS_INPUT_MAX_CHARS + 200);
        }
    }

    // ========== 输出不合规：重试一次 + 显式降级 ==========

    @Test
    void synthesize_storyInvalidTwice_degradesAndStillSynthesizesCharacters() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("抱歉，我无法完成这个任务。"))
                .thenReturn(response("{\"storySynopsis\":\"只有梗概没有题材\"}"))
                .thenReturn(response("{\"characters\":[{\"name\":\"母亲\",\"importance\":\"主角\"}]}"));

        MetadataResult result = synthesizer.synthesize(chatModel,
                List.of(digest("夜归", "概要")), List.of(stat("母亲", 1, "台词")));

        // 故事梗概重试一次仍缺 genre → 显式降级；人物表仍正常合成
        assertThat(result.success()).isFalse();
        assertThat(result.failures()).containsExactly("故事梗概");
        assertThat(result.storySynopsis()).isNull();
        assertThat(result.charactersJson()).isNotNull();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).call(promptCaptor.capture());
        // 第二次调用携带修复指令
        assertThat(userPromptOf(promptCaptor.getAllValues().get(1))).contains("修正");
        // 人物表调用在梗概缺失时仍构造（不带剧本简介）
        assertThat(userPromptOf(promptCaptor.getAllValues().get(2)))
                .doesNotContain("剧本简介：")
                .contains("- 母亲（出现1次）");
    }

    @Test
    void synthesize_charactersInvalidTwice_degradesButKeepsStory() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"storySynopsis\":\"全剧梗概\",\"genre\":\"悬疑\"}"))
                .thenReturn(response("不是 JSON"))
                .thenReturn(response("{\"characters\":[]}"));

        MetadataResult result = synthesizer.synthesize(chatModel,
                List.of(digest("夜归", "概要")), List.of(stat("母亲", 1, "台词")));

        assertThat(result.success()).isFalse();
        assertThat(result.failures()).containsExactly("人物表");
        assertThat(result.storySynopsis()).isEqualTo("全剧梗概");
        assertThat(result.genre()).isEqualTo("悬疑");
        assertThat(result.charactersJson()).isNull();
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    // ========== 解析归一化纯函数 ==========

    @Test
    void parseCharactersJson_normalizesSchemaAndDeduplicates() {
        String json = ScriptMetadataSynthesizer.parseCharactersJson("""
                {"characters":[\
                {"name":"母亲","importance":"主角","description":"张三的母亲"},\
                {"name":"母亲","importance":"配角","description":"重复条目"},\
                {"name":"  ","importance":"配角","description":"无名字"},\
                {"name":"邻居","importance":"龙套","description":"%s"},\
                {"name":"路人","description":"缺 importance"}]}\
                """.formatted("描".repeat(70)));

        assertThat(json).isNotNull();
        List<JSONObject> characters = JSONUtil.parseArray(json).toList(JSONObject.class);
        assertThat(characters).hasSize(3);
        assertThat(characters.get(0).getStr("name")).isEqualTo("母亲");
        assertThat(characters.get(0).getStr("importance")).isEqualTo("主角");
        // 重复名字剔除、description 截断、importance 缺失归一为配角
        assertThat(characters.get(1).getStr("name")).isEqualTo("邻居");
        assertThat(characters.get(1).getStr("importance")).isEqualTo("龙套");
        assertThat(characters.get(1).getStr("description"))
                .hasSize(ScriptMetadataSynthesizer.CHARACTER_DESCRIPTION_MAX_CHARS);
        assertThat(characters.get(2).getStr("name")).isEqualTo("路人");
        assertThat(characters.get(2).getStr("importance")).isEqualTo("配角");
    }

    @Test
    void parseCharactersJson_invalidOutputs_returnNull() {
        assertThat(ScriptMetadataSynthesizer.parseCharactersJson("不是 JSON")).isNull();
        assertThat(ScriptMetadataSynthesizer.parseCharactersJson("{\"storySynopsis\":\"x\"}")).isNull();
        assertThat(ScriptMetadataSynthesizer.parseCharactersJson("{\"characters\":[]}")).isNull();
        assertThat(ScriptMetadataSynthesizer.parseCharactersJson(
                "{\"characters\":[{\"importance\":\"主角\"}]}")).isNull();
    }

    @Test
    void parseStoryMeta_requiresBothFieldsAndTruncatesSynopsis() {
        assertThat(ScriptMetadataSynthesizer.parseStoryMeta(
                "{\"storySynopsis\":\"梗概\",\"genre\":\"悬疑\"}")).isNotNull();
        assertThat(ScriptMetadataSynthesizer.parseStoryMeta(
                "{\"storySynopsis\":\"只有梗概\",\"genre\":\" \"}")).isNull();
        assertThat(ScriptMetadataSynthesizer.parseStoryMeta(
                "{\"genre\":\"只有题材\"}")).isNull();

        var meta = ScriptMetadataSynthesizer.parseStoryMeta(
                "{\"storySynopsis\":\"%s\",\"genre\":\"悬疑\"}".formatted("剧".repeat(400)));
        assertThat(meta.storySynopsis()).hasSize(ScriptMetadataSynthesizer.SYNOPSIS_MAX_CHARS);
    }

    // ========== 输入构造 ==========

    @Test
    void buildCharacterStatsInput_truncatesSamplesAndControlsBudget() {
        List<CharacterStat> stats = List.of(
                stat("主角甲", 30, "长".repeat(80), "第二条"),
                stat("配角乙", 5));

        String input = ScriptMetadataSynthesizer.buildCharacterStatsInput(stats);

        // 样例单条截断到上限
        assertThat(input).contains(
                "“" + "长".repeat(ScriptMetadataSynthesizer.SAMPLE_DIALOGUE_MAX_CHARS) + "”");
        assertThat(input).contains("主角甲（出现30次）");
        // 无对白样例的角色只列名字与频次
        assertThat(input).contains("配角乙（出现5次）");
        assertThat(input).doesNotContain("配角乙（出现5次）对白样例");
    }

    @Test
    void buildCharacterStatsInput_stopsWhenBudgetExhaustedAndKeepsAcceptedPart() {
        // 每行名字+样例约 210 字：预算耗尽后停止追加名字行，已入部分保留且不产生负预算
        List<CharacterStat> stats = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            stats.add(stat("角色" + i, 3, "样".repeat(60), "样".repeat(60)));
        }

        String input = ScriptMetadataSynthesizer.buildCharacterStatsInput(stats);

        assertThat(input).isNotEmpty();
        assertThat(input.length()).isLessThanOrEqualTo(ScriptMetadataSynthesizer.SYNTHESIS_INPUT_MAX_CHARS);
        // 预算内的行完整保留：先有带样例的行，预算收窄后是退化后的整行名字行（无截断残行）
        assertThat(input).startsWith("- 角色0（出现3次）");
        assertThat(input).contains("对白样例：");
        assertThat(input).endsWith("（出现3次）");
    }

    @Test
    void buildCharacterStatsInput_thousandsOfCharactersStayWithinBudgetIncludingSynopsisPrefix() {
        // 300 字梗概前缀 + 固定标头先行计入预算，几千角色构造出的整体输入不超上限
        String synopsis = "梗".repeat(ScriptMetadataSynthesizer.SYNOPSIS_MAX_CHARS);
        int reservedPrefixChars = ScriptFinalizePrompts
                .characterUserMessage(synopsis, "").length();
        List<CharacterStat> stats = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            stats.add(stat("龙套" + i, 1, "对白样例".repeat(12), "第二条样例".repeat(8)));
        }

        String input = ScriptMetadataSynthesizer.buildCharacterStatsInput(stats, reservedPrefixChars);
        String userMessage = ScriptFinalizePrompts.characterUserMessage(synopsis, input);

        assertThat(userMessage.length())
                .as("简介前缀 + 角色统计整体不得超过单次合成输入上限")
                .isLessThanOrEqualTo(ScriptMetadataSynthesizer.SYNTHESIS_INPUT_MAX_CHARS);
        assertThat(input).isNotEmpty();
    }

    @Test
    void buildCharacterStatsInput_oversizedSingleNameLineDoesNotProduceNegativeBudget() {
        // 单个超长名字连“仅名字+频次”都放不进剩余预算：直接跳过且不破坏已有内容
        List<CharacterStat> stats = List.of(
                stat("甲", 1, "样"),
                stat("超".repeat(ScriptMetadataSynthesizer.SYNTHESIS_INPUT_MAX_CHARS), 1));

        String input = ScriptMetadataSynthesizer.buildCharacterStatsInput(stats);

        assertThat(input).isEqualTo("- 甲（出现1次）对白样例：“样”");
    }

    @Test
    void characterStat_recordsCountAndCapsSamples() {
        CharacterStat character = new CharacterStat("母亲");
        character.record("怎么才回来？");
        character.record("  ");
        character.record("吃饭吧");
        character.record("第三条不保留");

        assertThat(character.getCount()).isEqualTo(4);
        assertThat(character.getSamples()).containsExactly("怎么才回来？", "吃饭吧");
    }

    @Test
    void splitByBudget_batchesLinesWithoutLoss() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add("第" + i + "集" + "x".repeat(300));
        }
        List<String> batches = ScriptMetadataSynthesizer.splitByBudget(lines, 1000);

        assertThat(batches.size()).isGreaterThanOrEqualTo(3);
        for (String batch : batches) {
            assertThat(batch.length()).isLessThanOrEqualTo(1000);
        }
        // 行不丢、不重
        String all = String.join("", batches).replace("\n", "");
        assertThat(all).isEqualTo(String.join("", lines));
    }
}

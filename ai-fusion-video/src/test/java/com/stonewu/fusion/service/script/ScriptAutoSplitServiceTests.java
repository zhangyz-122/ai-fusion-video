package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AgentConversation;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.mapper.script.ScriptEpisodeMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import com.stonewu.fusion.mapper.script.ScriptSceneItemMapper;
import com.stonewu.fusion.service.ai.AgentConversationService;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.provider.AiProviderService;
import com.stonewu.fusion.service.task.TaskStreamService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ScriptAutoSplitServiceTests {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        // runAutoSplit 全链路会构造 Script/ScriptEpisode/ScriptSceneItem 的 Lambda Wrapper，
        // 需要提前初始化三张表的列缓存
        TableInfoHelper.initTableInfo(assistant, Script.class);
        TableInfoHelper.initTableInfo(assistant, ScriptEpisode.class);
        TableInfoHelper.initTableInfo(assistant, ScriptSceneItem.class);
    }

    private final ScriptMapper scriptMapper = mock(ScriptMapper.class);
    private final ScriptEpisodeMapper episodeMapper = mock(ScriptEpisodeMapper.class);
    private final ScriptSceneItemMapper sceneItemMapper = mock(ScriptSceneItemMapper.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final AiProviderService aiProviderService = mock(AiProviderService.class);
    private final TaskStreamService taskStreamService = mock(TaskStreamService.class);
    private final AgentConversationService conversationService = mock(AgentConversationService.class);
    private final CacheManager cacheManager = new ConcurrentMapCacheManager();

    private final ScriptAutoSplitService service = new ScriptAutoSplitService(
            scriptMapper, episodeMapper, sceneItemMapper,
            aiModelService, aiProviderService, taskStreamService, conversationService, cacheManager);

    private static String paragraph(int index, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append((char) ('a' + (index + i) % 26));
        }
        return builder.toString();
    }

    /** 生成 count 个段落，每段 paragraphLength 字符，段落间以换行分隔 */
    private static String paragraphs(int count, int paragraphLength) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(paragraph(i, paragraphLength));
        }
        return builder.toString();
    }

    /** 拼回全部分块并去除聚合时插入的空白分隔，仅保留字符内容，用于丢字校验 */
    private static String flatten(List<String> chunks) {
        return String.join("", chunks).replaceAll("\\s+", "");
    }

    private static String flatten(String text) {
        return text.replaceAll("\\s+", "");
    }

    // ========== 分块参数钳制 ==========

    @Test
    void normalizeChunkChars_clampsOutOfRangeValues() {
        assertThat(ScriptAutoSplitService.normalizeChunkChars(null)).isEqualTo(6000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(0)).isEqualTo(2000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(100)).isEqualTo(2000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(1999)).isEqualTo(2000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(2000)).isEqualTo(2000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(8000)).isEqualTo(8000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(12000)).isEqualTo(12000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(12001)).isEqualTo(12000);
        assertThat(ScriptAutoSplitService.normalizeChunkChars(999999)).isEqualTo(12000);
    }

    // ========== 章节切分与聚合 ==========

    @Test
    void splitIntoChunks_plainTextWithoutNewline_splitsIntoCharSizedChunks() {
        // 13000 字无换行：6000 + 6000 + 1000 → 3 块，任何块都不超过分块上限且不丢字
        String raw = paragraph(0, 13000);

        List<String> chunks = service.splitIntoChunks(raw);

        assertThat(chunks).hasSize(3);
        for (String chunk : chunks) {
            assertThat(chunk.strip().length()).isLessThanOrEqualTo(6000);
        }
        assertThat(flatten(chunks)).hasSize(13000).isEqualTo(flatten(raw));
    }

    @Test
    void splitIntoChunks_chapterText_aggregatesWholeChaptersInOrder() {
        // 每章约 2026 字（标题 + 20 段 × 100 字），默认 6000 上限下多章聚合为少量块
        String chapter1 = "第一章 起点\n" + paragraphs(20, 100) + "\n";
        String chapter2 = "第二章 转折\n" + paragraphs(21, 100) + "\n";
        String chapter3 = "第三章 结局\n" + paragraphs(22, 100);
        String raw = chapter1 + chapter2 + chapter3;

        List<String> chunks = service.splitIntoChunks(raw);

        // 6 个原子片段聚合为 2 块，而不是每段/每章一块
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).startsWith("第一章 起点");
        assertThat(chunks.get(0)).contains("第二章 转折");
        for (String chunk : chunks) {
            assertThat(chunk.strip().length()).isLessThanOrEqualTo(6000);
        }
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitIntoChunks_prefersParagraphBoundaryOverHardCut() {
        // 段落 100 字、共 70 段：6000 位置落在段内，应回退到段落边界，禁止截断残段
        String raw = paragraphs(70, 100);

        List<String> chunks = service.splitIntoChunks(raw);

        assertThat(chunks).hasSize(2);
        for (String chunk : chunks) {
            for (String line : chunk.strip().split("\n")) {
                // 每一行都是完整的 100 字段落，没有被硬切
                assertThat(line).hasSize(100);
            }
        }
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitIntoChunks_overlappingSmallParagraphs_respectsAggregationLimit() {
        // 100 个 100 字段落：相邻聚合后每块不超过默认上限，且块数远小于段落数
        String raw = paragraphs(100, 100);

        List<String> chunks = service.splitIntoChunks(raw);

        assertThat(chunks.size()).isBetween(2, 10);
        for (String chunk : chunks) {
            assertThat(chunk.strip().length()).isLessThanOrEqualTo(6000);
        }
        assertThat(flatten(chunks)).isEqualTo(flatten(raw));
    }

    @Test
    void splitIntoChunks_customChunkChars_isClampedAndApplied() {
        String raw = paragraph(0, 13000);

        // 低于下限钳制为 2000：13000 字 → 6×2000 + 1×1000 = 7 块
        List<String> smallChunks = service.splitIntoChunks(raw, 100);
        assertThat(smallChunks).hasSize(7);
        for (String chunk : smallChunks) {
            assertThat(chunk.strip().length()).isLessThanOrEqualTo(2000);
        }

        // 高于上限钳制为 12000：13000 字 → 12000 + 1000 = 2 块
        List<String> largeChunks = service.splitIntoChunks(raw, 999999);
        assertThat(largeChunks).hasSize(2);
        assertThat(largeChunks.get(0).strip().length()).isLessThanOrEqualTo(12000);

        // null 使用默认 6000，与单参重载一致
        assertThat(service.splitIntoChunks(raw, null)).isEqualTo(service.splitIntoChunks(raw));
    }

    @Test
    void splitIntoChunks_exceedingMaxChunkCount_throwsBusinessException() {
        // 401 个 2000 字段落，按 2000 分块恰好 401 块，超过 400 块安全上限
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 401; i++) {
            if (i > 0) {
                raw.append('\n');
            }
            raw.append(paragraph(i, 2000));
        }

        assertThatThrownBy(() -> service.splitIntoChunks(raw.toString(), 2000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("400");
    }

    // ========== 滞留恢复 ==========

    @Test
    void recoverStaleParsing_marksStaleScriptAsFailedWithRerunHint() {
        Script stale = new Script();
        stale.setId(5L);
        stale.setProjectId(9L);
        stale.setParsingStatus(1);
        stale.setUpdateTime(LocalDateTime.now().minusHours(2));
        when(scriptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stale));
        // 预热实体与分集列表缓存，验证状态更新后同步失效
        cacheManager.getCache("script").put(5L, stale);
        cacheManager.getCache("script").put("project:9", stale);
        cacheManager.getCache("episode").put("script:5", List.of());

        int recovered = service.recoverStaleParsing();

        assertThat(recovered).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper<Script>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(scriptMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(3, ScriptAutoSplitService.STALE_PARSING_MESSAGE);
        assertThat(cacheManager.getCache("script").get(5L)).isNull();
        assertThat(cacheManager.getCache("script").get("project:9")).isNull();
        assertThat(cacheManager.getCache("episode").get("script:5")).isNull();
    }

    @Test
    void recoverStaleParsing_skipsScriptsRunningInMemoryTaskTable() {
        Script running = new Script();
        running.setId(7L);
        running.setParsingStatus(1);
        running.setUpdateTime(LocalDateTime.now().minusHours(2));
        when(scriptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(running));
        service.markScriptRunning(7L);

        int recovered = service.recoverStaleParsing();

        assertThat(recovered).isZero();
        assertThat(service.isScriptRunning(7L)).isTrue();
        verify(scriptMapper, never()).update(any(), any());
    }

    @Test
    void recoverStaleParsing_withoutCandidates_doesNothing() {
        when(scriptMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        int recovered = service.recoverStaleParsing();

        assertThat(recovered).isZero();
        verify(scriptMapper, never()).update(any(), any());
    }

    // ========== 僵尸会话终态化 ==========

    @Test
    void recoverStaleConversations_finishesStaleRunningConversations() {
        AgentConversation stale = new AgentConversation();
        stale.setConversationId("conv-1");
        stale.setContextId(5L);
        when(conversationService.listStaleRunning(
                eq(ScriptAutoSplitService.AUTO_SPLIT_TASK_TYPE), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        int finished = service.recoverStaleConversations();

        assertThat(finished).isEqualTo(1);
        verify(conversationService).finish("conv-1", ScriptAutoSplitService.STALE_CONVERSATION_STATUS);
    }

    @Test
    void recoverStaleConversations_skipsConversationsOfScriptsRunningInMemory() {
        AgentConversation live = new AgentConversation();
        live.setConversationId("conv-2");
        live.setContextId(7L);
        when(conversationService.listStaleRunning(any(), any())).thenReturn(List.of(live));
        service.markScriptRunning(7L);

        int finished = service.recoverStaleConversations();

        assertThat(finished).isZero();
        verify(conversationService, never()).finish(any(), any());
    }

    // ========== 分块转换：校验 + 一次修复重试 ==========

    @Test
    void convertChunk_validOutput_parsesWithoutRetry() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(converted.getStr("episodeTitle")).isEqualTo("夜归");
        assertThat(converted.getStr("episodeSynopsis")).contains("张三深夜回家");
        assertThat(converted.getJSONArray("scenes").getJSONObject(0).getStr("sceneHeading"))
                .isEqualTo("内景 厨房 夜");
    }

    @Test
    void convertChunk_badJsonOnFirstAttempt_retriesWithRepairInstructionAndSucceeds() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("抱歉，我无法完成这个任务。"))
                .thenReturn(chatResponse(validEpisodeJson()));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        assertThat(converted.getStr("episodeTitle")).isEqualTo("夜归");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        // 重试消息必须携带修复指令
        assertThat(promptCaptor.getAllValues().get(1).getInstructions().get(1).getText())
                .contains("只输出 JSON");
    }

    @Test
    void convertChunk_emptyScenesOnFirstAttempt_retriesOnce() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("{\"scenes\":[]}"))
                .thenReturn(chatResponse(validEpisodeJson()));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        assertThat(converted.getJSONArray("scenes")).hasSize(1);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void convertChunk_sceneMissingHeadingOnFirstAttempt_retriesOnce() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse(
                        "{\"scenes\":[{\"sceneDescription\":\"缺标头\",\"dialogues\":[]}]}"))
                .thenReturn(chatResponse(validEpisodeJson()));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        assertThat(converted.getJSONArray("scenes").getJSONObject(0).getStr("sceneHeading"))
                .isEqualTo("内景 厨房 夜");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void convertChunk_bothAttemptsInvalid_throwsForVerbatimFallback() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("无法输出"))
                .thenReturn(chatResponse("{\"scenes\":[]}"));

        assertThatThrownBy(() -> service.convertChunk(chatModel, "第一章 起点", "张三……", 6000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连续两次");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ========== 全链路落库：schema 统一 + 集概要 + 原文兜底 ==========

    @Test
    void runAutoSplit_convertedEpisode_persistsUnifiedSchemaAndSynopsis() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        service.runAutoSplit(1L, 9L, 10L, "task-1", 6000);

        ArgumentCaptor<ScriptEpisode> episodeCaptor = ArgumentCaptor.forClass(ScriptEpisode.class);
        verify(episodeMapper).insert(episodeCaptor.capture());
        assertThat(episodeCaptor.getValue().getTitle()).isEqualTo("夜归");
        // 模型返回的一句话集概要写入 episode.synopsis
        assertThat(episodeCaptor.getValue().getSynopsis()).isEqualTo("张三深夜回家，在厨房与母亲交谈");

        ArgumentCaptor<ScriptSceneItem> sceneCaptor = ArgumentCaptor.forClass(ScriptSceneItem.class);
        verify(sceneItemMapper).insert(sceneCaptor.capture());
        ScriptSceneItem scene = sceneCaptor.getValue();
        // 场次号对齐 Agent 链路的"集-场"格式
        assertThat(scene.getSceneNumber()).isEqualTo("1-1");
        assertThat(scene.getSceneHeading()).isEqualTo("内景 厨房 夜");
        // dialogues 为标准 DialogueElement 结构（type 为整数、character_name 字段）
        List<JSONObject> dialogues = JSONUtil.parseArray(scene.getDialogues())
                .toList(JSONObject.class);
        assertThat(dialogues).hasSize(2);
        assertThat(dialogues.get(0).getInt("type")).isEqualTo(2);
        assertThat(dialogues.get(1).getInt("type")).isEqualTo(1);
        assertThat(dialogues.get(1).getStr("character_name")).isEqualTo("母亲");
        assertThat(dialogues.get(1).getStr("content")).isEqualTo("怎么才回来？");
        // 出场角色从对白讲者去重推导并写入 characters
        assertThat(JSONUtil.parseArray(scene.getCharacters()).toList(String.class))
                .containsExactly("母亲");
        // 对白不再复述进场景描述
        assertThat(scene.getSceneDescription()).doesNotContain("对白：");
        verify(taskStreamService).complete(eq("task-1"), any());
    }

    @Test
    void runAutoSplit_bothAttemptsFail_savesVerbatimEpisodeAsFallback() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("无法输出"))
                .thenReturn(chatResponse("{\"scenes\":[]}"));

        service.runAutoSplit(1L, 9L, 10L, "task-2", 6000);

        // 两次失败后原文兜底：整块原文保存为该集唯一场景，任务正常完成
        ArgumentCaptor<ScriptEpisode> episodeCaptor = ArgumentCaptor.forClass(ScriptEpisode.class);
        verify(episodeMapper).insert(episodeCaptor.capture());
        assertThat(episodeCaptor.getValue().getTitle()).isEqualTo("第一章 起点");
        assertThat(episodeCaptor.getValue().getSynopsis()).isNull();
        ArgumentCaptor<ScriptSceneItem> sceneCaptor = ArgumentCaptor.forClass(ScriptSceneItem.class);
        verify(sceneItemMapper).insert(sceneCaptor.capture());
        assertThat(sceneCaptor.getValue().getSceneNumber()).isEqualTo("1-1");
        assertThat(sceneCaptor.getValue().getSceneHeading()).isEqualTo("原文片段 1");
        assertThat(sceneCaptor.getValue().getDialogues()).isNull();
        verify(taskStreamService).complete(eq("task-2"), any());
    }

    // ========== 测试辅助 ==========

    private static String validEpisodeJson() {
        return """
                {"episodeTitle":"夜归","episodeSynopsis":"张三深夜回家，在厨房与母亲交谈",\
                "scenes":[{"sceneHeading":"内景 厨房 夜","sceneDescription":"张三推开厨房门，饭菜早已凉透。",\
                "dialogues":[{"type":2,"content":"张三推开厨房门，饭菜早已凉透。"},\
                {"type":1,"character_name":"母亲","content":"怎么才回来？","parenthetical":"低声"}],\
                "characters":["母亲"]}]}\
                """;
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** 准备 runAutoSplit 所需的剧本与模型桩 */
    private void stubScriptAndModel(String rawContent) {
        Script script = new Script();
        script.setId(1L);
        script.setProjectId(2L);
        script.setTitle("测试剧本");
        script.setRawContent(rawContent);
        when(scriptMapper.selectById(1L)).thenReturn(script);
        AiModel model = new AiModel();
        model.setId(10L);
        model.setName("qwen2.5:14b");
        when(aiModelService.getById(10L)).thenReturn(model);
    }
}

package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
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
    private final ScriptMetadataSynthesizer metadataSynthesizer = mock(ScriptMetadataSynthesizer.class);
    private final CacheManager cacheManager = new ConcurrentMapCacheManager();

    private final ScriptAutoSplitService service = new ScriptAutoSplitService(
            scriptMapper, episodeMapper, sceneItemMapper,
            aiModelService, aiProviderService, taskStreamService, conversationService,
            metadataSynthesizer, cacheManager);

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

    // ========== 同剧本并发启动防护与内存任务表计数 ==========

    @Test
    void startAutoSplit_rejectsWhenSameScriptIsAlreadyRunning() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        service.markScriptRunning(1L);

        assertThatThrownBy(() -> service.startAutoSplit(1L, 9L, 10L, 6000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该剧本正在解析中");
        // 被拒绝的第二次启动不创建任务，也不改写排队状态
        verify(taskStreamService, never()).createTask(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void startAutoSplit_registrationSurvivesUntilTaskThreadTakesOverAndReleasesOnSetupFailure() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        // 建任务阶段失败：登记必须被回收，避免滞留恢复豁免泄漏
        when(taskStreamService.createTask(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("task stream unavailable"));

        assertThatThrownBy(() -> service.startAutoSplit(1L, 9L, 10L, 6000))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.isScriptRunning(1L)).isFalse();
    }

    @Test
    void markScriptRunning_countsTasksAndKeepsExemptionUntilLastFinisher() {
        // 第一次登记生效，重复登记被拒绝启动路径识别为“已在跑”
        assertThat(service.markScriptRunning(7L)).isTrue();
        assertThat(service.markScriptRunning(7L)).isFalse();
        assertThat(service.isScriptRunning(7L)).isTrue();

        // 先结束者只递减计数：后启动者仍在跑时，滞留恢复豁免不得被提前移除
        service.markScriptFinished(7L);
        assertThat(service.isScriptRunning(7L)).isTrue();

        service.markScriptFinished(7L);
        assertThat(service.isScriptRunning(7L)).isFalse();

        // 多次回收是幂等的：计数不会跌为负值后误伤后续登记
        service.markScriptFinished(7L);
        assertThat(service.markScriptRunning(7L)).isTrue();
        assertThat(service.isScriptRunning(7L)).isTrue();
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

    // ========== 软性问题：修复重试一次后接受现状 ==========

    @Test
    void convertChunk_softProblemsOnBothAttempts_acceptsNormalizedResultAfterRetry() {
        // 两轮输出都缺说话人与景别前缀：重试一次后接受现状，不抛出也不无限重试
        String softProblemJson = """
                {"scenes":[{"sceneHeading":"高空 日","sceneDescription":"描述","dialogues":[\
                {"type":1,"content":"怎么才回来？"}]}]}\
                """;
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse(softProblemJson))
                .thenReturn(chatResponse(softProblemJson));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertThat(converted.getJSONArray("scenes").getJSONObject(0).getStr("sceneHeading"))
                .isEqualTo("高空 日");
        // 修复指令携带两类软性问题原文
        assertThat(promptCaptor.getAllValues().get(1).getInstructions().get(1).getText())
                .contains("第1场有对白缺少说话人")
                .contains("场次1标头缺少内景/外景前缀");
    }

    @Test
    void convertChunk_softProblemOnFirstAttempt_repairedOnRetry() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("""
                        {"scenes":[{"sceneHeading":"高空 日","sceneDescription":"描述","dialogues":[\
                        {"type":1,"content":"怎么才回来？"}]}]}\
                        """))
                .thenReturn(chatResponse(validEpisodeJson()));

        JSONObject converted = service.convertChunk(chatModel, "第一章 起点", "张三……", 6000);

        assertThat(converted.getStr("episodeTitle")).isEqualTo("夜归");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void convertChunk_softProblemThenHardFailure_throwsForSplitFallback() {
        // 软性问题触发修复重试后，重试输出硬性失败：仍走对半再切的抛出路径
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("""
                        {"scenes":[{"sceneHeading":"高空 日","sceneDescription":"描述","dialogues":[]}]}\
                        """))
                .thenReturn(chatResponse("无法输出"));

        assertThatThrownBy(() -> service.convertChunk(chatModel, "第一章 起点", "张三……", 6000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连续两次");
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ========== 全链路落库：schema 统一 + 集概要 + 原文兜底 ==========

    @Test
    void runAutoSplit_convertedEpisode_persistsUnifiedSchemaAndSynopsis() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(null, null, null, List.of()));
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
    void runAutoSplit_allSplitPiecesFail_savesVerbatimEpisodeAsFallback() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(null, null, null, List.of()));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("无法输出"))
                .thenReturn(chatResponse("{\"scenes\":[]}"))
                .thenAnswer(invocation -> chatResponse("无法输出"));

        service.runAutoSplit(1L, 9L, 10L, "task-2", 6000);

        // 对半再切后全部子块仍失败：原始整块原文兜底保存为该集唯一场景，任务正常完成
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

    // ========== 失败块对半再切 ==========

    @Test
    void convertChunkWithSplit_wholeFailsButHalvesSucceed_returnsPartsInOrder() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String text = userMessageText(invocation);
            if (text.contains("左半标记") && text.contains("右半标记")) {
                return chatResponse("无法输出");
            }
            if (text.contains("左半标记")) {
                return chatResponse("""
                        {"episodeTitle":"夜归","episodeSynopsis":"左半概要","scenes":[\
                        {"sceneHeading":"内景 厨房 夜","sceneDescription":"左一场","dialogues":[]}]}\
                        """);
            }
            if (text.contains("右半标记")) {
                return chatResponse("""
                        {"scenes":[{"sceneHeading":"外景 街道 夜","sceneDescription":"右一场","dialogues":[]}]}\
                        """);
            }
            return chatResponse("无法输出");
        });

        List<JSONObject> parts = service.convertChunkWithSplit(chatModel, "第一章 起点",
                "左半标记 甲乙丙丁\n右半标记 戊己庚辛", 6000);

        // 整块首次失败后对半解析，两个子块结果按剧情顺序返回
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getStr("episodeTitle")).isEqualTo("夜归");
        assertThat(parts.get(1).getJSONArray("scenes").getJSONObject(0).getStr("sceneHeading"))
                .isEqualTo("外景 街道 夜");
    }

    @Test
    void convertChunkWithSplit_allPiecesFail_returnsEmptyAfterDepthLimit() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("无法输出"));

        List<JSONObject> parts = service.convertChunkWithSplit(chatModel, "第一章 起点",
                "甲乙丙丁\n戊己庚辛\n子丑寅卯\n卯辰巳午", 6000);

        assertThat(parts).isEmpty();
        // 递归深度最多 2 层：整块(1)+两半(2)+四份(4)=7 次 convertChunk，每次含修复重试共 14 次调用
        verify(chatModel, times(14)).call(any(Prompt.class));
    }

    @Test
    void splitInHalf_prefersParagraphBoundaryAndRejectsUnsplittableChunk() {
        // 有段落边界：在中点附近回退到换行处
        String[] halves = ScriptAutoSplitService.splitInHalf("甲乙丙丁\n戊己庚辛");
        assertThat(halves[0]).isEqualTo("甲乙丙丁");
        assertThat(halves[1]).isEqualTo("戊己庚辛");

        // 无段落边界：字符中点硬切
        String[] hardCut = ScriptAutoSplitService.splitInHalf("甲乙丙丁戊己庚辛");
        assertThat(hardCut[0]).isEqualTo("甲乙丙丁");
        assertThat(hardCut[1]).isEqualTo("戊己庚辛");

        // 过短块不可再切
        assertThat(ScriptAutoSplitService.splitInHalf("甲")).isNull();
        assertThat(ScriptAutoSplitService.splitInHalf("  ")).isNull();
        assertThat(ScriptAutoSplitService.splitInHalf(null)).isNull();
    }

    @Test
    void runAutoSplit_subBlockTitlesMerged_firstNonBlankWinsAndSceneNumbersContinue() {
        stubScriptAndModel("第一章 起点\n左半标记 甲乙丙丁\n右半标记 戊己庚辛");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(null, null, null, List.of()));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String text = userMessageText(invocation);
            if (text.contains("左半标记") && text.contains("右半标记")) {
                return chatResponse("无法输出");
            }
            if (text.contains("左半标记")) {
                return chatResponse("""
                        {"episodeTitle":"夜归","episodeSynopsis":"左半概要","scenes":[\
                        {"sceneHeading":"内景 厨房 夜","sceneDescription":"左一场","dialogues":[]},\
                        {"sceneHeading":"外景 学校操场 白天","sceneDescription":"左二场","dialogues":[]}]}\
                        """);
            }
            if (text.contains("右半标记")) {
                return chatResponse("""
                        {"scenes":[{"sceneHeading":"内景 客厅 夜","sceneDescription":"右一场","dialogues":[]}]}\
                        """);
            }
            return chatResponse("无法输出");
        });

        service.runAutoSplit(1L, 9L, 10L, "task-3", 6000);

        ArgumentCaptor<ScriptEpisode> episodeCaptor = ArgumentCaptor.forClass(ScriptEpisode.class);
        verify(episodeMapper).insert(episodeCaptor.capture());
        // 子块各自给标题时取第一个非空的，概要同理
        assertThat(episodeCaptor.getValue().getTitle()).isEqualTo("夜归");
        assertThat(episodeCaptor.getValue().getSynopsis()).isEqualTo("左半概要");

        ArgumentCaptor<ScriptSceneItem> sceneCaptor = ArgumentCaptor.forClass(ScriptSceneItem.class);
        verify(sceneItemMapper, times(3)).insert(sceneCaptor.capture());
        List<ScriptSceneItem> scenes = sceneCaptor.getAllValues();
        // 子块场次并入同一集，场次号跨子块连续；标头清洗（白天→日）落库前生效
        assertThat(scenes).extracting(ScriptSceneItem::getSceneNumber)
                .containsExactly("1-1", "1-2", "1-3");
        assertThat(scenes).extracting(ScriptSceneItem::getSceneHeading)
                .containsExactly("内景 厨房 夜", "外景 学校操场 日", "内景 客厅 夜");
    }

    // ========== 收尾合成：剧本级元数据 ==========

    @Test
    void runAutoSplit_metadataSynthesized_persistsStoryGenreAndCharacters() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(
                "张三与母亲的故事", "家庭/剧情",
                "[{\"name\":\"母亲\",\"importance\":\"主角\",\"description\":\"张三的母亲\"}]",
                List.of()));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        service.runAutoSplit(1L, 9L, 10L, "task-4", 6000);

        // 元数据写入剧本实体并携带分集摘要与角色出场统计
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScriptMetadataSynthesizer.EpisodeDigest>> digestCaptor =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ScriptMetadataSynthesizer.CharacterStat>> statsCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(metadataSynthesizer).synthesize(eq(chatModel), digestCaptor.capture(), statsCaptor.capture());
        assertThat(digestCaptor.getValue()).hasSize(1);
        assertThat(digestCaptor.getValue().get(0).title()).isEqualTo("夜归");
        assertThat(digestCaptor.getValue().get(0).synopsis()).contains("张三深夜回家");
        assertThat(statsCaptor.getValue()).hasSize(1);
        ScriptMetadataSynthesizer.CharacterStat stat =
                statsCaptor.getValue().iterator().next();
        assertThat(stat.getName()).isEqualTo("母亲");
        assertThat(stat.getCount()).isEqualTo(1);
        assertThat(stat.getSamples()).containsExactly("怎么才回来？");

        // 元数据字段随 LambdaUpdateWrapper 落库
        Set<Object> updatedValues = capturedScriptUpdateValues();
        assertThat(updatedValues).contains("张三与母亲的故事", "家庭/剧情",
                "[{\"name\":\"母亲\",\"importance\":\"主角\",\"description\":\"张三的母亲\"}]");
        // 完成日志不含合成失败标注
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskStreamService).complete(eq("task-4"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo("解析完成：共 1 集");
    }

    @Test
    void runAutoSplit_metadataSynthesisFailed_degradesWithoutFailingTask() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(
                null, null, null, List.of("故事梗概", "人物表")));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        service.runAutoSplit(1L, 9L, 10L, "task-5", 6000);

        // 显式降级：任务仍正常完成，完成日志注明元数据合成失败
        verify(metadataSynthesizer).synthesize(eq(chatModel), anyList(), anyCollection());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskStreamService).complete(eq("task-5"), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .isEqualTo("解析完成：共 1 集（元数据合成失败：故事梗概、人物表）");
        Set<Object> updatedValues = capturedScriptUpdateValues();
        assertThat(updatedValues).contains("解析完成：共 1 集（元数据合成失败：故事梗概、人物表）")
                .doesNotContain("张三与母亲的故事");
    }

    @Test
    void runAutoSplit_publishesSynthesizingProgress() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(null, null, null, List.of()));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        service.runAutoSplit(1L, 9L, 10L, "task-6", 6000);

        verify(taskStreamService).publishContent("task-6", ScriptAutoSplitService.SYNTHESIZING_PROGRESS);
        Set<Object> updatedValues = capturedScriptUpdateValues();
        assertThat(updatedValues).contains(ScriptAutoSplitService.SYNTHESIZING_PROGRESS);
    }

    // ========== 收尾合成阶段日志：进入/完成/降级均落后端日志 ==========

    @Test
    void runAutoSplit_logsSynthesisEntryAndCompletionWithItems() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(
                "张三与母亲的故事", "家庭/剧情",
                "[{\"name\":\"母亲\",\"importance\":\"主角\",\"description\":\"张三的母亲\"}]",
                List.of()));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        List<String> messages = captureAutoSplitLogs(
                () -> service.runAutoSplit(1L, 9L, 10L, "task-7", 6000));

        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("[AutoSplit]").contains("进入剧本元数据合成阶段").contains("scriptId=1"));
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("[AutoSplit]").contains("剧本元数据合成完成")
                .contains("耗时=").contains("成功项=故事梗概、题材、人物表"));
    }

    @Test
    void runAutoSplit_logsSynthesisDegradationWithItems() {
        stubScriptAndModel("第一章 起点\n张三推开厨房门，饭菜早已凉透。");
        stubSynthesis(new ScriptMetadataSynthesizer.MetadataResult(
                "张三与母亲的故事", "家庭/剧情", null, List.of("人物表")));
        ChatModel chatModel = mock(ChatModel.class);
        when(aiProviderService.createChatModel(any(AiModel.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(validEpisodeJson()));

        List<String> messages = captureAutoSplitLogs(
                () -> service.runAutoSplit(1L, 9L, 10L, "task-8", 6000));

        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("[AutoSplit]").contains("剧本元数据合成降级")
                .contains("耗时=").contains("成功项=故事梗概、题材").contains("降级项=人物表"));
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

    /** 桩定收尾合成结果，阻断真实合成调用 */
    private void stubSynthesis(ScriptMetadataSynthesizer.MetadataResult result) {
        when(metadataSynthesizer.synthesize(any(ChatModel.class), anyList(), anyCollection()))
                .thenReturn(result);
    }

    /** 捕获 ScriptAutoSplitService 的格式化日志内容，供收尾合成阶段日志断言 */
    private List<String> captureAutoSplitLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ScriptAutoSplitService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 从 mock 调用中取出 Prompt 的用户消息文本（用于按块内容路由桩响应） */
    private static String userMessageText(InvocationOnMock invocation) {
        Prompt prompt = invocation.getArgument(0);
        return ((UserMessage) prompt.getInstructions().get(1)).getText();
    }

    /** 汇总全部 scriptMapper.update 的 Wrapper 参数值（状态文案、梗概、人物表等） */
    private Set<Object> capturedScriptUpdateValues() {
        ArgumentCaptor<LambdaUpdateWrapper<Script>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(scriptMapper, atLeastOnce()).update(isNull(), captor.capture());
        Set<Object> values = new HashSet<>();
        for (LambdaUpdateWrapper<Script> wrapper : captor.getAllValues()) {
            values.addAll(wrapper.getParamNameValuePairs().values());
        }
        return values;
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

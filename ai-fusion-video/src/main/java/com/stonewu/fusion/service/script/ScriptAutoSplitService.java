package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长文本（小说级）自动分块转剧本：
 * 后端按章节/段落确定性分块，逐块调用文本模型改写为结构化场次，
 * 与模型上下文大小无关——每块固定大小，任何模型都能稳定输出。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptAutoSplitService {

    /** 默认每块目标字符数：为小参数本地模型留足输出余量 */
    static final int DEFAULT_CHUNK_CHARS = 6000;
    /** 每块目标字符数下限：过小的块会让模型输出退化为逐段复述 */
    static final int MIN_CHUNK_CHARS = 2000;
    /** 每块目标字符数上限：超过后单次模型调用质量与稳定性下降 */
    static final int MAX_CHUNK_CHARS = 12000;
    /** 分块安全上限 */
    private static final int MAX_CHUNKS = 400;
    /** 单次调用给模型的原始块上限（防止异常超长块撑爆请求） */
    private static final int MODEL_INPUT_MAX = 9000;
    /** 解析任务滞留判定阈值：超过该时长没有进度更新的 parsing 任务视为中断 */
    static final int STALE_PARSING_THRESHOLD_MINUTES = 30;
    /** 滞留恢复时写入的失败说明 */
    static final String STALE_PARSING_MESSAGE = "解析中断，请重跑";
    /** 任务流类型：TaskStreamService.createTask 与 afv_agent_conversation.agent_type 共用 */
    static final String AUTO_SPLIT_TASK_TYPE = "script_auto_split";
    /** 僵尸会话判定阈值：任务线程随进程重启消失时，会话会永远停在 running，
     * 超过该时长仍无更新的 script_auto_split 会话视为僵尸并终态化。 */
    static final int STALE_CONVERSATION_THRESHOLD_MINUTES = 120;
    /** 僵尸会话终态化的状态值 */
    static final String STALE_CONVERSATION_STATUS = "failed";
    /** 收尾合成阶段的进度文案 */
    static final String SYNTHESIZING_PROGRESS = "正在合成剧本元数据";
    /** 失败块对半再切的最大递归深度：2 层即最多切成 4 份 */
    static final int MAX_SPLIT_DEPTH = 2;
    /** ScriptService 实体缓存名与键前缀（与 @Cacheable value/key 保持一致） */
    private static final String SCRIPT_CACHE_NAME = "script";
    private static final String EPISODE_CACHE_NAME = "episode";
    private static final String SCRIPT_PROJECT_KEY_PREFIX = "project:";
    private static final String EPISODE_SCRIPT_KEY_PREFIX = "script:";
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^第[0-9一二三四五六七八九十百千]+章.*$|^Chapter\\s+\\d+.*$", Pattern.CASE_INSENSITIVE);

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptSceneItemMapper sceneItemMapper;
    private final AiModelService aiModelService;
    private final AiProviderService aiProviderService;
    private final TaskStreamService taskStreamService;
    private final AgentConversationService conversationService;
    private final ScriptMetadataSynthesizer metadataSynthesizer;
    private final CacheManager cacheManager;

    private final ExecutorService autoSplitExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "script-auto-split");
        thread.setDaemon(true);
        return thread;
    });

    /** 正在执行自动分块解析的剧本任务计数（内存任务表，滞留恢复时据此避开进行中的任务） */
    private final Map<Long, AtomicInteger> runningScriptTasks = new ConcurrentHashMap<>();

    public String startAutoSplit(Long scriptId, Long userId, Long modelId) {
        return startAutoSplit(scriptId, userId, modelId, null);
    }

    public String startAutoSplit(Long scriptId, Long userId, Long modelId, Integer chunkChars) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new BusinessException(404, "剧本不存在: " + scriptId);
        }
        if (script.getRawContent() == null || script.getRawContent().isBlank()) {
            throw new BusinessException("剧本原文为空，无法自动分块解析");
        }
        AiModel model = modelId == null
                ? aiModelService.getDefaultByType(1)
                : aiModelService.getById(modelId);
        if (model == null || !Integer.valueOf(1).equals(model.getStatus())) {
            throw new BusinessException("请选择一个已启用的文本模型");
        }

        int normalizedChunkChars = normalizeChunkChars(chunkChars);
        // 原子占位登记：已在跑的剧本直接拒绝，防止并发解析互相覆盖解析产物
        if (!markScriptRunning(scriptId)) {
            throw new BusinessException(409, "该剧本正在解析中");
        }
        try {
            String taskId = taskStreamService.createTask(
                    userId,
                    script.getProjectId(),
                    AUTO_SPLIT_TASK_TYPE,
                    "自动分块解析 · " + script.getTitle(),
                    "script",
                    scriptId,
                    "开始自动分块解析，共 " + script.getRawContent().length() + " 字");
            markParsing(scriptId, script.getProjectId(), 1, "排队中");
            long capturedModelId = model.getId();
            autoSplitExecutor.execute(() -> {
                try {
                    runAutoSplit(scriptId, userId, capturedModelId, taskId, normalizedChunkChars);
                } catch (Throwable t) {
                    log.error("[AutoSplit] 自动分块解析失败: scriptId={}", scriptId, t);
                    markParsing(scriptId, script.getProjectId(), 3, "自动分块解析失败: " + t.getMessage());
                    taskStreamService.fail(taskId, "自动分块解析失败: " + t.getMessage());
                } finally {
                    markScriptFinished(scriptId);
                }
            });
            return taskId;
        } catch (RuntimeException registrationFailure) {
            // 未能进入任务线程（建任务/排队标记失败）：回收登记，避免滞留恢复豁免泄漏
            markScriptFinished(scriptId);
            throw registrationFailure;
        }
    }

    /**
     * 滞留恢复：应用启动完成后扫描一次。
     * 应用重启后内存任务表为空，所有 parsing_status=1 的滞留记录都视为中断；
     * 同时终态化进程重启遗留的 running 会话。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleParsingOnStartup() {
        recoverStaleParsing();
        recoverStaleConversations();
    }

    /** 滞留恢复：运行期间定时兜底扫描（每 5 分钟）。 */
    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    public void recoverStaleParsingScheduled() {
        recoverStaleParsing();
        recoverStaleConversations();
    }

    /**
     * 扫描 parsing_status=1 且 update_time 超过阈值的剧本，标记为失败并提示重跑。
     * 与内存任务表对照，正在运行的任务不会被误标。
     *
     * @return 本次恢复的滞留任务数
     */
    public int recoverStaleParsing() {
        List<Script> parsingScripts = scriptMapper.selectList(new LambdaQueryWrapper<Script>()
                .eq(Script::getParsingStatus, 1)
                .lt(Script::getUpdateTime, LocalDateTime.now().minusMinutes(STALE_PARSING_THRESHOLD_MINUTES)));
        return recoverStaleScripts(parsingScripts);
    }

    /** 对候选剧本执行滞留恢复，跳过内存任务表中正在运行的剧本。 */
    int recoverStaleScripts(List<Script> candidates) {
        int recovered = 0;
        for (Script script : candidates) {
            if (script == null || script.getId() == null) {
                continue;
            }
            if (isScriptRunning(script.getId())) {
                log.info("[AutoSplit] 跳过正在运行的解析任务: scriptId={}", script.getId());
                continue;
            }
            markParsing(script.getId(), script.getProjectId(), 3, STALE_PARSING_MESSAGE);
            recovered += 1;
            log.warn("[AutoSplit] 恢复滞留解析任务: scriptId={}, 标记为失败并提示重跑", script.getId());
        }
        return recovered;
    }

    /**
     * 僵尸会话兜底：任务完成/失败时 TaskStreamService 已会话终态化，但任务线程
     * 随进程重启消失后会话会永远停在 running。扫描超过阈值仍 running 的
     * script_auto_split 会话并置为 failed。
     *
     * @return 本次终态化的僵尸会话数
     */
    int recoverStaleConversations() {
        List<AgentConversation> stale = conversationService.listStaleRunning(
                AUTO_SPLIT_TASK_TYPE,
                LocalDateTime.now().minusMinutes(STALE_CONVERSATION_THRESHOLD_MINUTES));
        int finished = 0;
        for (AgentConversation conversation : stale) {
            if (conversation == null || conversation.getConversationId() == null) {
                continue;
            }
            // 内存任务表仍登记在案说明任务正在正常运行，不误终态化
            if (conversation.getContextId() != null && isScriptRunning(conversation.getContextId())) {
                continue;
            }
            conversationService.finish(conversation.getConversationId(), STALE_CONVERSATION_STATUS);
            finished += 1;
            log.warn("[AutoSplit] 终态化滞留解析会话: conversationId={}, contextId={}",
                    conversation.getConversationId(), conversation.getContextId());
        }
        return finished;
    }

    /**
     * 登记进行中的解析任务（内存任务表）：按剧本计数。
     *
     * @return true 表示该剧本此前空闲（本次启动生效）；false 表示已有任务在跑
     */
    boolean markScriptRunning(Long scriptId) {
        AtomicInteger counter = runningScriptTasks.computeIfAbsent(scriptId, key -> new AtomicInteger());
        return counter.getAndIncrement() == 0;
    }

    /**
     * 任务结束（成功或失败）后递减计数：计数归零才移除登记，保证并发场景下
     * 后启动者结束前，滞留恢复的豁免不会被先结束者提前移除。
     */
    void markScriptFinished(Long scriptId) {
        runningScriptTasks.computeIfPresent(scriptId, (key, counter) ->
                counter.decrementAndGet() <= 0 ? null : counter);
    }

    boolean isScriptRunning(Long scriptId) {
        return runningScriptTasks.containsKey(scriptId);
    }

    /** 越界钳制分块参数：null 取默认值，超出 [MIN, MAX] 时取边界值 */
    static int normalizeChunkChars(Integer chunkChars) {
        int value = chunkChars == null ? DEFAULT_CHUNK_CHARS : chunkChars;
        return Math.max(MIN_CHUNK_CHARS, Math.min(MAX_CHUNK_CHARS, value));
    }

    void runAutoSplit(Long scriptId, Long userId, Long modelId, String taskId, int chunkChars) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null || script.getRawContent() == null || script.getRawContent().isBlank()) {
            throw new BusinessException("剧本原文为空，无法自动分块解析");
        }
        String raw = script.getRawContent();
        AiModel model = aiModelService.getById(modelId);
        ChatModel chatModel = aiProviderService.createChatModel(model);

        // 清空旧解析产物
        sceneItemMapper.delete(new LambdaQueryWrapper<ScriptSceneItem>()
                .eq(ScriptSceneItem::getScriptId, scriptId));
        episodeMapper.delete(new LambdaQueryWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getScriptId, scriptId));
        evictScriptRelatedCaches(scriptId, script.getProjectId());

        List<String> chunks = splitIntoChunks(raw, chunkChars);
        int total = chunks.size();
        log.info("[AutoSplit] 开始自动分块解析: scriptId={}, chunks={}, model={}",
                scriptId, total, model.getName());
        taskStreamService.publishContent(taskId, "已分块：" + total + " 块");

        int episodeNumber = 0;
        List<ScriptMetadataSynthesizer.EpisodeDigest> episodeDigests = new ArrayList<>();
        Map<String, ScriptMetadataSynthesizer.CharacterStat> characterStats = new LinkedHashMap<>();
        for (int i = 0; i < total; i++) {
            markParsing(scriptId, script.getProjectId(), 1, "正在解析分块 " + (i + 1) + "/" + total);
            String chunk = chunks.get(i);
            String chunkTitle = chunkTitle(chunks, i);
            episodeNumber += 1;
            // 校验失败时对半再切递归解析；全部子块仍失败返回空列表走原文兜底
            List<JSONObject> convertedParts =
                    convertChunkWithSplit(chatModel, chunkTitle, chunk, chunkChars);
            if (convertedParts.isEmpty()) {
                // 对半再切后仍全部失败（含一次修复重试）：原文兜底保存为该块的场景
                log.warn("[AutoSplit] 分块 {} 转换失败，原文兜底保存", i + 1);
                saveVerbatimEpisode(scriptId, episodeNumber, chunkTitle, chunk);
                episodeDigests.add(new ScriptMetadataSynthesizer.EpisodeDigest(chunkTitle, null));
            } else {
                saveConvertedEpisode(scriptId, episodeNumber, chunkTitle, chunk, convertedParts);
                collectEpisodeDigestAndCharacters(episodeDigests, characterStats, chunkTitle, convertedParts);
            }
            taskStreamService.publishContent(taskId, "进度：" + (i + 1) + "/" + total);
        }

        // 收尾合成剧本级元数据（故事梗概/题材/人物表），失败显式降级不中断任务
        markParsing(scriptId, script.getProjectId(), 1, SYNTHESIZING_PROGRESS);
        taskStreamService.publishContent(taskId, SYNTHESIZING_PROGRESS);
        long synthesisStartMillis = System.currentTimeMillis();
        log.info("[AutoSplit] 进入剧本元数据合成阶段: scriptId={}, episodes={}, characters={}",
                scriptId, episodeDigests.size(), characterStats.size());
        ScriptMetadataSynthesizer.MetadataResult metadata =
                metadataSynthesizer.synthesize(chatModel, episodeDigests, characterStats.values());
        long synthesisCostMillis = System.currentTimeMillis() - synthesisStartMillis;
        String completionMessage = "解析完成：共 " + episodeNumber + " 集";
        if (metadata.success()) {
            List<String> successItems = synthesizedItemNames(metadata);
            if (successItems.isEmpty()) {
                // 合成调用成功但没有产出任何可保存字段：按明确语义记录，避免打出空的“成功项=”
                log.info("[AutoSplit] 剧本元数据合成完成（模型未产出可保存项）: scriptId={}, 耗时={}ms",
                        scriptId, synthesisCostMillis);
            } else {
                log.info("[AutoSplit] 剧本元数据合成完成: scriptId={}, 耗时={}ms, 成功项={}",
                        scriptId, synthesisCostMillis, String.join("、", successItems));
            }
        } else {
            log.warn("[AutoSplit] 剧本元数据合成降级: scriptId={}, 耗时={}ms, 成功项={}, 降级项={}",
                    scriptId, synthesisCostMillis,
                    String.join("、", synthesizedItemNames(metadata)),
                    String.join("、", metadata.failures()));
            completionMessage += "（元数据合成失败：" + String.join("、", metadata.failures()) + "）";
        }
        persistScriptMetadata(scriptId, script.getProjectId(), metadata);

        markParsing(scriptId, script.getProjectId(), 2, completionMessage);
        scriptMapper.update(null, new LambdaUpdateWrapper<Script>()
                .eq(Script::getId, scriptId)
                .set(Script::getTotalEpisodes, episodeNumber));
        taskStreamService.complete(taskId, completionMessage);
        log.info("[AutoSplit] 自动分块解析完成: scriptId={}, episodes={}", scriptId, episodeNumber);
    }

    /** 合成成功的元数据项名称（题材随故事梗概同一次调用产出） */
    private static List<String> synthesizedItemNames(ScriptMetadataSynthesizer.MetadataResult metadata) {
        List<String> items = new ArrayList<>();
        if (metadata.storySynopsis() != null) {
            items.add("故事梗概");
        }
        if (metadata.genre() != null) {
            items.add("题材");
        }
        if (metadata.charactersJson() != null) {
            items.add("人物表");
        }
        return items;
    }

    /**
     * 记录分集摘要与角色出场统计，供收尾合成阶段使用。
     * <p>
     * 口径说明（有意与 ScriptChunkValidator.normalizeScene 的场次级 characters 不同）：
     * 剧本级人物表只统计 type=1 对白讲者，避免「旁白」作为讲者混入角色卡；
     * 场次级 characters 则同时收录对白与旁白讲者，便于前端按场次定位配音角色。
     */
    private void collectEpisodeDigestAndCharacters(
            List<ScriptMetadataSynthesizer.EpisodeDigest> episodeDigests,
            Map<String, ScriptMetadataSynthesizer.CharacterStat> characterStats,
            String chunkTitle, List<JSONObject> convertedParts) {
        String title = firstNonBlank(convertedParts, "episodeTitle");
        episodeDigests.add(new ScriptMetadataSynthesizer.EpisodeDigest(
                title == null ? chunkTitle : title,
                firstNonBlank(convertedParts, "episodeSynopsis")));
        for (JSONObject part : convertedParts) {
            JSONArray scenes = part.getJSONArray("scenes");
            if (scenes == null) {
                continue;
            }
            for (Object sceneObj : scenes) {
                if (!(sceneObj instanceof JSONObject scene)) {
                    continue;
                }
                JSONArray dialogues = scene.getJSONArray("dialogues");
                if (dialogues == null) {
                    continue;
                }
                for (Object dialogueObj : dialogues) {
                    if (!(dialogueObj instanceof JSONObject dialogue)
                            || !Integer.valueOf(1).equals(dialogue.getInt("type", 0))) {
                        continue;
                    }
                    String characterName = dialogue.getStr("character_name", "");
                    if (characterName.isBlank()) {
                        continue;
                    }
                    characterStats.computeIfAbsent(characterName.strip(),
                            ScriptMetadataSynthesizer.CharacterStat::new)
                            .record(dialogue.getStr("content", ""));
                }
            }
        }
    }

    /** 取子块结果中第一个非空字段（子块可能各自给出标题/概要，取第一个非空的） */
    private static String firstNonBlank(List<JSONObject> parts, String key) {
        for (JSONObject part : parts) {
            String value = part.getStr(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** 收尾合成结果落库：只写成功合成的字段并失效相关缓存 */
    private void persistScriptMetadata(Long scriptId, Long projectId,
                                       ScriptMetadataSynthesizer.MetadataResult metadata) {
        if (metadata.storySynopsis() == null && metadata.genre() == null
                && metadata.charactersJson() == null) {
            return;
        }
        LambdaUpdateWrapper<Script> update = new LambdaUpdateWrapper<Script>()
                .eq(Script::getId, scriptId);
        if (metadata.storySynopsis() != null) {
            update.set(Script::getStorySynopsis, metadata.storySynopsis());
        }
        if (metadata.genre() != null) {
            update.set(Script::getGenre, metadata.genre());
        }
        if (metadata.charactersJson() != null) {
            update.set(Script::getCharactersJson, metadata.charactersJson());
        }
        scriptMapper.update(null, update);
        evictScriptRelatedCaches(scriptId, projectId);
    }

    /**
     * 章节感知分块（使用默认分块大小，保持既有调用兼容）：优先按 第X章/Chapter N
     * 切分，再按段落边界切成不超过分块字符数的原子片段后相邻聚合。
     */
    List<String> splitIntoChunks(String raw) {
        return splitIntoChunks(raw, null);
    }

    /**
     * 章节感知分块：优先按 第X章/Chapter N 切分，再按段落边界切成不超过
     * chunkChars 的原子片段后相邻聚合。任何块都不超过 chunkChars（输入上限
     * 亦随之放宽，见 {@link #convertChunk}），确保调用模型时不截断、不丢字。
     */
    List<String> splitIntoChunks(String raw, Integer chunkChars) {
        int targetChunkChars = normalizeChunkChars(chunkChars);
        // 1. 切原子片段：章节边界 → 段落边界，每片 ≤ targetChunkChars
        List<String> pieces = new ArrayList<>();
        Matcher matcher = CHAPTER_HEADING.matcher(raw);
        int last = 0;
        List<String> sections = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.start() > last) {
                sections.add(raw.substring(last, matcher.start()));
            }
            sections.add(raw.substring(matcher.start(), matcher.end()));
            last = matcher.end();
        }
        if (last < raw.length()) {
            sections.add(raw.substring(last));
        }
        if (sections.isEmpty()) {
            sections = List.of(raw);
        }
        for (String section : sections) {
            String rest = section.strip();
            while (rest.length() > targetChunkChars) {
                int cut = paragraphBoundary(rest, targetChunkChars);
                pieces.add(rest.substring(0, cut));
                rest = rest.substring(cut).strip();
            }
            if (!rest.isEmpty()) {
                pieces.add(rest);
            }
        }

        // 2. 相邻原子片段聚合为不超过 targetChunkChars 的块
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + piece.length() + 2 > targetChunkChars) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(piece).append("\n\n");
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        if (chunks.size() > MAX_CHUNKS) {
            throw new BusinessException("文本过长（分块超过 " + MAX_CHUNKS + " 块），请先拆分后再导入");
        }
        return chunks;
    }

    private static int paragraphBoundary(String text, int target) {
        int cut = Math.min(target, text.length());
        int boundary = text.lastIndexOf("\n", cut);
        return boundary > target / 2 ? boundary : cut;
    }

    private String chunkTitle(List<String> chunks, int index) {
        String chunk = chunks.get(index).strip();
        for (String line : chunk.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("第") || trimmed.toLowerCase().startsWith("chapter")) {
                return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
            }
        }
        return "第" + (index + 1) + "部分";
    }

    /**
     * 逐块调用模型改写为结构化场次，输出经 {@link ScriptChunkValidator} 校验
     * 与归一化的 JSON；首次输出有问题时带着修复指令重试一次：硬性问题仍不通过
     * 则抛出异常，由 {@link #convertChunkWithSplit} 对半再切；软性问题（对白缺
     * 说话人/标头缺景别前缀）重试后仍存在时接受现状，不整块失败。
     */
    JSONObject convertChunk(ChatModel chatModel, String chunkTitle, String chunk, int chunkChars) {
        // 用户显式调大分块时，输入上限随之放宽，避免正常块被截断丢字
        int inputLimit = Math.max(MODEL_INPUT_MAX, chunkChars);
        String input = chunk.length() > inputLimit ? chunk.substring(0, inputLimit) : chunk;
        String userMessage = ScriptAutoSplitPrompts.userMessage(chunkTitle, input);
        ScriptChunkValidator.Result result = callAndValidate(chatModel, userMessage, input);
        if (!result.problems().isEmpty()) {
            log.warn("[AutoSplit] 模型输出存在问题，携带修复指令重试一次: {}",
                    String.join("；", result.problems()));
            result = callAndValidate(chatModel,
                    ScriptAutoSplitPrompts.repairMessage(userMessage, result.problems()), input);
        }
        if (!result.valid()) {
            // 硬性问题重试后仍不通过：抛出交由上层对半再切/原文兜底
            throw new BusinessException("模型输出连续两次无法解析: " + String.join("；", result.problems()));
        }
        if (!result.problems().isEmpty()) {
            // 软性问题重试后仍存在：接受现状，不做无限重试，也不做兜底改写
            log.warn("[AutoSplit] 软性问题修复重试后仍存在，接受现状: {}",
                    String.join("；", result.problems()));
        }
        return result.normalized();
    }

    /**
     * 带对半再切的分块解析：convertChunk 校验失败且重试仍失败时，把该块按
     * 段落边界对半切成两块分别解析（子块继承同样的校验+重试逻辑），递归深度
     * 最多 {@link #MAX_SPLIT_DEPTH} 层（最多切成 4 份）。
     * 返回各子块的转换结果（保持剧情顺序）；全部子块仍失败时返回空列表，
     * 由调用方对原始块走原文兜底。
     */
    List<JSONObject> convertChunkWithSplit(ChatModel chatModel, String chunkTitle,
                                           String chunk, int chunkChars) {
        return convertChunkWithSplit(chatModel, chunkTitle, chunk, chunkChars, 0);
    }

    private List<JSONObject> convertChunkWithSplit(ChatModel chatModel, String chunkTitle,
                                                   String chunk, int chunkChars, int depth) {
        try {
            return List.of(convertChunk(chatModel, chunkTitle, chunk, chunkChars));
        } catch (Exception convertFailure) {
            log.warn("[AutoSplit] 分块转换失败（递归深度 {}/{}）: {}",
                    depth, MAX_SPLIT_DEPTH, convertFailure.getMessage());
        }
        if (depth >= MAX_SPLIT_DEPTH) {
            return List.of();
        }
        String[] halves = splitInHalf(chunk);
        if (halves == null) {
            return List.of();
        }
        List<JSONObject> results = new ArrayList<>();
        for (String half : halves) {
            results.addAll(convertChunkWithSplit(chatModel, chunkTitle, half, chunkChars, depth + 1));
        }
        return results;
    }

    /** 按段落边界对半切分块；无可用边界或切出空块时返回 null 表示不可再切 */
    static String[] splitInHalf(String chunk) {
        String text = chunk == null ? "" : chunk.strip();
        if (text.length() < 2) {
            return null;
        }
        int cut = paragraphBoundary(text, text.length() / 2);
        if (cut <= 0 || cut >= text.length()) {
            return null;
        }
        String first = text.substring(0, cut).strip();
        String second = text.substring(cut).strip();
        if (first.isEmpty() || second.isEmpty()) {
            return null;
        }
        return new String[]{first, second};
    }

    /** 调用一次模型并校验/归一化输出；input 为块原文，用于对白漏抽校验 */
    private ScriptChunkValidator.Result callAndValidate(ChatModel chatModel,
                                                        String userMessage, String input) {
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(ScriptAutoSplitPrompts.SYSTEM_PROMPT),
                new UserMessage(userMessage))));
        return ScriptChunkValidator.parseAndNormalize(response.getResult().getOutput().getText(), input);
    }

    /**
     * 落库模型转换成功的分集：写入模型给出的集标题与一句话集概要，
     * 场次 dialogues/characters 使用 {@link ScriptChunkValidator} 归一化后的
     * 标准 DialogueElement 结构（与 Agent 链路一致）。
     * convertedParts 为对半再切出的全部子块结果：场次按剧情顺序并入同一集
     * （场次号跨子块连续），标题与概要取第一个子块的非空值。
     */
    private void saveConvertedEpisode(Long scriptId, int episodeNumber, String chunkTitle,
                                      String chunk, List<JSONObject> convertedParts) {
        String title = firstNonBlank(convertedParts, "episodeTitle");
        if (title == null || title.isBlank()) {
            title = chunkTitle;
        }
        String synopsis = firstNonBlank(convertedParts, "episodeSynopsis");
        ScriptEpisode episode = insertEpisode(scriptId, episodeNumber, title, synopsis, chunk);

        JSONArray scenes = new JSONArray();
        for (JSONObject part : convertedParts) {
            JSONArray partScenes = part.getJSONArray("scenes");
            if (partScenes != null) {
                scenes.addAll(partScenes);
            }
        }
        List<ScriptSceneItem> items = new ArrayList<>();
        int index = 0;
        for (Object obj : scenes) {
            if (!(obj instanceof JSONObject sceneJson)) {
                continue;
            }
            index += 1;
            String heading = sceneJson.getStr("sceneHeading", "场次 " + index);
            // 场次号对齐 Agent 链路的"集-场"格式；前后端消费处均按展示字符串处理
            JSONArray dialogues = sceneJson.getJSONArray("dialogues");
            JSONArray characters = sceneJson.getJSONArray("characters");
            items.add(ScriptSceneItem.builder()
                    .scriptId(scriptId)
                    .episodeId(episode.getId())
                    .sceneNumber(String.format("%d-%d", episodeNumber, index))
                    .sceneHeading(heading)
                    .sceneDescription(sceneJson.getStr("sceneDescription", ""))
                    .dialogues(dialogues == null || dialogues.isEmpty() ? null : dialogues.toString())
                    .characters(characters == null || characters.isEmpty() ? null : characters.toString())
                    .status(1)
                    .build());
        }
        if (items.isEmpty()) {
            items.add(verbatimScene(scriptId, episode.getId(), episodeNumber, chunk));
        }
        persistScenes(items, episode);
    }

    private void saveVerbatimEpisode(Long scriptId, int episodeNumber, String chunkTitle, String chunk) {
        ScriptEpisode episode = insertEpisode(scriptId, episodeNumber, chunkTitle, null, chunk);
        List<ScriptSceneItem> items = List.of(verbatimScene(scriptId, episode.getId(), episodeNumber, chunk));
        persistScenes(items, episode);
    }

    private ScriptEpisode insertEpisode(Long scriptId, int episodeNumber, String title,
                                        String synopsis, String rawContent) {
        ScriptEpisode episode = ScriptEpisode.builder()
                .scriptId(scriptId)
                .episodeNumber(episodeNumber)
                .title(title.length() > 120 ? title.substring(0, 120) : title)
                .synopsis(synopsis)
                .rawContent(rawContent)
                .sortOrder(episodeNumber)
                .totalScenes(0)
                .version(1)
                .status(1)
                .build();
        episodeMapper.insert(episode);
        return episode;
    }

    /** 原文兜底场景：场景号沿用"集-场"格式（该集唯一一场），便于与转换成功的场次一致 */
    private ScriptSceneItem verbatimScene(Long scriptId, Long episodeId, int episodeNumber, String chunk) {
        return ScriptSceneItem.builder()
                .scriptId(scriptId)
                .episodeId(episodeId)
                .sceneNumber(episodeNumber + "-1")
                .sceneHeading("原文片段 1")
                .sceneDescription(chunk)
                .status(1)
                .build();
    }

    private void persistScenes(List<ScriptSceneItem> items, ScriptEpisode episode) {
        for (ScriptSceneItem item : items) {
            sceneItemMapper.insert(item);
        }
        episodeMapper.update(null, new LambdaUpdateWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getId, episode.getId())
                .set(ScriptEpisode::getTotalScenes, items.size()));
    }

    private void markParsing(Long scriptId, Long projectId, int status, String progress) {
        scriptMapper.update(null, new LambdaUpdateWrapper<Script>()
                .eq(Script::getId, scriptId)
                .set(Script::getParsingStatus, status)
                .set(Script::getParsingProgress, progress)
                // 显式刷新更新时间：UpdateWrapper 更新不触发自动填充，
                // 让滞留恢复的 update_time 阈值判定能跟随真实进度
                .set(Script::getUpdateTime, LocalDateTime.now()));
        // 绕过 Service 层直写数据库，必须同步失效相关缓存，避免状态轮询读到旧值
        evictScriptRelatedCaches(scriptId, projectId);
    }

    /** 失效剧本实体缓存与分集列表缓存中受解析影响的数据 */
    private void evictScriptRelatedCaches(Long scriptId, Long projectId) {
        evictCache(SCRIPT_CACHE_NAME, scriptId);
        if (projectId != null) {
            evictCache(SCRIPT_CACHE_NAME, SCRIPT_PROJECT_KEY_PREFIX + projectId);
            evictCache(EPISODE_CACHE_NAME, EPISODE_SCRIPT_KEY_PREFIX + scriptId);
        }
    }

    private void evictCache(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}

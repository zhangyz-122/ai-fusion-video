package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.mapper.script.ScriptEpisodeMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import com.stonewu.fusion.mapper.script.ScriptSceneItemMapper;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** 每块目标字符数：为小参数本地模型留足输出余量 */
    private static final int CHUNK_CHARS = 6000;
    /** 分块安全上限 */
    private static final int MAX_CHUNKS = 400;
    /** 单次调用给模型的原始块上限（防止异常超长块撑爆请求） */
    private static final int MODEL_INPUT_MAX = 9000;
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^第[0-9一二三四五六七八九十百千]+章.*$|^Chapter\\s+\\d+.*$", Pattern.CASE_INSENSITIVE);

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptSceneItemMapper sceneItemMapper;
    private final AiModelService aiModelService;
    private final AiProviderService aiProviderService;
    private final TaskStreamService taskStreamService;

    private final ExecutorService autoSplitExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "script-auto-split");
        thread.setDaemon(true);
        return thread;
    });

    public String startAutoSplit(Long scriptId, Long userId, Long modelId) {
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

        String taskId = taskStreamService.createTask(
                userId,
                script.getProjectId(),
                "script_auto_split",
                "自动分块解析 · " + script.getTitle(),
                "script",
                scriptId,
                "开始自动分块解析，共 " + script.getRawContent().length() + " 字");
        markParsing(scriptId, 1, "排队中");
        long capturedModelId = model.getId();
        autoSplitExecutor.execute(() -> {
            try {
                runAutoSplit(scriptId, userId, capturedModelId, taskId);
            } catch (Throwable t) {
                log.error("[AutoSplit] 自动分块解析失败: scriptId={}", scriptId, t);
                markParsing(scriptId, 3, "自动分块解析失败: " + t.getMessage());
                taskStreamService.fail(taskId, "自动分块解析失败: " + t.getMessage());
            }
        });
        return taskId;
    }

    private void runAutoSplit(Long scriptId, Long userId, Long modelId, String taskId) {
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

        List<String> chunks = splitIntoChunks(raw);
        int total = chunks.size();
        log.info("[AutoSplit] 开始自动分块解析: scriptId={}, chunks={}, model={}",
                scriptId, total, model.getName());
        taskStreamService.publishContent(taskId, "已分块：" + total + " 块");

        int episodeNumber = 0;
        for (int i = 0; i < total; i++) {
            markParsing(scriptId, 1, "正在解析分块 " + (i + 1) + "/" + total);
            String chunk = chunks.get(i);
            String chunkTitle = chunkTitle(chunks, i);
            episodeNumber += 1;
            try {
                JSONObject converted = convertChunk(chatModel, model.getName(), chunkTitle, chunk);
                saveConvertedEpisode(scriptId, episodeNumber, chunkTitle, chunk, converted);
            } catch (Exception convertFailure) {
                // 单块转换失败不中断整体：原文兜底保存为该块的场景
                log.warn("[AutoSplit] 分块 {} 转换失败，原文兜底保存: {}", i + 1, convertFailure.getMessage());
                saveVerbatimEpisode(scriptId, episodeNumber, chunkTitle, chunk);
            }
            taskStreamService.publishContent(taskId, "进度：" + (i + 1) + "/" + total);
        }

        markParsing(scriptId, 2, "解析完成：共 " + episodeNumber + " 集");
        scriptMapper.update(null, new LambdaUpdateWrapper<Script>()
                .eq(Script::getId, scriptId)
                .set(Script::getTotalEpisodes, episodeNumber));
        taskStreamService.complete(taskId, "自动分块解析完成，共 " + episodeNumber + " 集");
        log.info("[AutoSplit] 自动分块解析完成: scriptId={}, episodes={}", scriptId, episodeNumber);
    }

    /**
     * 章节感知分块：优先按 第X章/Chapter N 切分，再按段落边界切成不超过
     * CHUNK_CHARS 的原子片段后相邻聚合。任何块都不超过 MODEL_INPUT_MAX，
     * 确保调用模型时不需要截断、不丢字。
     */
    List<String> splitIntoChunks(String raw) {
        // 1. 切原子片段：章节边界 → 段落边界，每片 ≤ CHUNK_CHARS
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
            while (rest.length() > CHUNK_CHARS) {
                int cut = paragraphBoundary(rest, CHUNK_CHARS);
                pieces.add(rest.substring(0, cut));
                rest = rest.substring(cut).strip();
            }
            if (!rest.isEmpty()) {
                pieces.add(rest);
            }
        }

        // 2. 相邻原子片段聚合为不超过 CHUNK_CHARS 的块
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + piece.length() + 2 > CHUNK_CHARS) {
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

    private int paragraphBoundary(String text, int target) {
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

    /** 逐块调用模型改写为结构化场次，输出 JSON。 */
    private JSONObject convertChunk(ChatModel chatModel, String modelName, String chunkTitle, String chunk) {
        String input = chunk.length() > MODEL_INPUT_MAX ? chunk.substring(0, MODEL_INPUT_MAX) : chunk;
        String system = """
                你是剧本改编引擎。把输入的故事片段改写为结构化剧本场次。只输出 JSON，不要任何解释或代码块标记。
                输出格式：
                {"episodeTitle":"本块对应的剧集标题","scenes":[{"sceneHeading":"场次标题","sceneDescription":"场景与剧情描述","dialogues":[{"speaker":"角色名","line":"台词"}]}]}
                规则：忠于原文剧情，不虚构新主线；无对白的段落 dialogues 为空数组；场次数量按剧情自然划分。
                """;
        String user = "剧集标题参考：" + chunkTitle + "\n\n故事片段：\n" + input;
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(system),
                new UserMessage(user))));
        String text = response.getResult().getOutput().getText();
        return extractJsonObject(text);
    }

    private JSONObject extractJsonObject(String text) {
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
        return JSONUtil.parseObj(cleaned.substring(start, end + 1));
    }

    private void saveConvertedEpisode(Long scriptId, int episodeNumber, String chunkTitle,
                                      String chunk, JSONObject converted) {
        String title = converted.getStr("episodeTitle", chunkTitle);
        if (title == null || title.isBlank()) {
            title = chunkTitle;
        }
        ScriptEpisode episode = insertEpisode(scriptId, episodeNumber, title, chunk);

        JSONArray scenes = converted.getJSONArray("scenes");
        List<ScriptSceneItem> items = new ArrayList<>();
        int index = 0;
        if (scenes != null) {
            for (Object obj : scenes) {
                if (!(obj instanceof JSONObject sceneJson)) {
                    continue;
                }
                index += 1;
                String heading = sceneJson.getStr("sceneHeading", "场次 " + index);
                String description = sceneJson.getStr("sceneDescription", "");
                StringBuilder descriptionBuilder = new StringBuilder(description == null ? "" : description);
                JSONArray dialogues = sceneJson.getJSONArray("dialogues");
                if (dialogues != null && !dialogues.isEmpty()) {
                    descriptionBuilder.append("\n\n对白：");
                    for (Object d : dialogues) {
                        if (d instanceof JSONObject dialogue) {
                            descriptionBuilder.append("\n").append(dialogue.getStr("speaker", ""))
                                    .append("：").append(dialogue.getStr("line", ""));
                        }
                    }
                }
                items.add(ScriptSceneItem.builder()
                        .scriptId(scriptId)
                        .episodeId(episode.getId())
                        .sceneNumber(String.valueOf(index))
                        .sceneHeading(heading)
                        .sceneDescription(descriptionBuilder.toString())
                        .dialogues(dialogues == null ? null : dialogues.toString())
                        .status(1)
                        .build());
            }
        }
        if (items.isEmpty()) {
            items.add(verbatimScene(scriptId, episode.getId(), 1, chunk));
        }
        persistScenes(items, episode);
    }

    private void saveVerbatimEpisode(Long scriptId, int episodeNumber, String chunkTitle, String chunk) {
        ScriptEpisode episode = insertEpisode(scriptId, episodeNumber, chunkTitle, chunk);
        List<ScriptSceneItem> items = List.of(verbatimScene(scriptId, episode.getId(), 1, chunk));
        persistScenes(items, episode);
    }

    private ScriptEpisode insertEpisode(Long scriptId, int episodeNumber, String title, String rawContent) {
        ScriptEpisode episode = ScriptEpisode.builder()
                .scriptId(scriptId)
                .episodeNumber(episodeNumber)
                .title(title.length() > 120 ? title.substring(0, 120) : title)
                .rawContent(rawContent)
                .sortOrder(episodeNumber)
                .totalScenes(0)
                .version(1)
                .status(1)
                .build();
        episodeMapper.insert(episode);
        return episode;
    }

    private ScriptSceneItem verbatimScene(Long scriptId, Long episodeId, int index, String chunk) {
        return ScriptSceneItem.builder()
                .scriptId(scriptId)
                .episodeId(episodeId)
                .sceneNumber(String.valueOf(index))
                .sceneHeading("原文片段 " + index)
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

    private void markParsing(Long scriptId, int status, String progress) {
        scriptMapper.update(null, new LambdaUpdateWrapper<Script>()
                .eq(Script::getId, scriptId)
                .set(Script::getParsingStatus, status)
                .set(Script::getParsingProgress, progress));
    }
}

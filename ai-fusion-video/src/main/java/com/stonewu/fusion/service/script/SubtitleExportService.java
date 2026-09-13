package com.stonewu.fusion.service.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕导出服务：把分集场次对白组装成 SRT 字幕。
 * <p>
 * 对白来源按场次依次解析：优先取 dialogues JSON（type=1 对白 / type=3 画外音），
 * 没有可说话条目时改从 sceneDescription 中解析“角色:台词”行。
 * 时间轴不做语音对齐，按对白条数均匀分配，每条对白固定展示 secondsPerLine 秒。
 */
@Service
@RequiredArgsConstructor
public class SubtitleExportService {

    /** 每条对白默认展示时长（秒） */
    public static final int DEFAULT_SECONDS_PER_LINE = 3;
    /** 单条对白展示时长下限（秒） */
    public static final int MIN_SECONDS_PER_LINE = 1;
    /** 单条对白展示时长上限（秒） */
    public static final int MAX_SECONDS_PER_LINE = 60;

    /**
     * 场次描述中的“角色:台词”行：说话人限 1-16 个字且不含常见句读，
     * 避免把“夜里，他说：走吧”这类叙述句整行误判为对白。
     */
    private static final Pattern DESCRIPTION_DIALOGUE_LINE =
            Pattern.compile("^([^:：，。；！？、\\n]{1,16})[：:]\\s*(.+)$");

    /**
     * 场次描述中的独立说话人标签行，如“**陈砚：**”（台词通常在下一个非空行）。
     * 必须带 Markdown 加粗符号，避免把“可以按这个节奏做：”这类普通叙述行误判为说话人。
     */
    private static final Pattern STANDALONE_SPEAKER_LABEL =
            Pattern.compile("^\\*\\*\\s*【?([^*：:】\\n]{1,16})】?\\s*[：:]\\s*\\*{0,2}$");

    /** 文件名中不允许出现、需要剔除的字符 */
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\r\\n\\t]");

    /** 纯括号说明（如“（低声）”“（无）”）不能单独成字幕 */
    private static final Pattern PARENTHETICAL_ONLY = Pattern.compile("^[（(][^（）()]*[）)]$");

    /** 场次描述中的制作指令标签（如“**画面：**”），不是台词 */
    private static final Set<String> DESCRIPTION_META_SPEAKERS = Set.of(
            "画面", "台词", "动作", "镜头", "音乐", "音效", "转场",
            "场景", "时间", "地点", "人物", "角色", "说明", "备注",
            "内景", "外景", "道具", "服装", "氛围", "风格");

    private static final Set<String> NON_CONTENT_PLACEHOLDERS =
            Set.of("无", "-", "—", "--");

    private final ScriptService scriptService;

    /**
     * 生成整集 SRT 文本：场次按 sortOrder 顺序拼接，时间轴按对白条数均匀分配。
     *
     * @throws BusinessException 分集没有任何可导出的对白时抛出
     */
    public String buildEpisodeSrt(Long episodeId, Integer secondsPerLine) {
        int perLineSeconds = normalizeSecondsPerLine(secondsPerLine);
        List<ScriptSceneItem> scenes = scriptService.listScenesByEpisode(episodeId);
        List<SubtitleLine> lines = extractLines(scenes);
        if (lines.isEmpty()) {
            throw new BusinessException(400, "该分集暂无可导出的对白");
        }
        return buildSrt(lines, perLineSeconds);
    }

    /** 校验并归一化单条对白展示时长，未传时取默认 3 秒。 */
    public int normalizeSecondsPerLine(Integer secondsPerLine) {
        if (secondsPerLine == null) {
            return DEFAULT_SECONDS_PER_LINE;
        }
        if (secondsPerLine < MIN_SECONDS_PER_LINE || secondsPerLine > MAX_SECONDS_PER_LINE) {
            throw new BusinessException(400, String.format(
                    "每条对白展示时长需在 %d-%d 秒之间", MIN_SECONDS_PER_LINE, MAX_SECONDS_PER_LINE));
        }
        return secondsPerLine;
    }

    /**
     * 按场次顺序抽取全部可成字幕的对白行。
     * 单个场次优先使用 dialogues JSON；JSON 缺失、不可解析或不含可说话条目时，
     * 改从 sceneDescription 解析“角色:台词”行。
     */
    public List<SubtitleLine> extractLines(List<ScriptSceneItem> scenes) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (scenes == null || scenes.isEmpty()) {
            return lines;
        }
        for (ScriptSceneItem scene : scenes) {
            if (scene == null) {
                continue;
            }
            List<SubtitleLine> structured = extractFromDialogues(scene.getDialogues());
            if (!structured.isEmpty()) {
                lines.addAll(structured);
            } else {
                lines.addAll(extractFromDescription(scene.getSceneDescription()));
            }
        }
        return lines;
    }

    /**
     * 解析 dialogues JSON。仅 type=1（对白）与 type=3（画外音）可成字幕；
     * 未写 type 的条目（如自动拆分产物 {"speaker","line"}）按对白处理。
     * 说话人字段兼容 character_name / character / speaker，文本字段兼容 content / line。
     */
    public List<SubtitleLine> extractFromDialogues(String dialoguesJson) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (dialoguesJson == null || dialoguesJson.isBlank()) {
            return lines;
        }
        JSONArray array;
        try {
            array = JSONUtil.parseArray(dialoguesJson);
        } catch (RuntimeException e) {
            // 非法 JSON 不阻断导出，该场次由场次描述行承担字幕来源
            return lines;
        }
        for (Object item : array) {
            if (!(item instanceof JSONObject dialogue)) {
                continue;
            }
            String type = dialogue.getStr("type", "");
            if (!type.isBlank() && !"1".equals(type.trim()) && !"3".equals(type.trim())) {
                continue;
            }
            String speaker = firstNonBlank(
                    dialogue.getStr("character_name"),
                    dialogue.getStr("character"),
                    dialogue.getStr("speaker"));
            String content = trimToNull(firstNonBlank(
                    dialogue.getStr("content"),
                    dialogue.getStr("line")));
            if (content == null) {
                continue;
            }
            lines.add(new SubtitleLine(trimToNull(speaker), content));
        }
        return lines;
    }

    /**
     * 从场次描述解析对白，支持两种真实数据形态：
     * <ul>
     * <li>同行式：“张三：你好。”（半角/全角冒号均可）；</li>
     * <li>标签块式：“**陈砚：**”独立成行，台词在下一个非空行（AI 生成的场次描述常见格式）。</li>
     * </ul>
     * “画面：”“生成提示词：”等制作指令标签不产出字幕；
     * “旁白：”“字幕：”类标签的文本作为无说话人的叙述字幕保留。
     */
    public List<SubtitleLine> extractFromDescription(String sceneDescription) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (sceneDescription == null || sceneDescription.isBlank()) {
            return lines;
        }
        String[] rawLines = sceneDescription.split("\\r?\\n");
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // 形态一：同一行内的“角色：台词”
            Matcher sameLine = DESCRIPTION_DIALOGUE_LINE.matcher(line);
            if (sameLine.matches()) {
                String speaker = normalizeSpeakerLabel(sameLine.group(1));
                String content = normalizeSubtitleContent(sameLine.group(2));
                SubtitleLine subtitleLine = buildDescriptionLine(speaker, content);
                if (subtitleLine != null) {
                    lines.add(subtitleLine);
                    continue;
                }
            }

            // 形态二：独立标签行（如“**陈砚：**”），台词取下一个非空行
            String label = matchStandaloneLabel(line);
            if (label == null) {
                continue;
            }
            String content = normalizeSubtitleContent(nextNonEmptyLine(rawLines, i + 1));
            SubtitleLine subtitleLine = buildDescriptionLine(label, content);
            if (subtitleLine != null) {
                lines.add(subtitleLine);
            }
        }
        return lines;
    }

    /** 按“说话人标签 + 台词文本”组装字幕行；制作指令标签或空台词返回 null。 */
    private static SubtitleLine buildDescriptionLine(String speaker, String content) {
        if (speaker == null || content == null) {
            return null;
        }
        if (DESCRIPTION_META_SPEAKERS.contains(speaker) || speaker.endsWith("提示词")) {
            return null;
        }
        String resolvedSpeaker = isNarrationLabel(speaker) ? null : speaker;
        return new SubtitleLine(resolvedSpeaker, content);
    }

    /** “旁白：”“字幕：”类标签的文本属于叙述内容，导出时不加说话人前缀。 */
    private static boolean isNarrationLabel(String speaker) {
        return speaker.contains("旁白") || speaker.contains("字幕");
    }

    /** 归一化说话人标签：剔除 Markdown 强调符号与结尾冒号。 */
    private static String normalizeSpeakerLabel(String raw) {
        String cleaned = raw == null ? "" : raw.replace("*", "").trim();
        cleaned = cleaned.replaceAll("[：:]$", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 归一化台词文本：剔除 Markdown 符号与外侧引号；
     * 空文本、“（无）”占位与纯括号说明都不算台词。
     */
    private static String normalizeSubtitleContent(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("*", "").trim();
        cleaned = cleaned.replaceAll("^[\"'“”「『]+", "").replaceAll("[\"'“”」』]+$", "").trim();
        if (cleaned.isEmpty() || NON_CONTENT_PLACEHOLDERS.contains(cleaned)
                || PARENTHETICAL_ONLY.matcher(cleaned).matches()) {
            return null;
        }
        return cleaned;
    }

    /** 判断该行是否为独立说话人标签（如“**陈砚：**”），命中时返回归一化标签。 */
    private static String matchStandaloneLabel(String line) {
        Matcher matcher = STANDALONE_SPEAKER_LABEL.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String label = matcher.group(1).trim();
        return label.isEmpty() ? null : label;
    }

    /** 取 fromIndex 起第一个非空行，找不到返回 null。 */
    private static String nextNonEmptyLine(String[] rawLines, int fromIndex) {
        for (int i = fromIndex; i < rawLines.length; i++) {
            String line = rawLines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return null;
    }

    /**
     * 按均匀时间轴渲染 SRT：第 i 条对白占据 [i*时长, (i+1)*时长)。
     * 空对白返回空字符串。
     */
    public String buildSrt(List<SubtitleLine> lines, int secondsPerLine) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        long perLineMillis = secondsPerLine * 1000L;
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            long startMillis = i * perLineMillis;
            srt.append(i + 1).append('\n')
                    .append(formatSrtTimestamp(startMillis)).append(" --> ")
                    .append(formatSrtTimestamp(startMillis + perLineMillis)).append('\n')
                    .append(lines.get(i).displayText()).append('\n')
                    .append('\n');
        }
        return srt.toString();
    }

    /** 渲染 SRT 时间轴：HH:mm:ss,SSS（毫秒分隔符为英文逗号）。 */
    public String formatSrtTimestamp(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%02d:%02d:%02d,%03d",
                totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60, millis % 1000);
    }

    /**
     * 生成下载文件名：“第N集_标题.srt”；标题缺失或全部为非法字符时仅用“第N集.srt”。
     * 标题中的路径分隔符等非法字符被剔除，空白折叠为下划线，超长截断到 60 字。
     */
    public String buildSubtitleFilename(ScriptEpisode episode) {
        String base = "第%d集".formatted(
                episode != null && episode.getEpisodeNumber() != null
                        ? episode.getEpisodeNumber()
                        : (episode != null && episode.getId() != null ? episode.getId() : 0));
        if (episode == null || episode.getTitle() == null || episode.getTitle().isBlank()) {
            return base + ".srt";
        }
        String safeTitle = ILLEGAL_FILENAME_CHARS.matcher(episode.getTitle().trim()).replaceAll("");
        safeTitle = safeTitle.replaceAll("\\s+", "_");
        if (safeTitle.isBlank()) {
            return base + ".srt";
        }
        if (safeTitle.length() > 60) {
            safeTitle = safeTitle.substring(0, 60);
        }
        return base + "_" + safeTitle + ".srt";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 单条字幕行：speaker 可为空（此时仅输出台词文本）。 */
    public record SubtitleLine(String speaker, String content) {

        /** SRT 文本行：有说话人时渲染为“说话人：台词”。 */
        public String displayText() {
            return speaker == null || speaker.isBlank() ? content : speaker + "：" + content;
        }
    }
}

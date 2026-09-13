package com.stonewu.fusion.service.script;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.mapper.script.ScriptSceneItemMapper;
import com.stonewu.fusion.mapper.script.ScriptEpisodeMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 剧本服务（含分集、分场次管理）
 */
@Service
@RequiredArgsConstructor
public class ScriptService {

    private static final Pattern EPISODE_HEADING = Pattern.compile(
            "(?m)^#\\s*第\\s*([0-9一二三四五六七八九十百]+)\\s*集\\s*[：:|｜-]?\\s*(.*)$");
    private static final Pattern SCENE_HEADING = Pattern.compile(
            "(?m)^##\\s*(镜头|场景|场次)\\s*([0-9一二三四五六七八九十百]+)?\\s*[｜|：:.-]?\\s*(.*)$");

    /**
     * MySQL TEXT 字段最多约 64KB；中文按 UTF-8 保存时一个字符可能占 3 个字节。
     * 留出余量，避免超长原文作为单个场景描述写入时触发 Data too long。
     */
    private static final int MAX_SCENE_DESCRIPTION_CHARS = 12000;

    /** BeanUtil 更新时需要排除的基础字段（不应由前端覆盖） */
    private static final String[] IGNORE_FIELDS = {
            "id", "projectId", "title", "scope", "ownerType", "ownerId",
            "createTime", "updateTime", "deleted"
    };

    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptSceneItemMapper sceneItemMapper;

    // ========== 剧本 ==========

    @Cacheable(value = "script", key = "#id")
    public Script getById(Long id) {
        Script script = scriptMapper.selectById(id);
        if (script == null)
            throw new BusinessException("剧本不存在: " + id);
        return script;
    }

    @Cacheable(value = "script", key = "'project:' + #projectId", unless = "#result == null")
    public Script getByProjectId(Long projectId) {
        return scriptMapper.selectOne(new LambdaQueryWrapper<Script>()
                .eq(Script::getProjectId, projectId)
                .orderByDesc(Script::getCreateTime)
                .orderByDesc(Script::getId)
                .last("LIMIT 1"));
    }

    @CacheEvict(value = "script", allEntries = true)
    @Transactional
    public Script update(Script script) {
        Script existing = getById(script.getId());
        BeanUtil.copyProperties(script, existing,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties(IGNORE_FIELDS));
        int rows = scriptMapper.updateById(existing);
        if (rows == 0) {
            throw new BusinessException("更新失败，数据已被其他操作修改，请刷新后重试");
        }
        return existing;
    }

    @CacheEvict(value = "script", allEntries = true)
    @Transactional
    public void updateParsingStatus(Long scriptId, Integer status, String progress) {
        Script script = getById(scriptId);
        script.setParsingStatus(status);
        script.setParsingProgress(progress);
        scriptMapper.updateById(script);
    }

    /**
     * 本地模型未执行保存工具时的确定性兜底解析。
     * 只按原文中已有的集数和镜头标题切分，不生成任何剧情内容。
     */
    @CacheEvict(value = { "script", "episode", "scene" }, allEntries = true, beforeInvocation = true)
    @Transactional
    public Script fallbackParseStructure(Long scriptId) {
        // 行锁防止并发解析（AI 恢复与用户重试同时触发）重复创建分集
        Script script = scriptMapper.selectOne(new LambdaQueryWrapper<Script>()
                .eq(Script::getId, scriptId)
                .last("FOR UPDATE"));
        if (script == null) {
            throw new BusinessException("剧本不存在: " + scriptId);
        }
        String raw = script.getRawContent();
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("剧本原文为空，无法解析");
        }

        List<ScriptEpisode> existingEpisodes = listEpisodes(scriptId);
        if (existingEpisodes.isEmpty()) {
            List<TextBlock> episodeBlocks = splitBlocks(raw, EPISODE_HEADING, true);
            boolean structured = !episodeBlocks.isEmpty();
            if (episodeBlocks.isEmpty()) {
                episodeBlocks = List.of(new TextBlock(1, "第1集", raw));
            }
            for (TextBlock block : episodeBlocks) {
                ScriptEpisode episode = saveEpisode(
                        scriptId,
                        block.number(),
                        block.title(),
                        null,
                        block.content(),
                        2,
                        block.number());
                saveScenesFromText(episode, block.content());
            }
            script.setParsingProgress(structured
                    ? "解析完成"
                    : "未检测到“第X集/场次”分集标题，原文已按单集保存；结构化解析需要剧本格式文本或使用小说转剧本流程");
        } else {
            for (ScriptEpisode episode : existingEpisodes) {
                if (episode.getTotalScenes() == null || episode.getTotalScenes() == 0) {
                    saveScenesFromText(episode, episode.getRawContent());
                }
            }
            script.setParsingProgress("解析完成");
        }

        // 原文已经完整保存在 mediumtext 的 raw_content 中；content 是 TEXT，
        // 超长剧本不能再次整段写入，否则会在解析完成收尾时触发 Data too long。
        script.setContent(raw.length() <= MAX_SCENE_DESCRIPTION_CHARS ? raw : null);
        script.setTotalEpisodes(listEpisodes(scriptId).size());
        script.setParsingStatus(2);
        scriptMapper.updateById(script);
        return script;
    }

    private void saveScenesFromText(ScriptEpisode episode, String episodeText) {
        if (episodeText == null || episodeText.isBlank()
                || !listScenesByEpisode(episode.getId()).isEmpty()) {
            return;
        }
        List<TextBlock> sceneBlocks = splitBlocks(episodeText, SCENE_HEADING, false);
        if (sceneBlocks.isEmpty()) {
            sceneBlocks = List.of(new TextBlock(1, episode.getTitle(), episodeText));
        }
        List<ScriptSceneItem> scenes = new ArrayList<>();
        for (TextBlock block : sceneBlocks) {
            List<String> descriptionChunks = splitSceneDescription(block.content());
            for (int i = 0; i < descriptionChunks.size(); i++) {
                String heading = block.title();
                if (descriptionChunks.size() > 1) {
                    heading = heading + "（片段 " + (i + 1) + "/" + descriptionChunks.size() + "）";
                }
                scenes.add(ScriptSceneItem.builder()
                        .sceneHeading(heading)
                        .sceneDescription(descriptionChunks.get(i))
                        .status(1)
                        .build());
            }
        }
        batchSaveSceneItems(episode.getId(), episode.getVersion(), scenes, true);
    }

    /**
     * 将没有明确镜头标题的超长原文拆成多个可保存的场景描述。
     * 优先在段落/换行处切分，只有单行本身超长时才按长度硬切，确保原文不被截断。
     */
    private List<String> splitSceneDescription(String text) {
        String source = text == null ? "" : text.trim();
        if (source.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int remaining = source.length() - start;
            if (remaining <= MAX_SCENE_DESCRIPTION_CHARS) {
                chunks.add(source.substring(start).trim());
                break;
            }

            int end = start + MAX_SCENE_DESCRIPTION_CHARS;
            int split = source.lastIndexOf("\n\n", end);
            if (split <= start + MAX_SCENE_DESCRIPTION_CHARS / 2) {
                split = source.lastIndexOf('\n', end);
            }
            if (split <= start + MAX_SCENE_DESCRIPTION_CHARS / 2) {
                split = source.lastIndexOf('。', end) + 1;
            }
            if (split <= start + MAX_SCENE_DESCRIPTION_CHARS / 2) {
                split = end;
            }

            String chunk = source.substring(start, split).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = split;
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) {
                start++;
            }
        }
        return chunks;
    }

    private List<TextBlock> splitBlocks(String text, Pattern pattern, boolean episode) {
        Matcher matcher = pattern.matcher(text);
        List<TextBlock> blocks = new ArrayList<>();
        List<MatchBlock> matches = new ArrayList<>();
        while (matcher.find()) {
            int number = parseChineseNumber(matcher.group(2 - (episode ? 1 : 0)));
            String label = matcher.group(0).trim();
            String title = matcher.group(matcher.groupCount()).trim();
            if (episode) {
                title = title.isBlank() ? label : label;
            }
            matches.add(new MatchBlock(number > 0 ? number : matches.size() + 1,
                    title.isBlank() ? label : title, matcher.start()));
        }
        for (int i = 0; i < matches.size(); i++) {
            MatchBlock current = matches.get(i);
            int end = i + 1 < matches.size() ? matches.get(i + 1).start() : text.length();
            String content = text.substring(current.start(), end);
            int lineEnd = content.indexOf('\n');
            if (lineEnd >= 0) {
                content = content.substring(lineEnd + 1);
            }
            blocks.add(new TextBlock(current.number(), current.title(), content));
        }
        return blocks;
    }

    private int parseChineseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            String digits = "零一二三四五六七八九";
            int total = 0;
            int current = 0;
            for (char c : value.toCharArray()) {
                if (c == '十') {
                    total += (current == 0 ? 1 : current) * 10;
                    current = 0;
                } else if (c == '百') {
                    total += (current == 0 ? 1 : current) * 100;
                    current = 0;
                } else {
                    int digit = digits.indexOf(c);
                    if (digit >= 0) current = digit;
                }
            }
            return total + current;
        }
    }

    private record MatchBlock(int number, String title, int start) { }
    private record TextBlock(int number, String title, String content) { }

    @CacheEvict(value = { "script", "episode", "scene" }, allEntries = true)
    @Transactional
    public Script replaceSourceAndReset(Long scriptId, String rawContent) {
        getById(scriptId);
        sceneItemMapper.delete(new LambdaQueryWrapper<ScriptSceneItem>()
                .eq(ScriptSceneItem::getScriptId, scriptId));
        episodeMapper.delete(new LambdaQueryWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getScriptId, scriptId));

        UpdateWrapper<Script> update = new UpdateWrapper<Script>()
                .eq("id", scriptId)
                .set("raw_content", rawContent)
                .set("content", null)
                .set("total_episodes", 0)
                .set("story_synopsis", null)
                .set("characters_json", null)
                .set("parsing_status", 0)
                .set("parsing_progress", null)
                .set("summary", null)
                .set("genre", null)
                .set("target_audience", null)
                .set("duration_estimate", null)
                .setSql("version = version + 1");
        scriptMapper.update(null, update);
        return getById(scriptId);
    }

    // ========== 分集 ==========

    @Cacheable(value = "episode", key = "#id")
    public ScriptEpisode getEpisodeById(Long id) {
        ScriptEpisode ep = episodeMapper.selectById(id);
        if (ep == null)
            throw new BusinessException("分集不存在: " + id);
        return ep;
    }

    @Cacheable(value = "episode", key = "'script:' + #scriptId")
    public List<ScriptEpisode> listEpisodes(Long scriptId) {
        return episodeMapper.selectList(new LambdaQueryWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getScriptId, scriptId)
                .orderByAsc(ScriptEpisode::getSortOrder)
                .orderByAsc(ScriptEpisode::getEpisodeNumber));
    }

    @CacheEvict(value = "episode", allEntries = true)
    @Transactional
    public ScriptEpisode createEpisode(ScriptEpisode episode) {
        episodeMapper.insert(episode);
        return episode;
    }

    @CacheEvict(value = "episode", allEntries = true)
    @Transactional
    public ScriptEpisode updateEpisode(ScriptEpisode episode) {
        ScriptEpisode existing = getEpisodeById(episode.getId());
        BeanUtil.copyProperties(episode, existing,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties(IGNORE_FIELDS));
        int rows = episodeMapper.updateById(existing);
        if (rows == 0) {
            throw new BusinessException("更新失败，数据已被其他操作修改，请刷新后重试");
        }
        return existing;
    }

    @CacheEvict(value = { "episode", "scene" }, allEntries = true)
    @Transactional
    public void deleteEpisode(Long id) {
        getEpisodeById(id);
        sceneItemMapper.delete(new LambdaQueryWrapper<ScriptSceneItem>()
                .eq(ScriptSceneItem::getEpisodeId, id));
        episodeMapper.deleteById(id);
    }

    // ========== 分场次 ==========

    @Cacheable(value = "scene", key = "#id")
    public ScriptSceneItem getSceneById(Long id) {
        ScriptSceneItem scene = sceneItemMapper.selectById(id);
        if (scene == null)
            throw new BusinessException("场次不存在: " + id);
        return scene;
    }

    @Cacheable(value = "scene", key = "'episode:' + #episodeId")
    public List<ScriptSceneItem> listScenesByEpisode(Long episodeId) {
        return sceneItemMapper.selectList(new LambdaQueryWrapper<ScriptSceneItem>()
                .eq(ScriptSceneItem::getEpisodeId, episodeId)
                .orderByAsc(ScriptSceneItem::getSortOrder));
    }

    public List<ScriptSceneItem> listScenesByScript(Long scriptId) {
        return sceneItemMapper.selectList(new LambdaQueryWrapper<ScriptSceneItem>()
                .eq(ScriptSceneItem::getScriptId, scriptId)
                .orderByAsc(ScriptSceneItem::getSortOrder));
    }

    @CacheEvict(value = "scene", allEntries = true)
    @Transactional
    public ScriptSceneItem createScene(ScriptSceneItem scene) {
        sceneItemMapper.insert(scene);
        return scene;
    }

    @CacheEvict(value = "scene", allEntries = true)
    @Transactional
    public ScriptSceneItem updateScene(ScriptSceneItem scene) {
        // 读取数据库中的完整记录（含正确的 version，乐观锁需要）
        ScriptSceneItem existing = getSceneById(scene.getId());
        BeanUtil.copyProperties(scene, existing,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties(IGNORE_FIELDS));
        int rows = sceneItemMapper.updateById(existing);
        if (rows == 0) {
            throw new BusinessException("更新失败，数据已被其他操作修改，请刷新后重试");
        }
        return existing;
    }

    @CacheEvict(value = "scene", allEntries = true)
    @Transactional
    public void deleteScene(Long id) {
        sceneItemMapper.deleteById(id);
    }

    // ========== AI 工具支撑方法 ==========

    @CacheEvict(value = "episode", allEntries = true)
    @Transactional
    public ScriptEpisode saveEpisode(Long scriptId, Integer episodeNumber, String title,
            String synopsis, String rawContent, Integer sourceType) {
        return saveEpisode(scriptId, episodeNumber, title, synopsis, rawContent, sourceType, null);
    }

    @CacheEvict(value = "episode", allEntries = true)
    @Transactional
    public ScriptEpisode saveEpisode(Long scriptId, Integer episodeNumber, String title,
            String synopsis, String rawContent, Integer sourceType, Integer sortOrder) {
        ScriptEpisode episode = episodeMapper.selectOne(new LambdaQueryWrapper<ScriptEpisode>()
                .eq(ScriptEpisode::getScriptId, scriptId)
                .eq(ScriptEpisode::getEpisodeNumber, episodeNumber));

        if (episode != null) {
            if (title != null)
                episode.setTitle(title);
            if (synopsis != null)
                episode.setSynopsis(synopsis);
            if (rawContent != null)
                episode.setRawContent(rawContent);
            if (sourceType != null)
                episode.setSourceType(sourceType);
            if (sortOrder != null)
                episode.setSortOrder(sortOrder);
            episodeMapper.updateById(episode);
        } else {
            episode = ScriptEpisode.builder()
                    .scriptId(scriptId)
                    .episodeNumber(episodeNumber)
                    .title(title)
                    .synopsis(synopsis)
                    .rawContent(rawContent)
                    .sourceType(sourceType != null ? sourceType : 0)
                    .sortOrder(sortOrder != null ? sortOrder : episodeNumber)
                    .build();
            episodeMapper.insert(episode);
        }
        return episode;
    }

    @CacheEvict(value = { "scene", "episode" }, allEntries = true)
    @Transactional
    public void batchSaveSceneItems(Long episodeId, Integer episodeVersion, List<ScriptSceneItem> sceneItems) {
        batchSaveSceneItems(episodeId, episodeVersion, sceneItems, false);
    }

    @CacheEvict(value = { "scene", "episode" }, allEntries = true)
    @Transactional
    public void batchSaveSceneItems(Long episodeId, Integer episodeVersion, List<ScriptSceneItem> sceneItems,
            boolean overwriteMode) {
        ScriptEpisode episode = getEpisodeById(episodeId);

        // 乐观锁校验仅在覆盖模式下执行，避免多次追加调用之间发生版本冲突。
        if (overwriteMode && episodeVersion != null && !episodeVersion.equals(episode.getVersion())) {
            throw new BusinessException(String.format(
                    "版本冲突：期望版本 %d，实际版本 %d。请重新获取最新版本后再试。",
                    episodeVersion, episode.getVersion()));
        }

        int startIndex = 0;
        if (!overwriteMode) {
            // 追加模式：查询已有场次数量，从末尾继续编号
            Long existingCount = sceneItemMapper.selectCount(
                    new LambdaQueryWrapper<ScriptSceneItem>().eq(ScriptSceneItem::getEpisodeId, episodeId));
            startIndex = existingCount.intValue();
        } else {
            // 覆盖模式：删除旧场次
            sceneItemMapper
                    .delete(new LambdaQueryWrapper<ScriptSceneItem>().eq(ScriptSceneItem::getEpisodeId, episodeId));
        }

        // 写入新场次
        for (int i = 0; i < sceneItems.size(); i++) {
            ScriptSceneItem item = sceneItems.get(i);
            item.setId(null);
            item.setEpisodeId(episodeId);
            item.setScriptId(episode.getScriptId());
            item.setSortOrder(startIndex + i);
            item.setSceneNumber(String.format("%d-%d", episode.getEpisodeNumber(), startIndex + i + 1));
            sceneItemMapper.insert(item);
        }

        // 更新集的场次计数
        if (!overwriteMode) {
            // 追加模式：重新查询实际场次总数
            Long totalCount = sceneItemMapper.selectCount(
                    new LambdaQueryWrapper<ScriptSceneItem>().eq(ScriptSceneItem::getEpisodeId, episodeId));
            episode.setTotalScenes(totalCount.intValue());
        } else {
            episode.setTotalScenes(sceneItems.size());
        }
        episodeMapper.updateById(episode);
    }
}

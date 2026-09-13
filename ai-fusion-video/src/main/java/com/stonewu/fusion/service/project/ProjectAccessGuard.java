package com.stonewu.fusion.service.project;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardEpisode;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.entity.storyboard.StoryboardScene;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.script.ScriptEpisodeMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import com.stonewu.fusion.mapper.script.ScriptSceneItemMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardEpisodeMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardSceneMapper;
import com.stonewu.fusion.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 项目内容访问守卫：把被操作实体解析回所属项目，并校验当前用户可访问性。
 * 供控制器在进入业务逻辑前调用；内部服务与 Agent 工具链不经此类。
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final ProjectService projectService;
    private final ScriptMapper scriptMapper;
    private final ScriptEpisodeMapper scriptEpisodeMapper;
    private final ScriptSceneItemMapper scriptSceneItemMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardEpisodeMapper storyboardEpisodeMapper;
    private final StoryboardSceneMapper storyboardSceneMapper;
    private final StoryboardItemMapper storyboardItemMapper;
    private final AssetMapper assetMapper;
    private final AssetItemMapper assetItemMapper;
    private final ProductionRunMapper productionRunMapper;

    public void assertProject(Long projectId) {
        boolean allowed = projectId != null
                && projectService.canAccessProject(projectId, SecurityUtils.requireCurrentUserId());
        assertAllowed(allowed);
    }

    public void assertScript(Long scriptId) {
        Script script = scriptId == null ? null : scriptMapper.selectById(scriptId);
        requireEntity(script != null, "剧本不存在: " + scriptId);
        assertProject(script.getProjectId());
    }

    public void assertScriptEpisode(Long episodeId) {
        ScriptEpisode episode = episodeId == null ? null : scriptEpisodeMapper.selectById(episodeId);
        requireEntity(episode != null, "剧本分集不存在: " + episodeId);
        assertScript(episode.getScriptId());
    }

    public void assertScriptScene(Long sceneId) {
        ScriptSceneItem scene = sceneId == null ? null : scriptSceneItemMapper.selectById(sceneId);
        requireEntity(scene != null, "剧本场次不存在: " + sceneId);
        assertScript(scene.getScriptId());
    }

    public void assertStoryboard(Long storyboardId) {
        Storyboard storyboard = storyboardId == null ? null : storyboardMapper.selectById(storyboardId);
        requireEntity(storyboard != null, "分镜不存在: " + storyboardId);
        assertProject(storyboard.getProjectId());
    }

    public void assertStoryboardEpisode(Long episodeId) {
        StoryboardEpisode episode = episodeId == null ? null : storyboardEpisodeMapper.selectById(episodeId);
        requireEntity(episode != null, "分镜分集不存在: " + episodeId);
        assertStoryboard(episode.getStoryboardId());
    }

    public void assertStoryboardScene(Long sceneId) {
        StoryboardScene scene = sceneId == null ? null : storyboardSceneMapper.selectById(sceneId);
        requireEntity(scene != null, "分镜场次不存在: " + sceneId);
        assertStoryboard(scene.getStoryboardId());
    }

    public void assertStoryboardItem(Long itemId) {
        StoryboardItem item = itemId == null ? null : storyboardItemMapper.selectById(itemId);
        requireEntity(item != null, "分镜镜头不存在: " + itemId);
        assertStoryboard(item.getStoryboardId());
    }

    public void assertStoryboardItems(Iterable<Long> itemIds) {
        if (itemIds == null) {
            return;
        }
        for (Long itemId : itemIds) {
            assertStoryboardItem(itemId);
        }
    }

    public void assertAsset(Long assetId) {
        Asset asset = assetId == null ? null : assetMapper.selectById(assetId);
        requireEntity(asset != null, "资产不存在: " + assetId);
        assertProject(asset.getProjectId());
    }

    public void assertAssetItem(Long itemId) {
        AssetItem item = itemId == null ? null : assetItemMapper.selectById(itemId);
        requireEntity(item != null, "子资产不存在: " + itemId);
        assertAsset(item.getAssetId());
    }

    public void assertProductionRun(Long runId) {
        ProductionRun run = runId == null ? null : productionRunMapper.selectById(runId);
        requireEntity(run != null, "生产运行不存在: " + runId);
        assertProject(run.getProjectId());
    }

    private void requireEntity(boolean present, String message) {
        if (!present) {
            // 资源不存在使用 404 语义，由 GlobalExceptionHandler 映射 HTTP 状态；文案保持不变
            throw new BusinessException(404, message);
        }
    }

    private void assertAllowed(boolean allowed) {
        if (!allowed) {
            // 越权访问使用 403 语义，由 GlobalExceptionHandler 映射 HTTP 状态；文案保持不变
            throw new BusinessException(403, "无权访问该项目内容");
        }
    }
}

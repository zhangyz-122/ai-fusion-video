package com.stonewu.fusion.controller.script;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.script.vo.AutoSplitReqVO;
import com.stonewu.fusion.controller.script.vo.AutoSplitStatusVO;
import com.stonewu.fusion.controller.script.vo.EpisodeCreateReqVO;
import com.stonewu.fusion.controller.script.vo.EpisodeUpdateReqVO;
import com.stonewu.fusion.controller.script.vo.SceneCreateReqVO;
import com.stonewu.fusion.controller.script.vo.SceneUpdateReqVO;
import com.stonewu.fusion.controller.script.vo.ScriptSourceReplaceReqVO;
import com.stonewu.fusion.controller.script.vo.ScriptUpdateReqVO;
import com.stonewu.fusion.convert.script.ScriptConvert;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import com.stonewu.fusion.service.script.ScriptAutoSplitService;
import com.stonewu.fusion.service.script.ScriptService;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧本 Controller（含分集、分场次）
 */
@Tag(name = "剧本管理")
@RestController
@RequestMapping("/api/script")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;
    private final ProjectAccessGuard accessGuard;
    private final ScriptAutoSplitService scriptAutoSplitService;

    // ========== 剧本 ==========

    @Operation(summary = "获取剧本详情")
    @GetMapping("/{id}")
    public CommonResult<Script> get(@PathVariable Long id) {
        accessGuard.assertScript(id);
        return CommonResult.success(scriptService.getById(id));
    }

    @Operation(summary = "按项目获取唯一剧本")
    @GetMapping("/project/{projectId}")
    public CommonResult<Script> getByProject(@PathVariable Long projectId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(scriptService.getByProjectId(projectId));
    }

    @Operation(summary = "更新剧本")
    @PutMapping
    public CommonResult<Script> update(@Valid @RequestBody ScriptUpdateReqVO reqVO) {
        accessGuard.assertScript(reqVO.getId());
        Script script = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.update(script));
    }

    @Operation(summary = "替换剧本原文并重置分集与场次")
    @PutMapping("/{id}/source")
    public CommonResult<Script> replaceSource(
            @PathVariable Long id,
            @Valid @RequestBody ScriptSourceReplaceReqVO reqVO) {
        accessGuard.assertScript(id);
        return CommonResult.success(scriptService.replaceSourceAndReset(id, reqVO.getRawContent()));
    }

    @Operation(summary = "使用本地规则兜底生成剧本结构")
    @PostMapping("/{id}/fallback-parse")
    public CommonResult<Script> fallbackParse(@PathVariable Long id) {
        accessGuard.assertScript(id);
        return CommonResult.success(scriptService.fallbackParseStructure(id));
    }

    @Operation(summary = "长文本自动分块解析（章节感知分块，逐块 AI 转剧本）")
    @PostMapping("/{id}/auto-split")
    public CommonResult<String> autoSplit(@PathVariable Long id,
                                          @RequestBody(required = false) AutoSplitReqVO reqVO) {
        accessGuard.assertScript(id);
        Long userId = SecurityUtils.requireCurrentUserId();
        Long modelId = reqVO == null ? null : reqVO.getModelId();
        Integer chunkChars = reqVO == null ? null : reqVO.getChunkChars();
        return CommonResult.success(scriptAutoSplitService.startAutoSplit(id, userId, modelId, chunkChars));
    }

    @Operation(summary = "查询自动分块解析任务状态")
    @GetMapping("/{id}/auto-split")
    public CommonResult<AutoSplitStatusVO> autoSplitStatus(@PathVariable Long id) {
        accessGuard.assertScript(id);
        Script script = scriptService.getById(id);
        AutoSplitStatusVO vo = new AutoSplitStatusVO();
        vo.setParsingStatus(script.getParsingStatus());
        vo.setParsingProgress(script.getParsingProgress());
        vo.setTotalEpisodes(script.getTotalEpisodes());
        return CommonResult.success(vo);
    }

    // ========== 分集 ==========

    @Operation(summary = "获取分集列表")
    @GetMapping("/{scriptId}/episodes")
    public CommonResult<List<ScriptEpisode>> listEpisodes(@PathVariable Long scriptId) {
        accessGuard.assertScript(scriptId);
        return CommonResult.success(scriptService.listEpisodes(scriptId));
    }

    @Operation(summary = "获取分集详情")
    @GetMapping("/episode/{id}")
    public CommonResult<ScriptEpisode> getEpisode(@PathVariable Long id) {
        accessGuard.assertScriptEpisode(id);
        return CommonResult.success(scriptService.getEpisodeById(id));
    }

    @Operation(summary = "创建分集")
    @PostMapping("/episode")
    public CommonResult<ScriptEpisode> createEpisode(@Valid @RequestBody EpisodeCreateReqVO reqVO) {
        accessGuard.assertScript(reqVO.getScriptId());
        ScriptEpisode episode = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.createEpisode(episode));
    }

    @Operation(summary = "更新分集")
    @PutMapping("/episode")
    public CommonResult<ScriptEpisode> updateEpisode(@Valid @RequestBody EpisodeUpdateReqVO reqVO) {
        accessGuard.assertScriptEpisode(reqVO.getId());
        ScriptEpisode episode = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.updateEpisode(episode));
    }

    @Operation(summary = "删除分集")
    @DeleteMapping("/episode/{id}")
    public CommonResult<Boolean> deleteEpisode(@PathVariable Long id) {
        accessGuard.assertScriptEpisode(id);
        scriptService.deleteEpisode(id);
        return CommonResult.success(true);
    }

    // ========== 分场次 ==========

    @Operation(summary = "获取分场次列表（按分集）")
    @GetMapping("/episode/{episodeId}/scenes")
    public CommonResult<List<ScriptSceneItem>> listScenes(@PathVariable Long episodeId) {
        accessGuard.assertScriptEpisode(episodeId);
        return CommonResult.success(scriptService.listScenesByEpisode(episodeId));
    }

    @Operation(summary = "获取分场次详情")
    @GetMapping("/scene/{id}")
    public CommonResult<ScriptSceneItem> getScene(@PathVariable Long id) {
        accessGuard.assertScriptScene(id);
        return CommonResult.success(scriptService.getSceneById(id));
    }

    @Operation(summary = "创建分场次")
    @PostMapping("/scene")
    public CommonResult<ScriptSceneItem> createScene(@Valid @RequestBody SceneCreateReqVO reqVO) {
        if (reqVO.getScriptId() != null) {
            accessGuard.assertScript(reqVO.getScriptId());
        } else {
            accessGuard.assertScriptEpisode(reqVO.getEpisodeId());
        }
        ScriptSceneItem scene = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.createScene(scene));
    }

    @Operation(summary = "更新分场次")
    @PutMapping("/scene")
    public CommonResult<ScriptSceneItem> updateScene(@Valid @RequestBody SceneUpdateReqVO reqVO) {
        accessGuard.assertScriptScene(reqVO.getId());
        ScriptSceneItem scene = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.updateScene(scene));
    }

    @Operation(summary = "删除分场次")
    @DeleteMapping("/scene/{id}")
    public CommonResult<Boolean> deleteScene(@PathVariable Long id) {
        accessGuard.assertScriptScene(id);
        scriptService.deleteScene(id);
        return CommonResult.success(true);
    }
}

package com.stonewu.fusion.controller.production;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.production.vo.EpisodeContractReqVO;
import com.stonewu.fusion.controller.production.vo.StoryCommitReqVO;
import com.stonewu.fusion.entity.production.EpisodeContract;
import com.stonewu.fusion.entity.production.StoryEvent;
import com.stonewu.fusion.entity.production.StoryStateSnapshot;
import com.stonewu.fusion.service.production.EpisodeContractService;
import com.stonewu.fusion.service.production.EpisodeContractService.Violation;
import com.stonewu.fusion.service.production.StoryStateService;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;

/**
 * 剧情状态事实源接口（PR-021 / PR-022）。
 */
@Tag(name = "剧情状态")
@RestController
@RequestMapping("/api/production/story")
@RequiredArgsConstructor
public class StoryStateController {

    private final StoryStateService storyStateService;
    private final EpisodeContractService episodeContractService;
    private final ProjectAccessGuard accessGuard;

    @Operation(summary = "提交镜头声明的剧情状态变更")
    @PostMapping("/shots/{storyboardItemId}/commit")
    public CommonResult<List<StoryEvent>> commit(
            @PathVariable Long storyboardItemId,
            @Valid @RequestBody StoryCommitReqVO request) {
        accessGuard.assertStoryboardItem(storyboardItemId);
        return CommonResult.success(storyStateService.commitShot(
                storyboardItemId, toDeltas(request), requireCurrentUserId()));
    }

    @Operation(summary = "查询折叠后的剧情状态")
    @GetMapping("/state")
    public CommonResult<Map<String, Object>> state(
            @RequestParam Long projectId,
            @RequestParam(value = "episodeId", required = false) Long episodeId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(storyStateService.state(projectId, episodeId));
    }

    @Operation(summary = "固化剧情状态快照")
    @PostMapping("/snapshot")
    public CommonResult<StoryStateSnapshot> snapshot(
            @RequestParam Long projectId,
            @RequestParam(value = "episodeId", required = false) Long episodeId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(storyStateService.snapshot(projectId, episodeId));
    }

    @Operation(summary = "定义分集剧情契约")
    @PutMapping("/contract")
    public CommonResult<EpisodeContract> defineContract(@Valid @RequestBody EpisodeContractReqVO request) {
        accessGuard.assertProject(request.getProjectId());
        return CommonResult.success(episodeContractService.define(
                request.getProjectId(), request.getEpisodeId(),
                new EpisodeContractService.ContractInput(request.getOutputStateJson(),
                        request.getRequiredBeatsJson(), request.getMustResolveJson()),
                requireCurrentUserId()));
    }

    @Operation(summary = "查询分集剧情契约")
    @GetMapping("/contract")
    public CommonResult<EpisodeContract> currentContract(
            @RequestParam Long projectId,
            @RequestParam Long episodeId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(episodeContractService.current(projectId, episodeId));
    }

    @Operation(summary = "按已提交剧情事件校验分集契约")
    @GetMapping("/contract/lint")
    public CommonResult<List<Violation>> lintContract(
            @RequestParam Long projectId,
            @RequestParam Long episodeId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(episodeContractService.lint(projectId, episodeId));
    }

    private static List<StoryStateService.StateDelta> toDeltas(StoryCommitReqVO request) {
        return request.getDeltas().stream()
                .map(delta -> new StoryStateService.StateDelta(
                        delta.getSubjectType(), delta.getSubjectKey(), delta.getChangeKind(),
                        delta.getValue(), delta.getPredecessorEventId()))
                .toList();
    }
}

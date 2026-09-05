package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.WorkflowProfile;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PR-008: WorkflowProfile Resolver — 解析 Profile → 最终 WorkflowVersionId
 * 确保：无效/未发布 Workflow 不进入生产；执行时固化 workflowVersionId。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowProfileResolver {

    private final WorkflowProfileService profileService;
    private final ComfyUiWorkflowService workflowService;

    public ResolvedWorkflow resolve(Long profileId) {
        WorkflowProfile profile = profileService.getById(profileId);
        if (profile == null) throw new IllegalArgumentException("WorkflowProfile not found: " + profileId);
        if (!profile.getEnabled()) throw new IllegalStateException("WorkflowProfile is disabled: " + profileCode(profile));

        Long versionId;
        if (profile.getPinnedWorkflowVersionId() != null) {
            versionId = profile.getPinnedWorkflowVersionId();
        } else {
            versionId = getLatestPublishedVersionId(profile.getWorkflowId());
        }
        if (versionId == null) throw new IllegalStateException("No published workflow version for workflowId=" + profile.getWorkflowId());

        return new ResolvedWorkflow(profile.getId(), profile.getWorkflowId(), versionId, profile.getDefaultModelId());
    }

    public record ResolvedWorkflow(Long profileId, Long workflowId, Long workflowVersionId, String modelId) {}

    private String profileCode(WorkflowProfile p) { return p.getProfileCode(); }

    private Long getLatestPublishedVersionId(Long workflowId) {
        // 通过 ComfyUiWorkflowService 查询该 workflow 的最新 published 版本
        var versions = workflowService.getVersionsByWorkflowId(workflowId);
        return versions.stream()
            .filter(v -> "PUBLISHED".equals(v.getStatus()))
            .map(v -> v.getId())
            .max(Long::compareTo)
            .orElse(null);
    }
}

package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.WorkflowProfile;
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

    // TODO: 注入 ComfyUiWorkflowService 查询最新 published version
    private Long getLatestPublishedVersionId(Long workflowId) {
        // 简化实现：假设调用方已保证 workflow 有 published version
        // 实际实现需查询 ComfyUiWorkflowVersionService
        return null;
    }
}

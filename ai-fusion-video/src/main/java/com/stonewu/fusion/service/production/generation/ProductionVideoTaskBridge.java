package com.stonewu.fusion.service.production.generation;

import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.WorkflowProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PR-010: Production → Existing VideoTask Bridge
 * 将 GENERATE_VIDEO Step 转发到现有 VideoGenerationConsumer。
 * 禁止创建第二套队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionVideoTaskBridge {

    public VideoTask createVideoTaskForStep(ProductionStep step, WorkflowProfile profile,
                                             String prompt, String firstFrameUrl, String lastFrameUrl) {
        VideoTask task = new VideoTask();
        task.setPrompt(prompt);
        task.setGenerateMode("I2V");
        task.setFirstFrameImageUrl(firstFrameUrl);
        if (lastFrameUrl != null) task.setLastFrameImageUrl(lastFrameUrl);
        task.setCount(3);
        task.setModelId(profile.getDefaultModelId());
        task.setWorkflowVersionId(resolveWorkflowVersionId(profile));
        // projectId 由 ProductionRun 关联获取
        return task;
    }

    private Long resolveWorkflowVersionId(WorkflowProfile profile) {
        if (profile.getPinnedWorkflowVersionId() != null) return profile.getPinnedWorkflowVersionId();
        // 默认走 workflow_version_policy = LATEST_PUBLISHED，由 Resolver 解析
        return null;
    }
}

package com.stonewu.fusion.service.production.generation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.WorkflowProfile;
import com.stonewu.fusion.mapper.generation.VideoItemMapper;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.service.generation.video.consumer.VideoGenerationConsumer;
import com.stonewu.fusion.service.production.WorkflowProfileResolver;
import com.stonewu.fusion.service.production.WorkflowProfileResolver.ResolvedWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PR-010/011: Production Generation Service
 *
 * 核心职责：
 *   - GENERATE_VIDEO Step → VideoTask（通过现有 Consumer）
 *   - VideoItem → ProductionTake（幂等 ingest）
 *   - Take Selection → StoryboardItem.selectedTakeId（SSOT）
 *
 * 禁止：创建第二套队列 / 直接调 ComfyUI API / 绕开 VideoTask。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionGenerationService {

    private final VideoGenerationConsumer videoConsumer;
    private final VideoTaskMapper videoTaskMapper;
    private final VideoItemMapper videoItemMapper;
    private final ProductionStepMapper stepMapper;
    private final ProductionTakeMapper takeMapper;
    private final WorkflowProfileResolver profileResolver;

    /**
     * 提交 GENERATE_VIDEO Step：创建 VideoTask 并通过现有 Consumer 提交。
     * 调用方（ProductionStepService）在调用前已将 Step status 设为 QUEUED。
     */
    @Transactional
    public ProductionStep submitVideoGeneration(Long stepId, String prompt,
            String firstFrameUrl, String lastFrameUrl) {
        ProductionStep step = stepMapper.selectById(stepId);
        if (step == null) throw new IllegalArgumentException("Step not found: " + stepId);
        if (!"GENERATE_VIDEO".equals(step.getStepType())) {
            throw new IllegalArgumentException("Step is not GENERATE_VIDEO: " + step.getStepType());
        }

        // 1. 解析 WorkflowProfile → 最终 WorkflowVersionId
        //    Profile ID 从 metadata_json 获取，简化为从 Step 上下文传递
        //    P0 硬编码为 WAN_I2V_STANDARD（PR-009），后续从 Router 动态选择
        WorkflowProfile profile = resolveProfile();
        ResolvedWorkflow resolved = resolveProfile(profile);

        // 2. 创建 VideoTask
        VideoTask task = new VideoTask();
        task.setPrompt(prompt);
        task.setGenerateMode("I2V");
        task.setFirstFrameImageUrl(firstFrameUrl);
        if (lastFrameUrl != null) task.setLastFrameImageUrl(lastFrameUrl);
        task.setCount(3);
        task.setModelId(resolved.modelId());
        task.setWorkflowVersionId(resolved.workflowVersionId());
        // projectId 从 ProductionRun 关联获取（简化：由调用方预设）
        videoTaskMapper.insert(task);

        // 3. 通过现有 Consumer 提交（复用 RedisTaskQueue / 按模型队列 / capability validation）
        String taskId = videoConsumer.submitTask(task);
        log.info("[production] VideoTask submitted for step {}: taskId={}", stepId, taskId);

        // 4. 更新 Step 的 execution_ref_id → 指向 VideoTask.id
        step.setExecutionRefId(task.getId());
        step.setStatus("QUEUED");
        stepMapper.updateById(step);

        return step;
    }

    /**
     * PR-011: VideoItem → ProductionTake 幂等 Ingestion。
     * 当 VideoGenerationConsumer 完成 VideoTask 时调用（回调/轮询）。
     * VideoTask count=N → N 个 VideoItem → N 个 ProductionTake。
     */
    @Transactional
    public int ingestTakesFromVideoTask(Long videoTaskId, Long runId, Long storyboardItemId,
                                         Long profileId, Long versionId, String modelId) {
        List<VideoItem> items = videoItemMapper.selectList(
            new LambdaQueryWrapper<VideoItem>().eq(VideoItem::getTaskId, videoTaskId));
        int ingested = 0;
        for (VideoItem item : items) {
            ProductionTake existing = takeMapper.selectOne(
                new LambdaQueryWrapper<ProductionTake>()
                    .eq(ProductionTake::getSourceType, "VIDEO_ITEM")
                    .eq(ProductionTake::getSourceItemId, item.getId()));
            if (existing != null) continue; // 幂等

            ProductionTake take = new ProductionTake();
            take.setRunId(runId);
            take.setStoryboardItemId(storyboardItemId);
            take.setSourceType("VIDEO_ITEM");
            take.setSourceItemId(item.getId());
            take.setWorkflowProfileId(profileId);
            take.setWorkflowVersionId(versionId);
            take.setModelId(modelId);
            take.setSeed(null); // VideoItem 不直接存 seed
            take.setQcStatus("PENDING");
            String meta = String.format(
                "{\"videoUrl\":\"%s\",\"firstFrameUrl\":\"%s\",\"lastFrameUrl\":\"%s\",\"duration\":%s}",
                item.getVideoUrl() != null ? item.getVideoUrl() : "",
                item.getFirstFrameUrl() != null ? item.getFirstFrameUrl() : "",
                item.getLastFrameUrl() != null ? item.getLastFrameUrl() : "",
                item.getDuration() != null ? item.getDuration() : 0);
            take.setMetadataJson(meta);
            takeMapper.insert(take);
            ingested++;
        }
        log.info("[production] ingested {} takes from videoTask {}", ingested, videoTaskId);
        return ingested;
    }

    private WorkflowProfile resolveProfile() {
        // P0: 硬编码 WAN_I2V_STANDARD，后续由 WorkflowRouter 动态选择
        WorkflowProfile profile = new WorkflowProfile();
        profile.setId(1L); // WAN_I2V_STANDARD 的 id
        profile.setWorkflowId(null);
        profile.setPinnedWorkflowVersionId(null);
        profile.setDefaultModelId("wan-2.1-i2v");
        return profile;
    }

    private ResolvedWorkflow resolveProfile(WorkflowProfile profile) {
        Long versionId = profile.getPinnedWorkflowVersionId();
        return new ResolvedWorkflow(profile.getId(), profile.getWorkflowId(), versionId, profile.getDefaultModelId());
    }
}

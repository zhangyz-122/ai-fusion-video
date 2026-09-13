package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import com.stonewu.fusion.service.generation.video.consumer.VideoGenerationConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 执行已经通过 RepairRouter 批准的单次修复。
 *
 * <p>修复只复制原任务并调用现有 VideoGenerationConsumer，不创建新的队列或消费者。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductionRepairExecutor {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String FAILED = "FAILED";

    private final VideoGenerationService videoGenerationService;
    private final VideoGenerationConsumer videoGenerationConsumer;
    private final ProductionRepairAttemptMapper attemptMapper;
    private final ProductionStepMapper stepMapper;

    @Transactional
    public ExecutionResult execute(ProductionRun run, ProductionStep step,
                                   ProductionRepairAttempt attempt) {
        if (attempt == null || !ProductionRepairRouter.PLANNED.equals(attempt.getStatus())) {
            throw new BusinessException("当前修复计划不可执行");
        }
        if (step == null || step.getVideoTaskId() == null) {
            throw new BusinessException("修复步骤缺少原视频任务");
        }

        VideoTask source = videoGenerationService.getById(step.getVideoTaskId());
        VideoTask replacement = copyTask(source);
        replacement.setCategory("production-repair");

        final String executionRef;
        try {
            executionRef = videoGenerationConsumer.submitTask(replacement);
        } catch (RuntimeException exception) {
            attempt.setStatus(FAILED);
            attempt.setReason(appendReason(attempt.getReason(), exception.getMessage()));
            attemptMapper.updateById(attempt);
            throw exception;
        }

        if (replacement.getId() == null || !StringUtils.hasText(executionRef)) {
            String message = "修复任务已提交但没有返回完整任务引用";
            attempt.setStatus(FAILED);
            attempt.setReason(appendReason(attempt.getReason(), message));
            attemptMapper.updateById(attempt);
            throw new BusinessException(message);
        }

        attempt.setStatus(SUBMITTED);
        attempt.setReplacementVideoTaskId(replacement.getId());
        attempt.setReplacementExecutionRef(executionRef);
        attemptMapper.updateById(attempt);

        step.setVideoTaskId(replacement.getId());
        step.setExecutionRef(executionRef);
        step.setStatus("SUBMITTED");
        step.setAttempt((step.getAttempt() == null ? 0 : step.getAttempt()) + 1);
        step.setErrorCode(null);
        step.setErrorMessage(null);
        stepMapper.updateById(step);
        return new ExecutionResult(source, replacement, executionRef);
    }

    private VideoTask copyTask(VideoTask source) {
        return VideoTask.builder()
                .userId(source.getUserId())
                .projectId(source.getProjectId())
                .prompt(source.getPrompt())
                .promptTemplateId(source.getPromptTemplateId())
                .generateMode(source.getGenerateMode())
                .firstFrameImageUrl(source.getFirstFrameImageUrl())
                .lastFrameImageUrl(source.getLastFrameImageUrl())
                .referenceImageUrls(source.getReferenceImageUrls())
                .referenceVideoUrls(source.getReferenceVideoUrls())
                .referenceAudioUrls(source.getReferenceAudioUrls())
                .ratio(source.getRatio())
                .resolution(source.getResolution())
                .duration(source.getDuration())
                .watermark(source.getWatermark())
                .generateAudio(source.getGenerateAudio())
                .seed(source.getSeed())
                .cameraFixed(source.getCameraFixed())
                .count(source.getCount() == null ? 3 : source.getCount())
                .successCount(0)
                .status(0)
                .modelId(source.getModelId())
                .workflowVersionId(source.getWorkflowVersionId())
                .ownerType(source.getOwnerType())
                .ownerId(source.getOwnerId())
                .build();
    }

    private String appendReason(String current, String next) {
        String message = StringUtils.hasText(next) ? next.trim() : "未知修复提交错误";
        if (!StringUtils.hasText(current)) {
            return message;
        }
        String combined = current.trim() + "\n" + message;
        return combined.length() > 4000 ? combined.substring(0, 4000) : combined;
    }

    public record ExecutionResult(VideoTask sourceTask, VideoTask replacementTask,
                                  String executionRef) {
    }
}

package com.stonewu.fusion.service.generation.video.strategy.comfyui;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiExecutionContext;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiGenerationExecutor;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiPreparedSubmission;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiStoredOutput;
import com.stonewu.fusion.service.ai.comfyui.ComfyUiWorkflowService;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiJobResult;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import com.stonewu.fusion.service.generation.video.strategy.VideoGenerationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Video generation through an administrator-published ComfyUI workflow. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComfyUiVideoStrategy implements VideoGenerationStrategy {

    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 5_000L;
    private static final long DEFAULT_TIMEOUT_MILLIS = 60L * 60L * 1_000L;

    private final AiModelService aiModelService;
    private final VideoGenerationService videoGenerationService;
    private final ComfyUiGenerationExecutor executor;

    @Override
    public String getName() {
        return ComfyUiWorkflowService.PLATFORM;
    }

    @Override
    public String submit(VideoTask task) {
        AiModel model = requireModel(task);
        ComfyUiExecutionContext context = executor.resolveContext(model, task.getWorkflowVersionId());
        List<VideoItem> items = videoGenerationService.listItems(task.getId());
        if (items.isEmpty()) {
            throw new BusinessException(400, "视频任务缺少生成条目");
        }

        List<String> promptIds = new ArrayList<>(items.size());
        try {
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> values = videoValues(task, model);
                // 当前已发布工作流没有 batch/count 输出绑定。沿用同一个 VideoTask，
                // 为每个既有 VideoItem 提交一个独立 ComfyUI job，保证 count=3 真正对应 3 个候选。
                values.put("count", 1);
                values.put("seed", candidateSeed(task, index));
                String taskKey = items.size() == 1
                        ? task.getTaskId()
                        : task.getTaskId() + "-candidate-" + (index + 1);
                ComfyUiPreparedSubmission submission = executor.prepare(
                        context, taskKey, values);
                VideoItem item = items.get(index);
                item.setPlatformTaskId(submission.promptId());
                videoGenerationService.updateItem(item);
                executor.submit(submission);
                promptIds.add(submission.promptId());
            }
        } catch (RuntimeException e) {
            // 如果批量提交中途失败，尽量取消已经入队的远端 job，避免留下孤儿推理。
            for (String promptId : promptIds) {
                try {
                    executor.cancel(context, promptId);
                } catch (RuntimeException cancelError) {
                    log.warn("[ComfyUI Video] 清理部分提交任务失败: promptId={}", promptId, cancelError);
                }
            }
            throw e;
        }
        String platformTaskId = promptIds.size() == 1
                ? promptIds.get(0)
                : JSONUtil.toJsonStr(promptIds);
        log.info("[ComfyUI Video] 已提交: taskId={}, promptIds={}, workflowVersionId={}",
                task.getTaskId(), promptIds, task.getWorkflowVersionId());
        return platformTaskId;
    }

    @Override
    public void poll(String platformTaskId, VideoTask task) {
        AiModel model = requireModel(task);
        ComfyUiExecutionContext context = executor.resolveContext(model, task.getWorkflowVersionId());
        List<VideoItem> items = videoGenerationService.listItems(task.getId());
        List<String> promptIds = parsePlatformTaskIds(platformTaskId);
        if (promptIds.size() != items.size()) {
            throw new BusinessException(502,
                    "ComfyUI 任务数量与生成条目不一致，期望 " + items.size() + "，实际 " + promptIds.size());
        }

        int successCount = 0;
        for (int index = 0; index < items.size(); index++) {
            String promptId = promptIds.get(index);
            ComfyUiJobResult job = executor.waitForJob(
                    context, promptId, pollInterval(model), timeout(model));
            List<ComfyUiStoredOutput> outputs = executor.storeOutputs(context, job);
            List<ComfyUiStoredOutput> videos = outputs.stream()
                    .filter(output -> "video".equals(output.mediaType())
                            && "primary".equals(output.role()))
                    .toList();
            List<ComfyUiStoredOutput> covers = outputs.stream()
                    .filter(output -> "image".equals(output.mediaType())
                            && "cover".equals(output.role()))
                    .toList();
            if (videos.isEmpty()) {
                throw new BusinessException(502,
                        "ComfyUI 未返回第 " + (index + 1) + " 个候选视频");
            }
            VideoItem item = items.get(index);
            ComfyUiStoredOutput video = videos.get(0);
            item.setPlatformTaskId(promptId);
            item.setVideoUrl(video.url());
            item.setFileSize(video.size());
            item.setDuration(task.getDuration());
            if (!covers.isEmpty()) {
                item.setCoverUrl(covers.get(0).url());
            }
            item.setStatus(1);
            item.setErrorMsg(null);
            videoGenerationService.updateItem(item);
            successCount++;
            task.setSuccessCount(successCount);
            videoGenerationService.update(task);
        }
    }

    @Override
    public boolean cancel(String platformTaskId, VideoTask task) {
        ComfyUiExecutionContext context = executor.resolveContext(requireModel(task), task.getWorkflowVersionId());
        boolean cancelled = false;
        for (String promptId : parsePlatformTaskIds(platformTaskId)) {
            cancelled |= executor.cancel(context, promptId);
        }
        return cancelled;
    }

    @Override
    public boolean persistsResults() {
        return true;
    }

    private AiModel requireModel(VideoTask task) {
        AiModel model = task == null || task.getModelId() == null
                ? null : aiModelService.getById(task.getModelId());
        if (model == null) throw new BusinessException(404, "ComfyUI 视频模型不存在");
        return model;
    }

    private Map<String, Object> videoValues(VideoTask task, AiModel model) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt", StrUtil.blankToDefault(task.getPrompt(), ""));
        values.put("duration", task.getDuration());
        values.put("count", Math.max(1, task.getCount()));
        values.put("seed", task.getSeed() != null ? task.getSeed() : randomSeed());
        values.put("generateAudio", task.getGenerateAudio());
        int[] resolution = parseResolution(task.getResolution());
        if (resolution != null) {
            values.put("width", resolution[0]);
            values.put("height", resolution[1]);
        }
        Integer fps = configInt(model, "defaultFps");
        if (fps != null) values.put("fps", fps);
        if (StrUtil.isNotBlank(task.getFirstFrameImageUrl())) {
            values.put("firstFrame", task.getFirstFrameImageUrl());
        }
        if (StrUtil.isNotBlank(task.getLastFrameImageUrl())) {
            values.put("lastFrame", task.getLastFrameImageUrl());
        }
        List<String> images = parseList(task.getReferenceImageUrls());
        values.put("referenceImageCount", images.size());
        if (!images.isEmpty()) values.put("referenceImages", images);
        List<String> videos = parseList(task.getReferenceVideoUrls());
        if (!videos.isEmpty()) values.put("referenceVideos", videos);
        List<String> audios = parseList(task.getReferenceAudioUrls());
        if (!audios.isEmpty()) values.put("referenceAudios", audios);
        return values;
    }

    private List<String> parseList(String json) {
        if (StrUtil.isBlank(json)) return List.of();
        try {
            return new ArrayList<>(JSONUtil.parseArray(json).toList(String.class));
        } catch (RuntimeException e) {
            throw new BusinessException(400, "ComfyUI 参考素材列表不是合法 JSON 数组");
        }
    }

    private int[] parseResolution(String resolution) {
        if (StrUtil.isBlank(resolution) || !resolution.matches("\\d{2,5}x\\d{2,5}")) return null;
        String[] parts = resolution.split("x", 2);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private long pollInterval(AiModel model) {
        return configLong(model, "comfyuiPollIntervalMillis", DEFAULT_POLL_INTERVAL_MILLIS);
    }

    private long timeout(AiModel model) {
        return configLong(model, "comfyuiTimeoutMillis", DEFAULT_TIMEOUT_MILLIS);
    }

    private Integer configInt(AiModel model, String key) {
        if (model == null || StrUtil.isBlank(model.getConfig())) return null;
        try {
            return JSONUtil.parseObj(model.getConfig()).getInt(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long configLong(AiModel model, String key, long fallback) {
        if (model == null || StrUtil.isBlank(model.getConfig())) return fallback;
        try {
            JSONObject config = JSONUtil.parseObj(model.getConfig());
            Long value = config.getLong(key);
            return value != null && value > 0 ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long randomSeed() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private long candidateSeed(VideoTask task, int index) {
        long base = task.getSeed() != null ? task.getSeed() : randomSeed();
        return base + index;
    }

    private List<String> parsePlatformTaskIds(String platformTaskId) {
        if (StrUtil.isBlank(platformTaskId)) {
            throw new BusinessException(502, "ComfyUI 平台任务 ID 为空");
        }
        String value = platformTaskId.trim();
        if (!value.startsWith("[")) {
            return List.of(value);
        }
        try {
            return new ArrayList<>(JSONUtil.parseArray(value).toList(String.class));
        } catch (RuntimeException e) {
            throw new BusinessException(502, "ComfyUI 平台任务 ID 列表不是合法 JSON 数组");
        }
    }
}

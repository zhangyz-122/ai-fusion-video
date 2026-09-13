package com.stonewu.fusion.service.production;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.production.vo.ProductionStartReqVO;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.service.generation.video.VideoGenerationService;
import com.stonewu.fusion.service.generation.video.consumer.VideoGenerationConsumer;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.comfyui.WorkflowProfileService;
import com.stonewu.fusion.service.generation.GenerationModelCapabilityService;
import com.stonewu.fusion.service.storyboard.VideoComposeService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 分镜条目生产编排服务。
 *
 * <p>这里只编排现有 VideoTask 和现有 RedisTaskQueue，不创建第二套视频执行链。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductionRunService {

    public static final String RUN_CREATED = "CREATED";
    public static final String RUN_WAITING_GENERATION = "WAITING_GENERATION";
    public static final String RUN_QC_PENDING = "QC_PENDING";
    public static final String RUN_SELECTED = "SELECTED";
    public static final String RUN_FAILED = "FAILED";

    public static final String STEP_GENERATE_VIDEO = "GENERATE_VIDEO";
    private static final String STEP_CREATED = "CREATED";
    private static final String STEP_SUBMITTED = "SUBMITTED";
    private static final String STEP_SUCCEEDED = "SUCCEEDED";
    public static final String STEP_FAILED = "FAILED";

    private static final String QC_PASS = "PASS";
    private static final String QC_FAIL = "FAIL";
    private static final String QC_REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private final ProductionRunMapper runMapper;
    private final ProductionStepMapper stepMapper;
    private final ProductionTakeMapper takeMapper;
    private final QcResultMapper qcResultMapper;
    private final ProductionTechnicalQcService technicalQcService;
    private final ProductionRepairAttemptMapper repairAttemptMapper;
    private final ProductionRepairRouter repairRouter;
    private final ProductionRepairExecutor repairExecutor;
    private final StoryboardItemMapper storyboardItemMapper;
    private final StoryboardService storyboardService;
    private final VideoGenerationConsumer videoGenerationConsumer;
    private final VideoGenerationService videoGenerationService;
    private final VideoComposeService videoComposeService;
    private final AiModelService aiModelService;
    private final GenerationModelCapabilityService generationModelCapabilityService;
    private final WorkflowProfileService workflowProfileService;
    private final ShotReadinessService shotReadinessService;

    /**
     * 幂等启动一个分镜条目的三候选生产运行。
     */
    @Transactional
    public ProductionRunDetail start(ProductionStartReqVO request, Long userId) {
        String idempotencyKey = normalizeRequired(request.getIdempotencyKey(), "幂等键不能为空");
        ProductionRun existing = findByIdempotency(userId, request.getStoryboardItemId(), idempotencyKey);
        if (existing != null) {
            return detail(existing.getId(), userId);
        }

        StoryboardItem item = storyboardService.getItemById(request.getStoryboardItemId());
        shotReadinessService.requireReady(item, request);
        Storyboard storyboard = storyboardService.getById(item.getStoryboardId());
        if (storyboard == null) {
            throw new BusinessException(404, "分镜不存在: " + item.getStoryboardId());
        }

        String prompt = firstNonBlank(request.getPrompt(), item.getVideoPrompt(), item.getContent());
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("分镜条目没有可用的视频提示词");
        }

        AiModel model = resolveVideoModel(request.getModelId());
        WorkflowProfileService.VideoExecutionResolution profileResolution =
                request.getWorkflowProfileId() == null
                        ? null
                        : workflowProfileService.resolveVideoExecution(request.getWorkflowProfileId(), model);
        GenerationModelCapabilityService.VideoModelCapability capability =
                generationModelCapabilityService.resolveVideoCapability(model);

        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("storyboardItemId", item.getId());
        inputSnapshot.put("count", 3);
        inputSnapshot.put("prompt", prompt);
        if (profileResolution != null) {
            inputSnapshot.put("workflowProfileId", profileResolution.profile().getId());
            inputSnapshot.put("workflowProfileCode", profileResolution.profile().getCode());
            inputSnapshot.put("workflowVersionId", profileResolution.version().getId());
        }

        ProductionRun run = ProductionRun.builder()
                .storyboardItemId(item.getId())
                .userId(userId)
                .projectId(storyboard.getProjectId())
                .idempotencyKey(idempotencyKey)
                .status(RUN_CREATED)
                .build();
        runMapper.insert(run);

        ProductionStep step = ProductionStep.builder()
                .runId(run.getId())
                .stepType(STEP_GENERATE_VIDEO)
                .status(STEP_CREATED)
                .attempt(1)
                .workflowProfileId(profileResolution == null ? null : profileResolution.profile().getId())
                .workflowVersionId(profileResolution == null ? null : profileResolution.version().getId())
                .inputSnapshot(JSONUtil.toJsonStr(inputSnapshot))
                .build();
        stepMapper.insert(step);

        String firstFrameImageUrl = firstNonBlank(request.getFirstFrameImageUrl(), item.getFirstFrameImageUrl());
        String lastFrameImageUrl = firstNonBlank(request.getLastFrameImageUrl(), item.getLastFrameImageUrl());

        VideoTask.VideoTaskBuilder taskBuilder = VideoTask.builder()
                .userId(userId)
                .projectId(storyboard.getProjectId())
                .prompt(prompt)
                .generateMode(firstNonBlank(request.getGenerateMode(), "image2video"))
                .ratio(request.getRatio())
                .resolution(request.getResolution())
                .duration(request.getDuration())
                .seed(request.getSeed())
                .count(3)
                .category("production")
                .modelId(model.getId())
                .workflowVersionId(profileResolution == null ? null : profileResolution.version().getId());

        if (capability.supportsFirstFrame() && StringUtils.hasText(firstFrameImageUrl)) {
            taskBuilder.firstFrameImageUrl(firstFrameImageUrl);
            if (capability.supportsLastFrame() && StringUtils.hasText(lastFrameImageUrl)) {
                taskBuilder.lastFrameImageUrl(lastFrameImageUrl);
            }
        } else if (capability.supportsReferenceImages()) {
            // H3 reference-image workflows reject firstFrameImageUrl. Preserve the
            // existing storyboard image as a normal reference input instead.
            List<String> references = new ArrayList<>();
            if (StringUtils.hasText(firstFrameImageUrl)) {
                references.add(firstFrameImageUrl);
            } else if (StringUtils.hasText(lastFrameImageUrl)) {
                references.add(lastFrameImageUrl);
            }
            if (!references.isEmpty()) {
                if (capability.maxReferenceImages() != null
                        && references.size() > capability.maxReferenceImages()) {
                    references = references.subList(0, capability.maxReferenceImages());
                }
                taskBuilder.referenceImageUrls(JSONUtil.toJsonStr(references));
            }
        } else if (capability.supportsLastFrame() && StringUtils.hasText(lastFrameImageUrl)) {
            taskBuilder.lastFrameImageUrl(lastFrameImageUrl);
        }

        VideoTask task = taskBuilder.build();

        try {
            String taskId = videoGenerationConsumer.submitTask(task);
            VideoTask persistedTask = videoGenerationService.getByTaskId(taskId);
            step.setVideoTaskId(persistedTask.getId());
            step.setStatus(STEP_SUBMITTED);
            step.setExecutionRef(taskId);
            stepMapper.updateById(step);

            run.setStatus(RUN_WAITING_GENERATION);
            runMapper.updateById(run);
            return detail(run.getId(), userId);
        } catch (RuntimeException exception) {
            step.setStatus(STEP_FAILED);
            step.setErrorCode("VIDEO_TASK_SUBMIT_FAILED");
            step.setErrorMessage(trimMessage(exception.getMessage()));
            stepMapper.updateById(step);
            run.setStatus(RUN_FAILED);
            run.setFailureCode("VIDEO_TASK_SUBMIT_FAILED");
            run.setFailureMessage(trimMessage(exception.getMessage()));
            runMapper.updateById(run);
            // 保留 ProductionRun/Step 作为可恢复的失败证据；调用方可据此展示失败原因并用新幂等键重试。
            return detail(run.getId(), userId);
        }
    }

    private AiModel resolveVideoModel(Long modelId) {
        AiModel model = modelId == null
                ? aiModelService.getDefaultByType(3)
                : aiModelService.getById(modelId);
        if (model == null || !Integer.valueOf(3).equals(model.getModelType())
                || !Integer.valueOf(1).equals(model.getStatus())) {
            throw new BusinessException("没有可用的视频生成模型");
        }
        return model;
    }

    public ShotReadiness readiness(Long storyboardItemId) {
        return shotReadinessService.evaluate(storyboardService.getItemById(storyboardItemId));
    }

    /**
     * 当前用户的生产运行分页列表，可按状态过滤。
     */
    public PageResult<ProductionRun> list(Long userId, String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<ProductionRun> query = new LambdaQueryWrapper<ProductionRun>()
                .eq(ProductionRun::getUserId, userId)
                .eq(StrUtil.isNotBlank(status), ProductionRun::getStatus, status)
                .orderByDesc(ProductionRun::getCreateTime);
        return PageResult.of(runMapper.selectPage(new Page<>(pageNo, pageSize), query));
    }

    public ProductionRunDetail detail(Long runId, Long userId) {
        ProductionRun run = requireRun(runId, userId);        ProductionStep step = stepMapper.selectOne(new LambdaQueryWrapper<ProductionStep>()
                .eq(ProductionStep::getRunId, runId)
                .eq(ProductionStep::getStepType, STEP_GENERATE_VIDEO));
        VideoTask task = step != null && step.getVideoTaskId() != null
                ? videoGenerationService.getById(step.getVideoTaskId()) : null;
        List<ProductionTake> takeEntities = takeMapper.selectList(new LambdaQueryWrapper<ProductionTake>()
                .eq(ProductionTake::getRunId, runId)
                .orderByAsc(ProductionTake::getTakeIndex));
        Map<Long, VideoItem> itemMap = task == null ? Map.of()
                : videoGenerationService.listItems(task.getId()).stream()
                        .collect(Collectors.toMap(VideoItem::getId, item -> item));
        List<ProductionTakeView> takes = takeEntities.stream()
                .map(take -> ProductionTakeView.of(take, itemMap.get(take.getVideoItemId())))
                .toList();
        List<QcResult> qcResults = qcResultMapper.selectList(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getRunId, runId)
                .orderByAsc(QcResult::getTakeId));
        List<ProductionRepairAttempt> repairAttempts = repairAttemptMapper.selectList(
                new LambdaQueryWrapper<ProductionRepairAttempt>()
                        .eq(ProductionRepairAttempt::getRunId, runId)
                        .orderByAsc(ProductionRepairAttempt::getAttemptNo));
        return ProductionRunDetail.builder()
                .run(run)
                .step(step)
                .videoTask(task)
                .takes(takes)
                .qcResults(qcResults)
                .repairAttempts(repairAttempts)
                .build();
    }

    /**
     * 将现有 VideoTask 的结果幂等同步为 ProductionTake。
     */
    @Transactional
    public ProductionRunDetail reconcile(Long runId, Long userId) {
        ProductionRun run = requireRun(runId, userId);
        ProductionStep step = requireGenerateStep(runId);
        if (step.getVideoTaskId() == null) {
            throw new BusinessException("生产步骤尚未关联生视频任务");
        }

        VideoTask task = videoGenerationService.getById(step.getVideoTaskId());
        if (Integer.valueOf(3).equals(task.getStatus())) {
            markRunFailed(run, step, "VIDEO_TASK_FAILED", task.getErrorMsg());
            return detail(runId, userId);
        }
        if (!Integer.valueOf(2).equals(task.getStatus())) {
            return detail(runId, userId);
        }

        List<VideoItem> items = videoGenerationService.listItems(task.getId()).stream()
                .sorted(Comparator.comparing(VideoItem::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (items.size() != 3) {
            markRunFailed(run, step, "TAKE_COUNT_MISMATCH", "期望 3 个视频条目，实际收到 " + items.size());
            return detail(runId, userId);
        }

        for (int i = 0; i < items.size(); i++) {
            VideoItem item = items.get(i);
            ProductionTake existing = takeMapper.selectOne(new LambdaQueryWrapper<ProductionTake>()
                    .eq(ProductionTake::getRunId, runId)
                    .eq(ProductionTake::getTakeIndex, i + 1));
            if (existing != null) {
                ensureQcResult(run, existing, item);
                recordTechnicalFailure(run, step, technicalQcService.evaluate(existing, item));
                continue;
            }
            String qcStatus = Integer.valueOf(1).equals(item.getStatus()) ? QC_REVIEW_REQUIRED : QC_FAIL;
            ProductionTake take = ProductionTake.builder()
                    .runId(runId)
                    .storyboardItemId(run.getStoryboardItemId())
                    .videoItemId(item.getId())
                    .takeIndex(i + 1)
                    .qcStatus(qcStatus)
                    .qcNote(Integer.valueOf(1).equals(item.getStatus()) ? null : item.getErrorMsg())
                    .build();
            takeMapper.insert(take);
            ensureQcResult(run, take, item);
            recordTechnicalFailure(run, step, technicalQcService.evaluate(take, item));
        }

        step.setStatus(STEP_SUCCEEDED);
        step.setOutputSnapshot(JSONUtil.toJsonStr(Map.of(
                "videoTaskId", task.getId(),
                "videoItemIds", items.stream().map(VideoItem::getId).toList()
        )));
        stepMapper.updateById(step);
        if (!RUN_SELECTED.equals(run.getStatus())) {
            run.setStatus(RUN_QC_PENDING);
            runMapper.updateById(run);
        }
        return detail(runId, userId);
    }

    /** 执行最新一条可执行修复计划；修复由调用方显式触发，避免后台无限重试。 */
    @Transactional
    public ProductionRunDetail repair(Long runId, Long userId) {
        ProductionRun run = requireRun(runId, userId);
        ProductionRepairAttempt attempt = latestRepairAttempt(runId);
        if (attempt == null) {
            throw new BusinessException("生产运行没有修复计划");
        }
        if (ProductionRepairExecutor.SUBMITTED.equals(attempt.getStatus())) {
            return detail(runId, userId);
        }
        if (!ProductionRepairRouter.PLANNED.equals(attempt.getStatus())) {
            throw new BusinessException("当前修复计划不可执行: " + attempt.getStatus());
        }

        ProductionStep step = requireGenerateStep(runId);
        repairExecutor.execute(run, step, attempt);
        run.setStatus(RUN_WAITING_GENERATION);
        run.setFailureCode(null);
        run.setFailureMessage(null);
        runMapper.updateById(run);
        return detail(runId, userId);
    }

    @Transactional
    public ProductionRunDetail updateQc(Long runId, Long takeId, Long userId, String qcStatus, String qcNote) {
        ProductionRun run = requireRun(runId, userId);
        String normalized = normalizeQcStatus(qcStatus);
        ProductionTake take = requireTake(runId, takeId);
        QcResult result = findQcResult(takeId);
        if (QcResult.PASS.equals(normalized) && result != null
                && QcResult.FAIL.equals(result.getTechnicalStatus())) {
            throw new BusinessException("技术质检失败，不能人工标记通过");
        }

        take.setQcStatus(normalized);
        take.setQcNote(qcNote);
        takeMapper.updateById(take);

        if (result == null) {
            result = QcResult.builder()
                    .runId(runId)
                    .takeId(takeId)
                    .storyboardItemId(take.getStoryboardItemId())
                    .status(normalized)
                    .evaluatorType("MANUAL")
                    .note(qcNote)
                    .reviewedBy(userId)
                    .reviewedAt(java.time.LocalDateTime.now())
                    .build();
            qcResultMapper.insert(result);
        } else {
            result.setStatus(normalized);
            result.setEvaluatorType("MANUAL");
            result.setNote(qcNote);
            result.setReviewedBy(userId);
            result.setReviewedAt(java.time.LocalDateTime.now());
            qcResultMapper.updateById(result);
        }

        if (!QC_PASS.equals(normalized) && takeId.equals(run.getSelectedTakeId())) {
            run.setSelectedTakeId(null);
            storyboardItemMapper.update(null, new UpdateWrapper<StoryboardItem>()
                    .eq("id", run.getStoryboardItemId())
                    .set("selected_take_id", null));
        }
        if (QC_PASS.equals(normalized)) {
            run.setStatus(RUN_QC_PENDING);
            runMapper.updateById(run);
        } else if (run.getSelectedTakeId() == null) {
            run.setStatus(RUN_QC_PENDING);
            runMapper.updateById(run);
        }
        return detail(run.getId(), userId);
    }

    @Transactional
    @CacheEvict(value = "storyboardItem", allEntries = true)
    public ProductionRunDetail selectTake(Long runId, Long takeId, Long userId) {
        ProductionRun run = requireRun(runId, userId);
        ProductionTake take = requireTake(runId, takeId);
        QcResult qcResult = findQcResult(takeId);
        String qcStatus = qcResult == null ? take.getQcStatus() : qcResult.getStatus();
        if (!QC_PASS.equals(qcStatus)) {
            throw new BusinessException("只有 QC PASS 的候选视频才能被选中");
        }
        if (!run.getStoryboardItemId().equals(take.getStoryboardItemId())) {
            throw new BusinessException("候选视频与分镜条目不匹配");
        }

        StoryboardItem item = storyboardService.getItemById(run.getStoryboardItemId());
        storyboardItemMapper.update(null, new UpdateWrapper<StoryboardItem>()
                .eq("id", item.getId())
                .set("selected_take_id", take.getId()));
        run.setSelectedTakeId(take.getId());
        run.setStatus(RUN_SELECTED);
        runMapper.updateById(run);
        return detail(runId, userId);
    }

    public String compose(Long runId, Long userId) {
        ProductionRun run = requireRun(runId, userId);
        if (run.getSelectedTakeId() == null) {
            throw new BusinessException("请先完成 QC 并选择候选视频");
        }
        StoryboardItem item = storyboardService.getItemById(run.getStoryboardItemId());
        if (item.getStoryboardEpisodeId() == null) {
            throw new BusinessException("分镜条目未关联分镜集，无法调用现有合成服务");
        }
        return videoComposeService.submitCompose(item.getStoryboardEpisodeId(), userId);
    }

    private ProductionRun findByIdempotency(Long userId, Long storyboardItemId, String idempotencyKey) {
        return runMapper.selectOne(new LambdaQueryWrapper<ProductionRun>()
                .eq(ProductionRun::getUserId, userId)
                .eq(ProductionRun::getStoryboardItemId, storyboardItemId)
                .eq(ProductionRun::getIdempotencyKey, idempotencyKey));
    }

    private ProductionRepairAttempt latestRepairAttempt(Long runId) {
        return repairAttemptMapper.selectList(new LambdaQueryWrapper<ProductionRepairAttempt>()
                .eq(ProductionRepairAttempt::getRunId, runId)
                .orderByDesc(ProductionRepairAttempt::getAttemptNo)
                .orderByDesc(ProductionRepairAttempt::getId)).stream()
                .findFirst().orElse(null);
    }

    private ProductionRun requireRun(Long runId, Long userId) {
        ProductionRun run = runMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId())) {
            throw new BusinessException(404, "生产运行不存在: " + runId);
        }
        return run;
    }

    private ProductionStep requireGenerateStep(Long runId) {
        ProductionStep step = stepMapper.selectOne(new LambdaQueryWrapper<ProductionStep>()
                .eq(ProductionStep::getRunId, runId)
                .eq(ProductionStep::getStepType, STEP_GENERATE_VIDEO));
        if (step == null) {
            throw new BusinessException("生产运行缺少视频生成步骤: " + runId);
        }
        return step;
    }

    private ProductionTake requireTake(Long runId, Long takeId) {
        ProductionTake take = takeMapper.selectOne(new LambdaQueryWrapper<ProductionTake>()
                .eq(ProductionTake::getId, takeId)
                .eq(ProductionTake::getRunId, runId));
        if (take == null) {
            throw new BusinessException(404, "候选视频不存在: " + takeId);
        }
        return take;
    }

    private QcResult findQcResult(Long takeId) {
        return qcResultMapper.selectOne(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getTakeId, takeId));
    }

    private void ensureQcResult(ProductionRun run, ProductionTake take, VideoItem item) {
        if (findQcResult(take.getId()) != null) {
            return;
        }
        String status = StringUtils.hasText(take.getQcStatus())
                ? take.getQcStatus()
                : (Integer.valueOf(1).equals(item.getStatus()) ? QC_REVIEW_REQUIRED : QC_FAIL);
        qcResultMapper.insert(QcResult.builder()
                .runId(run.getId())
                .takeId(take.getId())
                .storyboardItemId(run.getStoryboardItemId())
                .status(status)
                .evaluatorType("SYSTEM")
                .note(take.getQcNote())
                .build());
    }

    private void markRunFailed(ProductionRun run, ProductionStep step, String code, String message) {
        repairRouter.recordFailure(run, step, code, message);
        step.setStatus(STEP_FAILED);
        step.setErrorCode(code);
        step.setErrorMessage(trimMessage(message));
        stepMapper.updateById(step);
        run.setStatus(RUN_FAILED);
        run.setFailureCode(code);
        run.setFailureMessage(trimMessage(message));
        runMapper.updateById(run);
    }

    private void recordTechnicalFailure(ProductionRun run, ProductionStep step, QcResult result) {
        if (result == null || !QcResult.FAIL.equals(result.getTechnicalStatus())) {
            return;
        }
        repairRouter.recordFailure(run, step,
                StringUtils.hasText(result.getTechnicalFailureCode())
                        ? result.getTechnicalFailureCode() : "VIDEO_QC_EXCEPTION",
                result.getNote());
    }

    private String normalizeQcStatus(String qcStatus) {
        String normalized = normalizeRequired(qcStatus, "质检状态不能为空").toUpperCase(Locale.ROOT);
        if (!QC_PASS.equals(normalized) && !QC_FAIL.equals(normalized) && !QC_REVIEW_REQUIRED.equals(normalized)) {
            throw new BusinessException("质检状态仅支持 PASS、FAIL、REVIEW_REQUIRED");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }

    private String firstNonBlank(String first, String second, String third) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return third;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}

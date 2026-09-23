package com.stonewu.fusion.service.production.qc;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.service.production.QcResultService;
import com.stonewu.fusion.service.production.qc.QcSidecarClient.QcEvaluationRequest;
import com.stonewu.fusion.service.production.qc.QcSidecarClient.QcEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PR-019~020: 对单个 Take 执行质检，并把结论写回 take.qc_status。
 * 质检不创建新的产物，只判定既有产物是否可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcEvaluationService {

    private static final List<String> CHECKS = List.of(
            "TECHNICAL_VALIDITY",
            "DURATION",
            "FRAME_EXTRACTION",
            "BRIGHTNESS",
            "MOTION_ANALYSIS",
            "CHARACTER_COUNT");

    private final QcSidecarClient sidecarClient;
    private final QcResultService qcResultService;
    private final ProductionTakeMapper takeMapper;
    private final StoryboardItemMapper itemMapper;

    @Transactional
    public ProductionTake evaluate(Long takeId) {
        ProductionTake take = takeMapper.selectById(takeId);
        if (take == null) {
            throw new BusinessException(404, "镜头产物不存在: " + takeId);
        }
        JSONObject metadata = JSONUtil.parseObj(StrUtil.emptyIfNull(take.getMetadataJson()));
        String videoUrl = metadata.getStr("videoUrl");
        if (StrUtil.isBlank(videoUrl)) {
            throw new BusinessException(400, "镜头产物缺少 videoUrl，无法质检");
        }
        StoryboardItem item = itemMapper.selectById(take.getStoryboardItemId());
        if (item == null) {
            throw new BusinessException(404, "分镜镜头不存在: " + take.getStoryboardItemId());
        }
        if (item.getDuration() == null) {
            throw new BusinessException(400, "分镜镜头未规划时长，无法按时长质检");
        }

        QcEvaluationResult result = sidecarClient.evaluate(new QcEvaluationRequest(
                videoUrl,
                metadata.getStr("firstFrameUrl"),
                metadata.getStr("lastFrameUrl"),
                StrUtil.emptyIfNull(item.getVideoPrompt()),
                countCharacters(item.getCharacterIds()),
                item.getDuration().doubleValue(),
                CHECKS));

        qcResultService.replaceEvaluation(takeId, result);
        take.setQcStatus(result.overallVerdict());
        takeMapper.updateById(take);
        log.info("[qc] take {} 质检完成: verdict={}, checks={}, elapsed={}ms",
                takeId, result.overallVerdict(), result.checks().size(), result.elapsedMs());
        return take;
    }

    private int countCharacters(String characterIdsJson) {
        if (StrUtil.isBlank(characterIdsJson)) {
            return 0;
        }
        return JSONUtil.parseArray(characterIdsJson).size();
    }
}

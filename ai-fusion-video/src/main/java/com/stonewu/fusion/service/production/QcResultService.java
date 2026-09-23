package com.stonewu.fusion.service.production;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.service.production.qc.QcSidecarClient.QcCheckOutcome;
import com.stonewu.fusion.service.production.qc.QcSidecarClient.QcEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PR-019~020: 质检结果落库。每个质检项一行，FAIL 项必须带 failure_code 以便归因。
 */
@Service
@RequiredArgsConstructor
public class QcResultService {

    private final QcResultMapper qcMapper;

    /** 重跑质检时整批替换，保留同一 take 的最新一次结论 */
    @Transactional
    @CacheEvict(value = "qcResult", key = "#takeId")
    public void replaceEvaluation(Long takeId, QcEvaluationResult result) {
        qcMapper.delete(new LambdaQueryWrapper<QcResult>().eq(QcResult::getTakeId, takeId));
        for (QcCheckOutcome outcome : result.checks()) {
            QcResult row = new QcResult();
            row.setTakeId(takeId);
            row.setCriterion(outcome.criterion());
            row.setVerdict(outcome.verdict());
            row.setValueScore(toDecimal(outcome.valueScore()));
            row.setThresholdValue(toDecimal(outcome.thresholdValue()));
            row.setEvidenceUrl(outcome.evidenceUrl());
            if (outcome.failureCode() != null) {
                row.setEvidenceJson(JSONUtil.toJsonStr(Map.of("failureCode", outcome.failureCode())));
            }
            qcMapper.insert(row);
        }
    }

    @Cacheable(value = "qcResult", key = "#takeId", unless = "#result.isEmpty()")
    public List<QcResult> listByTake(Long takeId) {
        return qcMapper.selectList(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getTakeId, takeId)
                .orderByAsc(QcResult::getId));
    }

    private BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}

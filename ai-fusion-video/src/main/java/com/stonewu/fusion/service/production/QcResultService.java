package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QcResultService {
    private final QcResultMapper qcMapper;

    public QcResult record(QcResult result) { qcMapper.insert(result); return result; }

    public List<QcResult> listByTake(Long takeId) {
        return qcMapper.selectList(new LambdaQueryWrapper<QcResult>().eq(QcResult::getTakeId, takeId));
    }

    public boolean hasBlockingFail(Long takeId) {
        return qcMapper.selectCount(new LambdaQueryWrapper<QcResult>()
            .eq(QcResult::getTakeId, takeId).eq(QcResult::getVerdict, "FAIL")) > 0;
    }
}

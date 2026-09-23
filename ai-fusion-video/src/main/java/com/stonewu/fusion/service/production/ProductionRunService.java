package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PR-004/014: 生产运行批次查询。Run 只由生产编排写入，此处仅提供列表与详情读取。
 */
@Service
@RequiredArgsConstructor
public class ProductionRunService {

    private final ProductionRunMapper runMapper;

    @Cacheable(value = "productionRun", key = "'project:' + #projectId", unless = "#result.isEmpty()")
    public List<ProductionRun> listByProject(Long projectId) {
        return runMapper.selectList(new LambdaQueryWrapper<ProductionRun>()
                .eq(ProductionRun::getProjectId, projectId)
                .orderByDesc(ProductionRun::getId));
    }

    @Cacheable(value = "productionRun", key = "'id:' + #id", unless = "#result == null")
    public ProductionRun getById(Long id) {
        return runMapper.selectById(id);
    }
}

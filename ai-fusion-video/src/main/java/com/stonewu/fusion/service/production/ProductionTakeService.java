package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductionTakeService {

    private final ProductionTakeMapper takeMapper;

    public ProductionTake ingest(Long runId, Long storyboardItemId, String sourceType,
                                  Long sourceItemId, Long profileId, Long versionId,
                                  String modelId, Long seed, String metadataJson) {
        // 幂等：同 source_item_id 不重复 ingest
        ProductionTake existing = takeMapper.selectOne(
            new LambdaQueryWrapper<ProductionTake>()
                .eq(ProductionTake::getSourceType, sourceType)
                .eq(ProductionTake::getSourceItemId, sourceItemId));
        if (existing != null) return existing;

        ProductionTake take = new ProductionTake();
        take.setRunId(runId);
        take.setStoryboardItemId(storyboardItemId);
        take.setSourceType(sourceType);
        take.setSourceItemId(sourceItemId);
        take.setWorkflowProfileId(profileId);
        take.setWorkflowVersionId(versionId);
        take.setModelId(modelId);
        take.setSeed(seed);
        take.setQcStatus("PENDING");
        take.setMetadataJson(metadataJson);
        takeMapper.insert(take);
        return take;
    }

    public List<ProductionTake> listByStoryboardItem(Long storyboardItemId) {
        return takeMapper.selectList(
            new LambdaQueryWrapper<ProductionTake>()
                .eq(ProductionTake::getStoryboardItemId, storyboardItemId)
                .orderByDesc(ProductionTake::getId));
    }

    public ProductionTake getById(Long id) { return takeMapper.selectById(id); }
}

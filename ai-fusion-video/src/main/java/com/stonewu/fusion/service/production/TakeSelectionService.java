package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PR-012: Take Selection / Ownership Guard
 * StoryboardItem.selectedTakeId 是唯一选择 SSOT。
 * 校验 Take 属于同 StoryboardItem，跨镜头/跨 Run 选择一律拒绝。
 */
@Service
@RequiredArgsConstructor
public class TakeSelectionService {

    private final ProductionTakeMapper takeMapper;
    private final StoryboardItemMapper itemMapper;

    @Transactional
    public ProductionTake selectTake(Long storyboardItemId, Long takeId) {
        ProductionTake take = takeMapper.selectById(takeId);
        if (take == null) throw new IllegalArgumentException("Take not found: " + takeId);
        if (!take.getStoryboardItemId().equals(storyboardItemId)) {
            throw new IllegalArgumentException(
                "Take " + takeId + " belongs to item " + take.getStoryboardItemId()
                + ", cannot select for item " + storyboardItemId);
        }
        if ("FAIL".equals(take.getQcStatus())) {
            throw new IllegalStateException("QC FAIL 的 Take 不可 selected（override 需审计）");
        }
        StoryboardItem item = itemMapper.selectById(storyboardItemId);
        if (item == null) throw new IllegalArgumentException("StoryboardItem not found: " + storyboardItemId);
        item.setSelectedTakeId(takeId);
        itemMapper.updateById(item);
        return take;
    }

    @Transactional
    public void deselectTake(Long storyboardItemId) {
        StoryboardItem item = itemMapper.selectById(storyboardItemId);
        if (item != null) {
            item.setSelectedTakeId(null);
            itemMapper.updateById(item);
        }
    }
}

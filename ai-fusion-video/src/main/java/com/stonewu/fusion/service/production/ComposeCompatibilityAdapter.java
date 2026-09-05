package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PR-013: Compose 兼容适配器 — selected Take 投影到 StoryboardItem 兼容字段。
 * 旧 Compose 不重写；无 Production 镜头沿用老逻辑。
 */
@Service
@RequiredArgsConstructor
public class ComposeCompatibilityAdapter {

    private final StoryboardItemMapper itemMapper;

    public void projectSelectedTakeToLegacyFields(StoryboardItem item, ProductionTake take) {
        if (take == null || !"VIDEO_ITEM".equals(take.getSourceType())) return;
        // 从 Take metadata 中提取 videoUrl / firstFrameUrl / lastFrameUrl
        String meta = take.getMetadataJson();
        if (meta != null && meta.contains("videoUrl")) {
            // 解析 metadata 并设置兼容字段
            item.setGeneratedVideoUrl(extractJsonString(meta, "videoUrl"));
            item.setFirstFrameImageUrl(extractJsonString(meta, "firstFrameUrl"));
            item.setLastFrameImageUrl(extractJsonString(meta, "lastFrameUrl"));
        }
        itemMapper.updateById(item);
    }

    private String extractJsonString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon);
        int end = json.indexOf('"', start + 1);
        return (start >= 0 && end > start) ? json.substring(start + 1, end) : null;
    }
}

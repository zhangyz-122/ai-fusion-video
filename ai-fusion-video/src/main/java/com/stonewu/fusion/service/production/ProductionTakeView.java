package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.production.ProductionTake;
import lombok.Builder;
import lombok.Data;

/**
 * 候选视频视图：ProductionTake 叠加所属 VideoItem 的展示字段，供前端预览。
 */
@Data
@Builder
public class ProductionTakeView {

    private Long id;

    private Long runId;

    private Long videoItemId;

    private Integer takeIndex;

    private String qcStatus;

    private String qcNote;

    private String videoUrl;

    private String coverUrl;

    private Integer videoStatus;

    private String videoErrorMsg;

    public static ProductionTakeView of(ProductionTake take, VideoItem item) {
        return ProductionTakeView.builder()
                .id(take.getId())
                .runId(take.getRunId())
                .videoItemId(take.getVideoItemId())
                .takeIndex(take.getTakeIndex())
                .qcStatus(take.getQcStatus())
                .qcNote(take.getQcNote())
                .videoUrl(item == null ? null : item.getVideoUrl())
                .coverUrl(item == null ? null : item.getCoverUrl())
                .videoStatus(item == null ? null : item.getStatus())
                .videoErrorMsg(item == null ? null : item.getErrorMsg())
                .build();
    }
}

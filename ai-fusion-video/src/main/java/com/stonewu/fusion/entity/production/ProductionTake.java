package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

/**
 * Production Run 生成的候选视频。
 */
@TableName("afv_production_take")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionTake extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long storyboardItemId;

    private Long videoItemId;

    private Integer takeIndex;

    @Builder.Default
    private String qcStatus = "REVIEW_REQUIRED";

    private String qcNote;
}

package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;

/**
 * 一个分镜条目的可追踪生产运行。
 */
@TableName("afv_production_run")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionRun extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyboardItemId;

    private Long userId;

    private Long projectId;

    private String idempotencyKey;

    private String status;

    private Long selectedTakeId;

    private String failureCode;

    private String failureMessage;
}

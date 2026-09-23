package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 一条已提交的剧情状态变更。
 *
 * <p>append-only：不修改、不删除，更正通过追加更晚的事件表达。
 * 只记录世界事实，不记录资产版本与任务执行状态。</p>
 */
@TableName("afv_story_event")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryEvent extends BaseEntity {

    public static final String SUBJECT_CHARACTER = "CHARACTER";
    public static final String SUBJECT_PROP = "PROP";
    public static final String SUBJECT_LOCATION = "LOCATION";
    public static final String SUBJECT_FACT = "FACT";
    public static final String SUBJECT_OPEN_LOOP = "OPEN_LOOP";

    public static final String CHANGE_SET = "SET";
    public static final String CHANGE_ADD = "ADD";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 0 表示项目级事实，避免唯一键落在 NULL 上 */
    private Long episodeId;

    private Long storyboardItemId;

    private String subjectType;

    private String subjectKey;

    @Builder.Default
    private String changeKind = CHANGE_SET;

    private String value;

    private Long predecessorEventId;

    private String idempotencyKey;

    private Long createdBy;
}

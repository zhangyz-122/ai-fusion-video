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
 * 剧情事件折叠到某个位置后的状态快照。
 *
 * <p>快照只用于避免全量重放，必须能从 {@code after_event_id} 之前的事件重建出同一份状态。</p>
 */
@TableName("afv_story_state_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryStateSnapshot extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 0 表示项目级状态 */
    private Long episodeId;

    private Long afterEventId;

    private String stateJson;

    private String stateHash;
}

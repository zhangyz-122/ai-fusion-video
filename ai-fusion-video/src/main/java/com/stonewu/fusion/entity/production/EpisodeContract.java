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
 * 分集剧情契约：本集开拍前必须成立的状态、结束时应达成的状态、必须出现的节拍与必须闭合的悬念。
 *
 * <p>它只是 lint 的判定基线，不改变剧情事件的事实源地位。</p>
 */
@TableName("afv_episode_contract")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeContract extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long episodeId;

    private String inputStateJson;

    private String outputStateJson;

    private String requiredBeatsJson;

    private String mustResolveJson;

    @Builder.Default
    private Integer revision = 1;

    private Long definedBy;
}

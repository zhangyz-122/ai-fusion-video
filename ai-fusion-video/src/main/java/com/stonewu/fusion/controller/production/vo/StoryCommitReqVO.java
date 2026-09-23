package com.stonewu.fusion.controller.production.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 提交镜头剧情状态变更请求。
 */
@Schema(description = "提交镜头剧情状态变更请求")
@Data
public class StoryCommitReqVO {

    @Valid
    @NotEmpty(message = "剧情状态变更不能为空")
    private List<Delta> deltas;

    @Schema(description = "单条剧情主体变更")
    @Data
    public static class Delta {

        @Schema(description = "主体类型：CHARACTER/PROP/LOCATION/FACT/OPEN_LOOP")
        private String subjectType;

        private String subjectKey;

        @Schema(description = "变更方式：SET 覆盖主体，ADD 追加列表型主体")
        private String changeKind;

        private String value;

        @Schema(description = "被本条更正的前序事件标识，可空")
        private Long predecessorEventId;
    }
}

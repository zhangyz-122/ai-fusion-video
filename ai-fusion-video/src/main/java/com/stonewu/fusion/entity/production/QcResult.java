package com.stonewu.fusion.entity.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "afv_qc_result", autoResultMap = true)
public class QcResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long takeId;
    private String criterion;
    private String verdict;
    private BigDecimal valueScore;
    private BigDecimal thresholdValue;
    private String evidenceUrl;
    private String evidenceJson;
    private Long reviewedBy;
    private String overrideReason;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}

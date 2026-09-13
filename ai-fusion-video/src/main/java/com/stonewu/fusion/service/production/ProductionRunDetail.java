package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Production Run 聚合视图。
 */
@Data
@Builder
public class ProductionRunDetail {

    private ProductionRun run;

    private ProductionStep step;

    private VideoTask videoTask;

    private List<ProductionTake> takes;

    private List<QcResult> qcResults;

    private List<ProductionRepairAttempt> repairAttempts;
}

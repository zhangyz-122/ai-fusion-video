CREATE TABLE `afv_qc_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '生产运行标识',
  `take_id` bigint NOT NULL COMMENT '候选视频标识',
  `storyboard_item_id` bigint NOT NULL COMMENT '分镜条目标识',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW_REQUIRED' COMMENT '质检状态',
  `evaluator_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SYSTEM' COMMENT '质检来源：SYSTEM/AUTO/MANUAL',
  `score` decimal(6,3) DEFAULT NULL COMMENT '可选质量分数',
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败编码',
  `metrics_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '结构化质检指标',
  `note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '质检说明',
  `reviewed_by` bigint DEFAULT NULL COMMENT '人工复核用户标识',
  `reviewed_at` datetime DEFAULT NULL COMMENT '人工复核时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qc_result_take` (`take_id`, `deleted`),
  KEY `idx_qc_result_run_status` (`run_id`, `status`),
  KEY `idx_qc_result_storyboard_item` (`storyboard_item_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Production 候选视频质检结果';

-- Backfill the independent audit record for candidates created before PR-018.
INSERT INTO `afv_qc_result`
  (`run_id`, `take_id`, `storyboard_item_id`, `status`, `evaluator_type`, `note`, `deleted`)
SELECT t.`run_id`, t.`id`, t.`storyboard_item_id`, t.`qc_status`, 'SYSTEM', t.`qc_note`, 0
FROM `afv_production_take` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `afv_qc_result` q
    WHERE q.`take_id` = t.`id` AND q.`deleted` = 0
  );

ALTER TABLE `afv_qc_result`
  ADD COLUMN `technical_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自动技术质检状态' AFTER `status`,
  ADD COLUMN `technical_failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自动技术质检失败编码' AFTER `technical_status`,
  ADD COLUMN `technical_metrics_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '自动技术质检指标' AFTER `technical_failure_code`,
  ADD COLUMN `technical_evaluated_at` datetime DEFAULT NULL COMMENT '自动技术质检时间' AFTER `technical_metrics_json`,
  ADD KEY `idx_qc_result_technical_status` (`technical_status`);

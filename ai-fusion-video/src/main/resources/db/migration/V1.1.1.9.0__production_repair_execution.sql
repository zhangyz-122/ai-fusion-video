ALTER TABLE `afv_production_repair_attempt`
  ADD COLUMN `replacement_video_task_id` bigint DEFAULT NULL COMMENT '实际替代视频任务ID' AFTER `idempotency_key`,
  ADD COLUMN `replacement_execution_ref` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '替代任务执行引用' AFTER `replacement_video_task_id`;

ALTER TABLE `afv_production_repair_attempt`
  ADD KEY `idx_production_repair_replacement_task` (`replacement_video_task_id`);

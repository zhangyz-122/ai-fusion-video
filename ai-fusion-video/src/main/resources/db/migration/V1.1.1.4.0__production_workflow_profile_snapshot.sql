ALTER TABLE `afv_production_step`
  ADD COLUMN `workflow_profile_id` bigint DEFAULT NULL COMMENT '本次生产使用的逻辑 WorkflowProfile' AFTER `video_task_id`,
  ADD COLUMN `workflow_version_id` bigint DEFAULT NULL COMMENT '提交时固化的 WorkflowVersion' AFTER `workflow_profile_id`,
  ADD KEY `idx_production_step_workflow_profile` (`workflow_profile_id`),
  ADD KEY `idx_production_step_workflow_version` (`workflow_version_id`);

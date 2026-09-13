CREATE TABLE `afv_production_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `storyboard_item_id` bigint NOT NULL COMMENT '分镜条目标识',
  `user_id` bigint NOT NULL COMMENT '发起用户标识',
  `project_id` bigint DEFAULT NULL COMMENT '项目标识',
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生产状态',
  `selected_take_id` bigint DEFAULT NULL COMMENT '唯一选中的候选视频标识',
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败编码',
  `failure_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '失败信息',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_run_idempotency` (`user_id`, `storyboard_item_id`, `idempotency_key`),
  KEY `idx_production_run_item_status` (`storyboard_item_id`, `status`),
  KEY `idx_production_run_selected_take` (`selected_take_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分镜生产运行';

CREATE TABLE `afv_production_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '生产运行标识',
  `step_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤类型',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤状态',
  `video_task_id` bigint DEFAULT NULL COMMENT '关联生视频任务标识',
  `execution_ref` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部执行引用',
  `attempt` int NOT NULL DEFAULT 0 COMMENT '执行尝试次数',
  `input_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '输入快照',
  `output_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '输出快照',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误编码',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_step_type` (`run_id`, `step_type`),
  KEY `idx_production_step_video_task` (`video_task_id`),
  KEY `idx_production_step_status` (`run_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分镜生产步骤';

CREATE TABLE `afv_production_take` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` bigint NOT NULL COMMENT '生产运行标识',
  `storyboard_item_id` bigint NOT NULL COMMENT '分镜条目标识',
  `video_item_id` bigint NOT NULL COMMENT '生视频条目标识',
  `take_index` int NOT NULL COMMENT '候选视频序号，从1开始',
  `qc_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW_REQUIRED' COMMENT '质检状态',
  `qc_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '质检备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_take_index` (`run_id`, `take_index`),
  UNIQUE KEY `uk_production_take_video_item` (`run_id`, `video_item_id`),
  KEY `idx_production_take_item` (`storyboard_item_id`, `qc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分镜生产候选视频';

ALTER TABLE `afv_storyboard_item`
  ADD COLUMN `selected_take_id` bigint DEFAULT NULL COMMENT 'Production 层唯一选中的候选视频标识' AFTER `generated_video_url`,
  ADD KEY `idx_sb_item_selected_take` (`selected_take_id`);

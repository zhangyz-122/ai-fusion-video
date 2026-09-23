-- PR-021 / PR-022：剧情状态事件与状态快照
-- 契约见 dev-docs/2-in-progress/20260923-story-state/story-state-contract.md

CREATE TABLE `afv_story_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，同时是项目内的剧情顺序号',
  `project_id` bigint NOT NULL COMMENT '所属项目标识，剧情状态的隔离与权限边界',
  `episode_id` bigint NOT NULL DEFAULT '0' COMMENT '所属分集标识，0 表示项目级事实',
  `storyboard_item_id` bigint DEFAULT NULL COMMENT '触发该变更的镜头标识，可空表示人工登记',
  `subject_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更主体类型：CHARACTER/PROP/LOCATION/FACT/OPEN_LOOP',
  `subject_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目内稳定的主体名称，折叠状态时作为键',
  `change_kind` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SET' COMMENT '折叠方式：SET-覆盖该主体，ADD-追加到列表型主体',
  `value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '变更后的值；OPEN_LOOP 关闭时写 RESOLVED',
  `predecessor_event_id` bigint DEFAULT NULL COMMENT '被本事件更正的前序事件标识，可空',
  `idempotency_key` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键，重复提交同一镜头的同一主体变更时不产生第二条事件',
  `created_by` bigint NOT NULL COMMENT '提交该剧情变更的用户标识',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志；剧情事件按契约 append-only，恒为 0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_event_idempotency` (`project_id`, `idempotency_key`, `deleted`),
  KEY `idx_story_event_subject` (`project_id`, `subject_type`, `subject_key`),
  KEY `idx_story_event_shot` (`storyboard_item_id`),
  KEY `idx_story_event_episode` (`project_id`, `episode_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧情状态事件（append-only 唯一事实源）';

CREATE TABLE `afv_story_state_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '所属项目标识',
  `episode_id` bigint NOT NULL DEFAULT '0' COMMENT '所属分集标识，0 表示项目级状态',
  `after_event_id` bigint NOT NULL COMMENT '本快照已折叠到的最后一条剧情事件标识',
  `state_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件折叠后的完整剧情状态',
  `state_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态内容 SHA-256，用于判定快照等价与漂移',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_snapshot_position` (`project_id`, `episode_id`, `after_event_id`, `deleted`),
  KEY `idx_story_snapshot_latest` (`project_id`, `episode_id`, `after_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧情状态快照（事件折叠的可重建缓存，非第二事实源）';

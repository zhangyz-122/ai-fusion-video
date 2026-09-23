-- PR-023：分集剧情契约与 lint 依据
-- 契约定义见 dev-docs/2-in-progress/20260923-story-state/story-state-contract.md

CREATE TABLE `afv_episode_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '所属项目标识',
  `episode_id` bigint NOT NULL COMMENT '所属分集标识，与剧情事件的 episode_id 同一粒度',
  `output_state_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '本集结束时应成立的剧情状态，用于与已提交事件折叠结果比对',
  `required_beats_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '本集必须出现的剧情节拍，按关键词匹配已提交事件的取值',
  `must_resolve_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '本集必须闭合的悬念主体名列表',
  `revision` int NOT NULL DEFAULT '1' COMMENT '契约修订号，每次覆盖写入递增',
  `defined_by` bigint NOT NULL COMMENT '定义该契约的用户标识',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_episode_contract_scope` (`project_id`, `episode_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分集剧情契约（lint 的判定基线）';

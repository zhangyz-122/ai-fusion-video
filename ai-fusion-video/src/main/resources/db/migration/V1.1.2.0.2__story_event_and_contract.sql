-- AI Drama OS PR-021~023: Story Event Store + Snapshot + Episode Contract

CREATE TABLE IF NOT EXISTS afv_story_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，同时是事件的全局顺序号，重放按此升序',
    project_id BIGINT NOT NULL COMMENT '所属项目标识',
    episode_id BIGINT COMMENT '所属分集标识，为空表示项目级事实',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型：CHARACTER_STATE_CHANGED/PROP_STATE_CHANGED/RELATION_CHANGED/LOCATION_CHANGED/FACT_REVEALED/OPEN_LOOP_CREATED/OPEN_LOOP_RESOLVED/WARDROBE_CHANGED/INJURY_CHANGED',
    character_name VARCHAR(100) COMMENT '角色状态变更涉及的名称',
    prop_name VARCHAR(100) COMMENT '道具状态变更涉及的名称',
    location_name VARCHAR(100) COMMENT '场景位置变更涉及的名称',
    old_value TEXT COMMENT '变更前的值，用于审计与回滚',
    new_value TEXT COMMENT '变更后的值，重放时写入状态',
    description TEXT COMMENT '人类可读的事件说明',
    source_shot_id BIGINT COMMENT '触发该事件的镜头标识，用于追溯是哪个镜头改变了剧情状态',
    idempotency_key VARCHAR(128) COMMENT '幂等键，重复追加同一剧情事实时不产生第二条事件',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_story_event_idem (idempotency_key),
    INDEX idx_story_event_project (project_id),
    INDEX idx_story_event_episode (episode_id),
    INDEX idx_story_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧情状态事件（append-only，不可修改或删除）';

CREATE TABLE IF NOT EXISTS afv_story_state_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目标识',
    episode_id BIGINT COMMENT '所属分集标识',
    after_event_id BIGINT COMMENT '该快照折叠到的最后一条剧情事件标识',
    state_json JSON NOT NULL COMMENT '由事件重放折叠出的完整剧情状态',
    hash_version VARCHAR(64) NOT NULL COMMENT '状态内容摘要，用于判定快照是否等价',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_snapshot_project_episode (project_id, episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧情状态快照（避免每次全量重放事件）';

CREATE TABLE IF NOT EXISTS afv_episode_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目标识',
    episode_id BIGINT NOT NULL COMMENT '所属分集标识',
    input_state_json JSON COMMENT '本集开场时必须成立的剧情状态',
    required_beats_json JSON COMMENT '本集必须出现的剧情节拍',
    knowledge_boundary_json JSON COMMENT '角色知道/不知道的信息边界约束',
    open_loops_in_json JSON COMMENT '本集开场时仍未闭合的剧情悬念',
    must_resolve_json JSON COMMENT '本集必须闭合的悬念',
    open_loops_out_json JSON COMMENT '本集结尾时留给下一集的悬念',
    output_state_json JSON COMMENT '本集结尾时应当成立的剧情状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_episode_contract (project_id, episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分集剧情契约（跨镜头一致性的校验依据）';

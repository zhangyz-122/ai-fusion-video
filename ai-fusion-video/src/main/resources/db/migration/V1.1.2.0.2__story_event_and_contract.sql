-- AI Drama OS PR-021~023: Story Event Store + Snapshot + Episode Contract

CREATE TABLE IF NOT EXISTS afv_story_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    episode_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    character_name VARCHAR(100),
    prop_name VARCHAR(100),
    location_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    description TEXT,
    source_shot_id BIGINT,
    idempotency_key VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_story_event_idem (idempotency_key),
    INDEX idx_story_event_project (project_id),
    INDEX idx_story_event_episode (episode_id),
    INDEX idx_story_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS afv_story_state_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    episode_id BIGINT,
    after_event_id BIGINT,
    state_json JSON NOT NULL,
    hash_version VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_snapshot_project_episode (project_id, episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS afv_episode_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    episode_id BIGINT NOT NULL,
    input_state_json JSON,
    required_beats_json JSON,
    knowledge_boundary_json JSON,
    open_loops_in_json JSON,
    must_resolve_json JSON,
    open_loops_out_json JSON,
    output_state_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_episode_contract (project_id, episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

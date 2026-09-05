-- AI Drama OS PR-003: Production Run/Step/Take tables
-- Depends on: V1.1.1.0.0__comfyui_workflow_support.sql

-- ================================================
-- afv_production_run
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    storyboard_id BIGINT NOT NULL,
    storyboard_episode_id BIGINT,
    run_type VARCHAR(32) NOT NULL DEFAULT 'BATCH_PRODUCE',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(128),
    started_at DATETIME,
    finished_at DATETIME,
    metadata_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_production_run_idem (idempotency_key),
    INDEX idx_production_run_project (project_id),
    INDEX idx_production_run_storyboard (storyboard_id),
    INDEX idx_production_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- afv_production_step
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    storyboard_item_id BIGINT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    depends_on_json JSON,
    execution_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO',
    execution_ref_id BIGINT,
    attempt INT NOT NULL DEFAULT 0,
    parent_step_id BIGINT,
    error_code VARCHAR(64),
    error_message TEXT,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_production_step_idem (run_id, storyboard_item_id, step_type, attempt),
    INDEX idx_production_step_run (run_id),
    INDEX idx_production_step_item (storyboard_item_id),
    INDEX idx_production_step_status (status),
    CONSTRAINT fk_prod_step_run FOREIGN KEY (run_id) REFERENCES afv_production_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- afv_production_take
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_take (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    storyboard_item_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO_ITEM',
    source_item_id BIGINT NOT NULL,
    workflow_profile_id BIGINT,
    workflow_version_id BIGINT,
    model_id VARCHAR(64),
    seed BIGINT,
    qc_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    metadata_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_production_take_source (source_type, source_item_id),
    INDEX idx_production_take_run (run_id),
    INDEX idx_production_take_item (storyboard_item_id),
    INDEX idx_production_take_qc (qc_status),
    CONSTRAINT fk_prod_take_run FOREIGN KEY (run_id) REFERENCES afv_production_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- afv_workflow_profile
-- ================================================
CREATE TABLE IF NOT EXISTS afv_workflow_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_code VARCHAR(64) NOT NULL,
    media_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO',
    render_mode VARCHAR(32) NOT NULL DEFAULT 'T2V',
    capability_tags JSON,
    workflow_id BIGINT NOT NULL,
    workflow_version_policy VARCHAR(32) NOT NULL DEFAULT 'LATEST_PUBLISHED',
    pinned_workflow_version_id BIGINT,
    default_model_id VARCHAR(64),
    quality_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    cost_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    max_duration_seconds INT,
    reference_requirements JSON,
    camera_capabilities JSON,
    audio_capabilities JSON,
    retry_policy JSON,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_profile_code (profile_code),
    INDEX idx_workflow_profile_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- afv_qc_result
-- ================================================
CREATE TABLE IF NOT EXISTS afv_qc_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    take_id BIGINT NOT NULL,
    criterion VARCHAR(64) NOT NULL,
    verdict VARCHAR(16) NOT NULL,
    value_score DECIMAL(10,4),
    threshold_value DECIMAL(10,4),
    evidence_url VARCHAR(512),
    evidence_json JSON,
    reviewed_by BIGINT,
    override_reason VARCHAR(256),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_qc_result_take (take_id),
    INDEX idx_qc_result_verdict (verdict),
    CONSTRAINT fk_qc_result_take FOREIGN KEY (take_id) REFERENCES afv_production_take(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- afv_generation_usage
-- ================================================
CREATE TABLE IF NOT EXISTS afv_generation_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_id BIGINT,
    take_id BIGINT,
    workflow_profile_id BIGINT,
    model_provider VARCHAR(64),
    model_id VARCHAR(64),
    wall_ms BIGINT,
    gpu_ms BIGINT,
    provider_cost DECIMAL(12,6),
    retry_count INT DEFAULT 0,
    repair_count INT DEFAULT 0,
    qc_pass TINYINT(1),
    approved_seconds DECIMAL(10,2),
    human_review_minutes DECIMAL(10,2),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_gen_usage_run (run_id),
    INDEX idx_gen_usage_step (step_id),
    INDEX idx_gen_usage_take (take_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

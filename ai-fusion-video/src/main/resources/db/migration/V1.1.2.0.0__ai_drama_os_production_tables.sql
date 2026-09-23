-- AI Drama OS PR-003: Production Run/Step/Take tables
-- Depends on: V1.1.1.0.0__comfyui_workflow_support.sql

-- ================================================
-- afv_production_run
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目标识',
    storyboard_id BIGINT NOT NULL COMMENT '所属分镜标识',
    storyboard_episode_id BIGINT COMMENT '所属分镜集标识，为空表示整集以下粒度',
    run_type VARCHAR(32) NOT NULL DEFAULT 'BATCH_PRODUCE' COMMENT '运行类型：BATCH_PRODUCE-批量生产，SINGLE_SHOT-单镜头重做',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '运行状态：PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED',
    idempotency_key VARCHAR(128) COMMENT '幂等键，重复提交同一批生产时复用同一条 Run',
    started_at DATETIME COMMENT '开始时间',
    finished_at DATETIME COMMENT '结束时间',
    metadata_json JSON COMMENT '扩展上下文，如触发人、契约快照标识',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_production_run_idem (idempotency_key),
    INDEX idx_production_run_project (project_id),
    INDEX idx_production_run_storyboard (storyboard_id),
    INDEX idx_production_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 生产运行批次';

-- ================================================
-- afv_production_step
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    run_id BIGINT NOT NULL COMMENT '所属生产运行标识',
    storyboard_item_id BIGINT NOT NULL COMMENT '所属分镜镜头标识',
    step_type VARCHAR(32) NOT NULL COMMENT '步骤类型：GENERATE_IMAGE/GENERATE_VIDEO/QC/MEDIA/HUMAN',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '步骤状态：PENDING/READY/QUEUED/RUNNING/SUCCEEDED/FAILED/RETRYING/BLOCKED/REVIEW_REQUIRED/APPROVED/CANCELLED',
    depends_on_json JSON COMMENT '前置步骤标识列表，构成步骤依赖图',
    execution_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO' COMMENT '执行通道：IMAGE/VIDEO/QC/MEDIA/HUMAN',
    execution_ref_id BIGINT COMMENT '执行侧对象标识，如 VIDEO 通道指向 afv_video_task.id',
    attempt INT NOT NULL DEFAULT 0 COMMENT '同一逻辑步骤的第几次尝试，从 0 开始',
    parent_step_id BIGINT COMMENT '被修复的上一次同类型步骤标识',
    error_code VARCHAR(64) COMMENT '失败错误码，供 RepairRouter 选择重试策略',
    error_message TEXT COMMENT '失败详情',
    started_at DATETIME COMMENT '开始时间',
    finished_at DATETIME COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_production_step_idem (run_id, storyboard_item_id, step_type, attempt),
    INDEX idx_production_step_run (run_id),
    INDEX idx_production_step_item (storyboard_item_id),
    INDEX idx_production_step_status (status),
    CONSTRAINT fk_prod_step_run FOREIGN KEY (run_id) REFERENCES afv_production_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 生产步骤';

-- ================================================
-- afv_production_take
-- ================================================
CREATE TABLE IF NOT EXISTS afv_production_take (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    run_id BIGINT NOT NULL COMMENT '所属生产运行标识',
    storyboard_item_id BIGINT NOT NULL COMMENT '所属分镜镜头标识',
    source_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO_ITEM' COMMENT '产物来源类型：VIDEO_ITEM/IMAGE_ITEM',
    source_item_id BIGINT NOT NULL COMMENT '来源产物标识，与 source_type 共同构成幂等键',
    workflow_profile_id BIGINT COMMENT '生成时使用的工作流方案标识',
    workflow_version_id BIGINT COMMENT '生成时固化的工作流版本标识',
    model_id VARCHAR(64) COMMENT '生成时使用的模型 code，对应 afv_ai_model.code',
    seed BIGINT COMMENT '生成随机种子',
    qc_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '质检结论：PENDING/PASS/FAIL/REVIEW_REQUIRED',
    metadata_json JSON COMMENT '产物引用与参数快照，含 videoUrl/firstFrameUrl/lastFrameUrl/duration',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_production_take_source (source_type, source_item_id),
    INDEX idx_production_take_run (run_id),
    INDEX idx_production_take_item (storyboard_item_id),
    INDEX idx_production_take_qc (qc_status),
    CONSTRAINT fk_prod_take_run FOREIGN KEY (run_id) REFERENCES afv_production_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 镜头候选产物（Take）';

-- ================================================
-- afv_workflow_profile
-- ================================================
CREATE TABLE IF NOT EXISTS afv_workflow_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    profile_code VARCHAR(64) NOT NULL COMMENT '方案稳定标识，如 WAN_I2V_STANDARD',
    media_type VARCHAR(16) NOT NULL DEFAULT 'VIDEO' COMMENT '产物介质类型：IMAGE/VIDEO',
    render_mode VARCHAR(32) NOT NULL DEFAULT 'T2V' COMMENT '生成模式：T2V/I2V/R2V 等',
    capability_tags JSON COMMENT '能力标签列表，供 WorkflowRouter 匹配镜头需求',
    workflow_id BIGINT NOT NULL COMMENT '绑定的 ComfyUI 工作流标识',
    workflow_version_policy VARCHAR(32) NOT NULL DEFAULT 'LATEST_PUBLISHED' COMMENT '版本选取策略：LATEST_PUBLISHED/PINNED',
    pinned_workflow_version_id BIGINT COMMENT '策略为 PINNED 时固化的工作流版本标识',
    default_model_id VARCHAR(64) COMMENT '默认模型 code，对应 afv_ai_model.code',
    quality_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT '质量档位：FAST/STANDARD/HIGH',
    cost_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT '成本档位：LOW/STANDARD/HIGH',
    max_duration_seconds INT COMMENT '该方案支持的单镜头最大时长（秒）',
    reference_requirements JSON COMMENT '参考素材要求，如首帧/尾帧/参考图数量',
    camera_capabilities JSON COMMENT '镜头运动能力声明',
    audio_capabilities JSON COMMENT '音频能力声明，如是否支持口型同步',
    retry_policy JSON COMMENT '重试策略，含 maxRetries/retryStrategy/backoffMs',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_workflow_profile_code (profile_code),
    INDEX idx_workflow_profile_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 工作流方案（生成能力的稳定配置入口）';

-- ================================================
-- afv_qc_result
-- ================================================
CREATE TABLE IF NOT EXISTS afv_qc_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    take_id BIGINT NOT NULL COMMENT '被检镜头产物标识',
    criterion VARCHAR(64) NOT NULL COMMENT '质检项：TECHNICAL_VALIDITY/DURATION/FRAME_EXTRACTION/MOTION_ANALYSIS 等',
    verdict VARCHAR(16) NOT NULL COMMENT '单项结论：PASS/FAIL/REVIEW_REQUIRED',
    value_score DECIMAL(10,4) COMMENT '实测值',
    threshold_value DECIMAL(10,4) COMMENT '判定阈值',
    evidence_url VARCHAR(512) COMMENT '证据文件地址，如抽帧目录',
    evidence_json JSON COMMENT '证据明细，供质检失败归因',
    reviewed_by BIGINT COMMENT '人工复核人标识',
    override_reason VARCHAR(256) COMMENT '人工放行原因，必须留痕',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_qc_result_take (take_id),
    INDEX idx_qc_result_verdict (verdict),
    CONSTRAINT fk_qc_result_take FOREIGN KEY (take_id) REFERENCES afv_production_take(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 质检结果（每项一行）';

-- ================================================
-- afv_generation_usage
-- ================================================
CREATE TABLE IF NOT EXISTS afv_generation_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    run_id BIGINT NOT NULL COMMENT '所属生产运行标识',
    step_id BIGINT COMMENT '对应生产步骤标识',
    take_id BIGINT COMMENT '对应镜头产物标识',
    workflow_profile_id BIGINT COMMENT '使用的工作流方案标识',
    model_provider VARCHAR(64) COMMENT '模型供应方',
    model_id VARCHAR(64) COMMENT '模型 code，对应 afv_ai_model.code',
    wall_ms BIGINT COMMENT '端到端墙钟耗时（毫秒）',
    gpu_ms BIGINT COMMENT 'GPU 占用时长（毫秒）',
    provider_cost DECIMAL(12,6) COMMENT '供应商计费金额',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    repair_count INT DEFAULT 0 COMMENT '修复路由触发的重做次数',
    qc_pass TINYINT(1) COMMENT '是否一次通过质检：0-否，1-是，用于计算 FPR',
    approved_seconds DECIMAL(10,2) COMMENT '从产出到被选定的等待时长（秒）',
    human_review_minutes DECIMAL(10,2) COMMENT '人工介入耗时（分钟）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_gen_usage_run (run_id),
    INDEX idx_gen_usage_step (step_id),
    INDEX idx_gen_usage_take (take_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Drama OS 生成用量与成本核算';

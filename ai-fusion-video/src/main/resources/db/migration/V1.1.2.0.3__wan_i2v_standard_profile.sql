-- AI Drama OS PR-009: WAN_I2V_STANDARD first WorkflowProfile
-- workflow_id 为 NOT NULL，因此仅在已存在 ComfyUI 工作流时落种子行：
-- 空库上子查询无结果 → 插入 0 行，而不是把 NULL 写进 NOT NULL 列导致迁移失败。
INSERT INTO afv_workflow_profile (
    profile_code, media_type, render_mode, capability_tags,
    workflow_id, workflow_version_policy, default_model_id,
    quality_tier, cost_tier, max_duration_seconds,
    reference_requirements, camera_capabilities, audio_capabilities,
    retry_policy, enabled
)
SELECT
    'WAN_I2V_STANDARD',
    'VIDEO',
    'I2V',
    '["image-to-video","wan","standard-quality","single-character"]',
    w.id,
    'LATEST_PUBLISHED',
    'wan-2.1-i2v',
    'STANDARD',
    'STANDARD',
    10,
    '{"firstFrame": true, "lastFrame": false, "referenceImages": 0}',
    '{"cameraMovement": true, "cameraFixed": false, "cameraAngle": true}',
    '{"audio": false, "lipsync": false}',
    '{"maxRetries": 3, "retryStrategy": "RETRY_NEW_SEED", "backoffMs": 5000}',
    1
FROM (
    SELECT id FROM afv_comfyui_workflow ORDER BY id LIMIT 1
) w
WHERE NOT EXISTS (
    SELECT 1 FROM afv_workflow_profile WHERE profile_code = 'WAN_I2V_STANDARD'
);

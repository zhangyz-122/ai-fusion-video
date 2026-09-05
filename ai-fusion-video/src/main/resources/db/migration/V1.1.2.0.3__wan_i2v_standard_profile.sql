-- AI Drama OS PR-009: WAN_I2V_STANDARD first WorkflowProfile
INSERT INTO afv_workflow_profile (
    profile_code, media_type, render_mode, capability_tags,
    workflow_id, workflow_version_policy, default_model_id,
    quality_tier, cost_tier, max_duration_seconds,
    reference_requirements, camera_capabilities, audio_capabilities,
    retry_policy, enabled
) VALUES (
    'WAN_I2V_STANDARD',
    'VIDEO',
    'I2V',
    '["image-to-video","wan","standard-quality","single-character"]',
    (SELECT id FROM afv_comfyui_workflow LIMIT 1),
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
);

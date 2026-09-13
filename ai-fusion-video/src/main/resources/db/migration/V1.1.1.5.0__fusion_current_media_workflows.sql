-- Promote the current local media workflows into Fusion's registry.
-- Old Z-Image model rows are logically retired; historical task rows remain intact.
SET @comfy_api_id = (
  SELECT id FROM afv_api_config
  WHERE platform = _utf8mb4'comfyui' COLLATE utf8mb4_unicode_ci AND deleted = 0
  ORDER BY id LIMIT 1
);

SET @seedvr2_code = 'seedvr2_hd_video_upscale';
SET @seedvr2_api = '{"10":{"class_type":"SeedVR2VideoUpscaler","inputs":{"batch_size":5,"color_correction":"lab","dit":["14",0],"enable_debug":false,"image":["22",0],"latent_noise_scale":0.0,"max_resolution":1920,"offload_device":"cpu","prepend_frames":0,"resolution":1080,"seed":42,"temporal_overlap":2,"uniform_batch_size":true,"vae":["13",0]}},"13":{"class_type":"SeedVR2LoadVAEModel","inputs":{"cache_model":false,"decode_tiled":true,"decode_tile_overlap":128,"decode_tile_size":1024,"device":"cuda:0","encode_tiled":true,"encode_tile_overlap":128,"encode_tile_size":1024,"model":"ema_vae_fp16.safetensors","offload_device":"cpu","tile_debug":"false"}},"14":{"class_type":"SeedVR2LoadDiTModel","inputs":{"attention_mode":"sdpa","blocks_to_swap":0,"cache_model":false,"device":"cuda:0","model":"seedvr2_ema_3b_fp8_e4m3fn.safetensors","offload_device":"none","swap_io_components":false}},"21":{"class_type":"LoadVideo","inputs":{"file":"input.mp4"}},"22":{"class_type":"GetVideoComponents","inputs":{"video":["21",0]}},"23":{"class_type":"VHS_VideoCombine","inputs":{"audio":["22",1],"crf":19,"filename_prefix":"Fusion/SeedVR2_HD_1080P","format":"video/h264-mp4","frame_rate":["22",2],"images":["10",0],"loop_count":0,"pingpong":false,"pix_fmt":"yuv420p","save_metadata":true,"save_output":true}},"24":{"class_type":"CreateVideo","inputs":{"audio":["22",1],"bit_depth":["22",3],"fps":["22",2],"images":["10",0]}}}';
SET @seedvr2_inputs = '{"referenceVideos":[{"inputName":"file","nodeId":"21","valueType":"uploaded_video","index":0}]}';
SET @seedvr2_outputs = '[{"mediaType":"video","nodeId":"23","role":"primary"}]';
SET @seedvr2_nodes = '["CreateVideo","GetVideoComponents","LoadVideo","SeedVR2LoadDiTModel","SeedVR2LoadVAEModel","SeedVR2VideoUpscaler","VHS_VideoCombine"]';

INSERT INTO afv_comfyui_workflow
  (api_config_id, name, code, model_type, description, status, deleted, deleted_id)
SELECT @comfy_api_id,
       'SeedVR2 高清视频放大 1080P',
       @seedvr2_code,
       3,
       '当前本机 SeedVR2 v2.5 视频放大链：保留原音频与帧率，目标短边 1080P，最高边 1920。',
       0,
       0,
       0
WHERE @comfy_api_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM afv_comfyui_workflow
    WHERE api_config_id = @comfy_api_id
      AND code = CONVERT(@seedvr2_code USING utf8mb4) COLLATE utf8mb4_unicode_ci
      AND deleted_id = 0
  );

SET @seedvr2_workflow_id = (
  SELECT id FROM afv_comfyui_workflow
  WHERE api_config_id = @comfy_api_id
    AND code = CONVERT(@seedvr2_code USING utf8mb4) COLLATE utf8mb4_unicode_ci
    AND deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO afv_comfyui_workflow_version
  (workflow_id, version_no, api_workflow_json, input_bindings_json,
   output_bindings_json, required_nodes_json, workflow_hash,
   validation_status, test_status, published, deleted)
SELECT @seedvr2_workflow_id,
       1,
       @seedvr2_api,
       @seedvr2_inputs,
       @seedvr2_outputs,
       @seedvr2_nodes,
       SHA2(CONCAT(@seedvr2_api, CHAR(10), @seedvr2_inputs, CHAR(10), @seedvr2_outputs), 256),
       0,
       0,
       0,
       0
WHERE @seedvr2_workflow_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM afv_comfyui_workflow_version
    WHERE workflow_id = @seedvr2_workflow_id AND version_no = 1
  );

INSERT INTO afv_ai_model
  (name, code, model_protocol, capability_preset_code, model_type, icon, description,
   sort, status, config, default_model, max_concurrency, api_config_id,
   comfyui_workflow_id, support_vision, multimodal_input_types, multimodal_input_transports,
   support_reasoning, reasoning_effort_levels, context_window, deleted, deleted_id)
SELECT 'SeedVR2 高清视频放大 1080P',
       'seedvr2_hd_video_upscale_model',
       src.model_protocol,
       src.capability_preset_code,
       3,
       src.icon,
       'SeedVR2 v2.5 高清视频放大：保留原音频与帧率，短边 1080P，最高边 1920。',
       src.sort,
       0,
       '{"supportReferenceVideos":true,"minReferenceVideos":1,"maxReferenceVideos":1,"referenceVideoInputFormats":["url","data_uri"],"supportDataUriInput":true,"defaultFps":24,"comfyuiPollIntervalMillis":5000,"comfyuiTimeoutMillis":3600000}',
       0,
       src.max_concurrency,
       src.api_config_id,
       @seedvr2_workflow_id,
       0,
       '[]',
       '{}',
       0,
       '[]',
       NULL,
       0,
       0
FROM afv_ai_model src
WHERE src.id = 4
  AND @seedvr2_workflow_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM afv_ai_model
    WHERE code = _utf8mb4'seedvr2_hd_video_upscale_model' COLLATE utf8mb4_unicode_ci
      AND deleted_id = 0
  );

-- Current H3 image generation replaces the old rows that were mislabeled as Z-Image.
INSERT INTO afv_ai_model
  (name, code, model_protocol, capability_preset_code, model_type, icon, description,
   sort, status, config, default_model, max_concurrency, api_config_id,
   comfyui_workflow_id, support_vision, multimodal_input_types, multimodal_input_transports,
   support_reasoning, reasoning_effort_levels, context_window, deleted, deleted_id)
SELECT 'MiniMax H3 T8 文戏生图',
       'minimax_h3_t8_donghua_still_model',
       src.model_protocol,
       src.capability_preset_code,
       src.model_type,
       src.icon,
       '当前 H3 T8 文戏生图：生成单张动画关键帧，供分镜资产和视频首帧使用。',
       src.sort,
       1,
       src.config,
       0,
       src.max_concurrency,
       src.api_config_id,
       src.comfyui_workflow_id,
       src.support_vision,
       src.multimodal_input_types,
       src.multimodal_input_transports,
       src.support_reasoning,
       src.reasoning_effort_levels,
       src.context_window,
       0,
       0
FROM afv_ai_model src
WHERE src.id = 5
  AND NOT EXISTS (
    SELECT 1 FROM afv_ai_model
    WHERE BINARY code = BINARY CONCAT('mini', 'max_h3_t8_donghua_still_model')
      AND deleted_id = 0
  );

INSERT INTO afv_ai_model
  (name, code, model_protocol, capability_preset_code, model_type, icon, description,
   sort, status, config, default_model, max_concurrency, api_config_id,
   comfyui_workflow_id, support_vision, multimodal_input_types, multimodal_input_transports,
   support_reasoning, reasoning_effort_levels, context_window, deleted, deleted_id)
SELECT 'MiniMax H3 T8 文戏参考图生图',
       'minimax_h3_t8_donghua_edit_model',
       src.model_protocol,
       src.capability_preset_code,
       src.model_type,
       src.icon,
       '当前 H3 T8 文戏参考图生图：保留角色身份与构图锚点，生成统一画风关键帧。',
       src.sort,
       1,
       src.config,
       0,
       src.max_concurrency,
       src.api_config_id,
       src.comfyui_workflow_id,
       src.support_vision,
       src.multimodal_input_types,
       src.multimodal_input_transports,
       src.support_reasoning,
       src.reasoning_effort_levels,
       src.context_window,
       0,
       0
FROM afv_ai_model src
WHERE src.id = 6
  AND NOT EXISTS (
    SELECT 1 FROM afv_ai_model
    WHERE BINARY code = BINARY CONCAT('mini', 'max_h3_t8_donghua_edit_model')
      AND deleted_id = 0
  );

UPDATE afv_ai_model
SET status = 0, deleted = 1, deleted_id = id
WHERE id IN (5, 6)
  AND code LIKE _utf8mb4'z_image_turbo%' COLLATE utf8mb4_unicode_ci;

-- The upscale model is activated only after target ComfyUI validation and a smoke run.

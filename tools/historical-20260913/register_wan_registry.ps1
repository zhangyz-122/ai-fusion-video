$ErrorActionPreference = 'Stop'
$out = Join-Path $PSScriptRoot '../../evidence/2026-09-13/workflow-gold'
$dbPassword = $env:FUSION_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw 'Set FUSION_DB_PASSWORD before running the local registry bootstrap.' }

function SqlLiteral([string] $value) {
    if ($null -eq $value) { return 'NULL' }
    return "'" + $value.Replace("'", "''") + "'"
}

function JsonCompact($value) {
    return ($value | ConvertTo-Json -Compress -Depth 100)
}

function New-RegistrySql {
    param(
        [string] $File,
        [string] $Code,
        [string] $Name,
        [string] $Purpose,
        $Bindings,
        $Capabilities,
        $InputContract,
        $OutputBindings,
        $Dependencies,
        $Runtime,
        $ModelConfig
    )
    $workflow = Get-Content -Raw -Encoding UTF8 (Join-Path $out $File) | ConvertFrom-Json -AsHashtable
    $apiJson = JsonCompact $workflow
    $requiredNodes = @($workflow.Values | ForEach-Object { $_['class_type'] } | Sort-Object -Unique)
    $requiredNodesJson = JsonCompact $requiredNodes
    $hash = (Get-FileHash -LiteralPath (Join-Path $out $File) -Algorithm SHA256).Hash.ToLowerInvariant()
    $workflowCode = $Code.ToLowerInvariant()
    $profileCode = $Code.ToUpperInvariant()
    $modelCode = $workflowCode + '_model'
    $description = "Canonical $Purpose workflow; verified on RT-LOCAL-4090-R002"
    $modelDescription = "Canonical $Purpose video model"
    return @"
INSERT INTO afv_comfyui_workflow (api_config_id,name,code,model_type,description,status,deleted,deleted_id)
SELECT 1,$(SqlLiteral $Name),$(SqlLiteral $workflowCode),3,$(SqlLiteral $description),1,0,0
WHERE NOT EXISTS (SELECT 1 FROM afv_comfyui_workflow WHERE api_config_id=1 AND code=$(SqlLiteral $workflowCode) AND deleted_id=0);
SELECT id INTO @wf FROM afv_comfyui_workflow WHERE api_config_id=1 AND code=$(SqlLiteral $workflowCode) AND deleted_id=0 LIMIT 1;
INSERT INTO afv_comfyui_workflow_version (workflow_id,version_no,ui_workflow_json,api_workflow_json,input_bindings_json,output_bindings_json,required_nodes_json,workflow_hash,validation_status,test_status,published,deleted)
SELECT @wf,1,NULL,$(SqlLiteral $apiJson),$(SqlLiteral (JsonCompact $Bindings)),$(SqlLiteral (JsonCompact $OutputBindings)),$(SqlLiteral $requiredNodesJson),$(SqlLiteral $hash),1,1,1,0
WHERE NOT EXISTS (SELECT 1 FROM afv_comfyui_workflow_version WHERE workflow_id=@wf AND version_no=1 AND deleted=0);
SELECT id INTO @ver FROM afv_comfyui_workflow_version WHERE workflow_id=@wf AND version_no=1 AND deleted=0 LIMIT 1;
UPDATE afv_comfyui_workflow SET active_version_id=@ver,status=1 WHERE id=@wf;
INSERT INTO afv_workflow_profile (code,name,workflow_id,purpose,capabilities_json,input_contract_json,output_contract_json,dependency_manifest_json,runtime_requirements_json,status,deleted,deleted_id)
SELECT $(SqlLiteral $profileCode),$(SqlLiteral $Name),@wf,$(SqlLiteral $Purpose),$(SqlLiteral (JsonCompact $Capabilities)),$(SqlLiteral (JsonCompact $InputContract)),$(SqlLiteral (JsonCompact $OutputBindings)),$(SqlLiteral (JsonCompact $Dependencies)),$(SqlLiteral (JsonCompact $Runtime)),1,0,0
WHERE NOT EXISTS (SELECT 1 FROM afv_workflow_profile WHERE code=$(SqlLiteral $profileCode) AND deleted_id=0);
INSERT INTO afv_ai_model (name,code,model_protocol,model_type,description,sort,status,config,default_model,max_concurrency,api_config_id,comfyui_workflow_id,support_vision,multimodal_input_types,multimodal_input_transports,support_reasoning,reasoning_effort_levels,deleted,deleted_id)
SELECT $(SqlLiteral $Name),$(SqlLiteral $modelCode),'comfyui',3,$(SqlLiteral $modelDescription),0,1,$(SqlLiteral (JsonCompact $ModelConfig)),0,5,1,@wf,0,'[]','{}',0,'[]',0,0
WHERE NOT EXISTS (SELECT 1 FROM afv_ai_model WHERE code=$(SqlLiteral $modelCode) AND deleted_id=0);
"@
}

$videoOutput = @(@{ nodeId='215'; mediaType='video'; role='primary' })
$talkOutput = @(@{ nodeId='316'; mediaType='video'; role='primary' })
$runtime = [ordered]@{ runtimeRevision='RT-LOCAL-4090-R002'; comfyUi='0.33.0'; gpu='NVIDIA GeForce RTX 4090'; vramMb=24564; attention='sageattn' }
$wanDeps = [ordered]@{ customNodes=@('ComfyUI-WanVideoWrapper','ComfyUI-VideoHelperSuite','ComfyUI-KJNodes'); modelStack=@('WanVideo/Wan2_1-I2V-14B-480P_fp8_e4m3fn.safetensors','wanvideo/Wan2_1_VAE_bf16.safetensors','umt5-xxl-enc-bf16.safetensors','clip_vision_h.safetensors') }
$i2vBindings = [ordered]@{ prompt=@(@{nodeId='205';inputName='positive_prompt';valueType='string'}); negativePrompt=@(@{nodeId='205';inputName='negative_prompt';valueType='string'}); firstFrame=@(@{nodeId='207';inputName='image';valueType='uploaded_image'}); width=@(@{nodeId='212';inputName='width';valueType='integer'}); height=@(@{nodeId='212';inputName='height';valueType='integer'}); fps=@(@{nodeId='215';inputName='frame_rate';valueType='number'}); seed=@(@{nodeId='213';inputName='seed';valueType='integer'}) }
$flfBindings = [ordered]@{ prompt=@(@{nodeId='205';inputName='positive_prompt';valueType='string'}); negativePrompt=@(@{nodeId='205';inputName='negative_prompt';valueType='string'}); firstFrame=@(@{nodeId='207';inputName='image';valueType='uploaded_image'}); lastFrame=@(@{nodeId='208';inputName='image';valueType='uploaded_image'}); width=@(@{nodeId='212';inputName='width';valueType='integer'}); height=@(@{nodeId='212';inputName='height';valueType='integer'}); fps=@(@{nodeId='215';inputName='frame_rate';valueType='number'}); seed=@(@{nodeId='213';inputName='seed';valueType='integer'}) }
$talkBindings = [ordered]@{ prompt=@(@{nodeId='308';inputName='positive_prompt';valueType='string'}); firstFrame=@(@{nodeId='310';inputName='image';valueType='uploaded_image'}); referenceAudios=@(@{nodeId='301';inputName='audio';valueType='uploaded_audio';index=0}); width=@(@{nodeId='313';inputName='width';valueType='integer'}); height=@(@{nodeId='313';inputName='height';valueType='integer'}); fps=@(@{nodeId='316';inputName='frame_rate';valueType='number'}); seed=@(@{nodeId='314';inputName='seed';valueType='integer'}) }
$i2vContract = [ordered]@{ firstFrame='uploaded_image'; prompt='string'; width='integer'; height='integer'; fps='number'; seed='integer' }
$flfContract = [ordered]@{ firstFrame='uploaded_image'; lastFrame='uploaded_image'; prompt='string'; width='integer'; height='integer'; fps='number'; seed='integer' }
$talkContract = [ordered]@{ firstFrame='uploaded_image'; referenceAudios='uploaded_audio'; prompt='string'; width='integer'; height='integer'; fps='number'; seed='integer' }
$i2vConfig = [ordered]@{ supportFirstFrame=$true; supportLastFrame=$false; supportReferenceAudios=$false; minDuration=1; maxDuration=15; defaultFps=16 }
$flfConfig = [ordered]@{ supportFirstFrame=$true; supportLastFrame=$true; supportReferenceAudios=$false; minDuration=1; maxDuration=15; defaultFps=16 }
$talkConfig = [ordered]@{ supportFirstFrame=$true; supportLastFrame=$false; supportReferenceAudios=$true; minDuration=1; maxDuration=15; defaultFps=16 }
$talkDeps = [ordered]@{ customNodes=@('ComfyUI-WanVideoWrapper','ComfyUI-VideoHelperSuite'); modelStack=@('WanVideo/InfiniteTalk/Wan2_1-InfiniTetalk-Single_fp16.safetensors','WanVideo/Wan2_1-I2V-14B-480P_fp8_e4m3fn.safetensors','wanvideo/Wan2_1_VAE_bf16.safetensors','umt5-xxl-enc-bf16.safetensors','clip_vision_h.safetensors','wav2vec2/wav2vec2-chinese-base_fp16.safetensors') }
$sql = 'START TRANSACTION;' + (New-RegistrySql 'WAN_I2V_STANDARD.json' 'wan_i2v_standard' 'Wan I2V Standard' 'I2V' $i2vBindings @{mode='I2V';formalInputE3=$true} $i2vContract $videoOutput $wanDeps $runtime $i2vConfig) + (New-RegistrySql 'WAN_FLF_STANDARD.json' 'wan_flf_standard' 'Wan FLF Standard' 'FLF' $flfBindings @{mode='FLF';formalInputE3=$true} $flfContract $videoOutput $wanDeps $runtime $flfConfig) + (New-RegistrySql 'WAN_INFINITETALK.json' 'wan_infinitetalk' 'Wan InfiniteTalk' 'INFINITETALK' $talkBindings @{mode='INFINITETALK';formalInputE3=$true} $talkContract $talkOutput $talkDeps $runtime $talkConfig) + 'COMMIT;'
$sql | & docker exec -i fusion-mysql mysql --default-character-set=utf8mb4 -uroot ("-p$dbPassword") ai_fusion_video
if ($LASTEXITCODE -ne 0) { throw "WAN registry SQL failed: $LASTEXITCODE" }
Write-Output 'WAN_REGISTRY_APPLY_PASS'

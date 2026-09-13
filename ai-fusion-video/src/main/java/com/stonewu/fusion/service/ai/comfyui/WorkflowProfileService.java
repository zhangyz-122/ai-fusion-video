package com.stonewu.fusion.service.ai.comfyui;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.WorkflowProfile;
import com.stonewu.fusion.mapper.ai.WorkflowProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Manages logical workflow capability metadata without duplicating executable graphs. */
@Service
@RequiredArgsConstructor
public class WorkflowProfileService {

    public record VideoExecutionResolution(
            WorkflowProfile profile,
            ComfyUiWorkflow workflow,
            ComfyUiWorkflowVersion version) {
    }

    private final WorkflowProfileMapper profileMapper;
    private final ComfyUiWorkflowService workflowService;

    @Transactional
    @CacheEvict(value = "workflowProfile", allEntries = true)
    public Long createProfile(String code,
                              String name,
                              Long workflowId,
                              String purpose,
                              String capabilitiesJson,
                              String inputContractJson,
                              String outputContractJson,
                              String dependencyManifestJson,
                              String runtimeRequirementsJson,
                              Integer status) {
        String normalizedCode = normalizeCode(code);
        requireText(normalizedCode, "WorkflowProfile code 不能为空");
        requireText(name, "WorkflowProfile 名称不能为空");
        ComfyUiWorkflow workflow = workflowService.requireWorkflow(workflowId);
        if (profileMapper.exists(new LambdaQueryWrapper<WorkflowProfile>()
                .eq(WorkflowProfile::getCode, normalizedCode))) {
            throw new BusinessException(400, "WorkflowProfile code 已存在");
        }
        WorkflowProfile profile = WorkflowProfile.builder()
                .code(normalizedCode)
                .name(name.trim())
                .workflowId(workflow.getId())
                .purpose(normalizePurpose(purpose))
                .capabilitiesJson(capabilitiesJson)
                .inputContractJson(inputContractJson)
                .outputContractJson(outputContractJson)
                .dependencyManifestJson(dependencyManifestJson)
                .runtimeRequirementsJson(runtimeRequirementsJson)
                .status(status == null ? 1 : status)
                .build();
        profileMapper.insert(profile);
        return profile.getId();
    }

    @Transactional
    @CacheEvict(value = "workflowProfile", allEntries = true)
    public void updateProfile(Long id,
                              String name,
                              Long workflowId,
                              String purpose,
                              String capabilitiesJson,
                              String inputContractJson,
                              String outputContractJson,
                              String dependencyManifestJson,
                              String runtimeRequirementsJson,
                              Integer status) {
        WorkflowProfile profile = requireProfile(id);
        if (workflowId != null && !workflowId.equals(profile.getWorkflowId())) {
            workflowService.requireWorkflow(workflowId);
            profile.setWorkflowId(workflowId);
        }
        if (name != null) {
            requireText(name, "WorkflowProfile 名称不能为空");
            profile.setName(name.trim());
        }
        if (purpose != null) profile.setPurpose(normalizePurpose(purpose));
        if (capabilitiesJson != null) profile.setCapabilitiesJson(capabilitiesJson);
        if (inputContractJson != null) profile.setInputContractJson(inputContractJson);
        if (outputContractJson != null) profile.setOutputContractJson(outputContractJson);
        if (dependencyManifestJson != null) profile.setDependencyManifestJson(dependencyManifestJson);
        if (runtimeRequirementsJson != null) profile.setRuntimeRequirementsJson(runtimeRequirementsJson);
        if (status != null) profile.setStatus(status);
        profileMapper.updateById(profile);
    }

    @Cacheable(value = "workflowProfile", key = "#id", unless = "#result == null")
    public WorkflowProfile getById(Long id) {
        return id == null ? null : profileMapper.selectById(id);
    }

    public WorkflowProfile requireProfile(Long id) {
        WorkflowProfile profile = getById(id);
        if (profile == null) {
            throw new BusinessException(404, "WorkflowProfile 不存在");
        }
        return profile;
    }

    public List<WorkflowProfile> getEnabledList(String purpose) {
        return profileMapper.selectList(new LambdaQueryWrapper<WorkflowProfile>()
                .eq(WorkflowProfile::getStatus, 1)
                .eq(StrUtil.isNotBlank(purpose), WorkflowProfile::getPurpose, normalizePurpose(purpose))
                .orderByAsc(WorkflowProfile::getCode));
    }

    /**
     * Resolves a profile to the currently published version once, with all ownership and model
     * compatibility checks performed before a production task is submitted.
     */
    public VideoExecutionResolution resolveVideoExecution(Long profileId,
                                                            AiModel model) {
        WorkflowProfile profile = requireProfile(profileId);
        if (!Integer.valueOf(1).equals(profile.getStatus())) {
            throw new BusinessException(400, "WorkflowProfile 已禁用");
        }
        if (model == null || !Integer.valueOf(3).equals(model.getModelType())
                || !Integer.valueOf(1).equals(model.getStatus())) {
            throw new BusinessException(400, "WorkflowProfile 只能用于启用的视频模型");
        }
        ComfyUiWorkflow workflow = workflowService.requireWorkflow(profile.getWorkflowId());
        if (!Integer.valueOf(1).equals(workflow.getStatus())
                || workflow.getActiveVersionId() == null) {
            throw new BusinessException(400, "WorkflowProfile 关联的工作流已禁用或没有已发布版本");
        }
        if (!workflow.getId().equals(model.getComfyuiWorkflowId())) {
            throw new BusinessException(400, "WorkflowProfile 与视频模型绑定的工作流不匹配");
        }
        var version = workflowService.requireVersion(workflow.getActiveVersionId());
        if (!workflow.getId().equals(version.getWorkflowId())
                || !Boolean.TRUE.equals(version.getPublished())) {
            throw new BusinessException(400, "WorkflowProfile 的工作流版本不可执行");
        }
        return new VideoExecutionResolution(profile, workflow, version);
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizePurpose(String value) {
        return StrUtil.isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireText(String value, String message) {
        if (StrUtil.isBlank(value)) throw new BusinessException(400, message);
    }
}

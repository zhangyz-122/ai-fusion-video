package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.WorkflowProfile;
import com.stonewu.fusion.mapper.production.WorkflowProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowProfileService {

    private final WorkflowProfileMapper profileMapper;

    public WorkflowProfile create(WorkflowProfile profile) {
        profileMapper.insert(profile);
        return profile;
    }

    public WorkflowProfile getById(Long id) {
        return profileMapper.selectById(id);
    }

    public WorkflowProfile getByCode(String profileCode) {
        return profileMapper.selectOne(
            new LambdaQueryWrapper<WorkflowProfile>()
                .eq(WorkflowProfile::getProfileCode, profileCode)
                .eq(WorkflowProfile::getEnabled, true));
    }

    public List<WorkflowProfile> listEnabled() {
        return profileMapper.selectList(
            new LambdaQueryWrapper<WorkflowProfile>()
                .eq(WorkflowProfile::getEnabled, true)
                .orderByDesc(WorkflowProfile::getId));
    }

    public WorkflowProfile update(WorkflowProfile profile) {
        profileMapper.updateById(profile);
        return profileMapper.selectById(profile.getId());
    }

    public void enable(Long id) {
        WorkflowProfile p = profileMapper.selectById(id);
        if (p != null) { p.setEnabled(true); profileMapper.updateById(p); }
    }

    public void disable(Long id) {
        WorkflowProfile p = profileMapper.selectById(id);
        if (p != null) { p.setEnabled(false); profileMapper.updateById(p); }
    }

    public void pinWorkflowVersion(Long id, Long workflowVersionId) {
        WorkflowProfile p = profileMapper.selectById(id);
        if (p != null) { p.setPinnedWorkflowVersionId(workflowVersionId); profileMapper.updateById(p); }
    }

    public void delete(Long id) {
        profileMapper.deleteById(id);
    }
}

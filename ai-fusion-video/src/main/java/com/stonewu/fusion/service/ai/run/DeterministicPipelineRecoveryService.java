package com.stonewu.fusion.service.ai.run;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.service.ai.agentscope.context.ProjectContext;
import com.stonewu.fusion.service.script.ScriptService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Ensures pipeline agents cannot report success before their business result exists.
 * This runs on the server after the model has finished, so it does not depend on a
 * browser staying connected long enough to perform the recovery callback.
 */
@Service
@RequiredArgsConstructor
public class DeterministicPipelineRecoveryService {

    private final ScriptService scriptService;
    private final StoryboardService storyboardService;

    public Mono<Void> recover(String agentName, ProjectContext project) {
        if (!isRecoverablePipeline(agentName)) {
            return Mono.empty();
        }
        if (project == null || project.projectId() == null) {
            return Mono.error(new BusinessException(
                    "流水线缺少项目上下文，无法完成结果落库"));
        }

        return Mono.fromRunnable(() -> {
            if ("script_full_parse".equals(agentName)) {
                var script = scriptService.getByProjectId(project.projectId());
                if (script == null) {
                    throw new BusinessException("项目尚未找到剧本");
                }
                scriptService.fallbackParseStructure(script.getId());
                return;
            }

            var storyboard = storyboardService.getByProjectId(project.projectId());
            if (storyboard == null) {
                throw new BusinessException("项目尚未找到分镜脚本");
            }
            storyboardService.fallbackGenerateFromScript(storyboard.getId());
        }).then().subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isRecoverablePipeline(String agentName) {
        return "script_full_parse".equals(agentName)
                || "script_to_storyboard".equals(agentName);
    }
}

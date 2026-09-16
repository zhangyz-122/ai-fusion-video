package com.stonewu.fusion.service.ai.run;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.script.Script;
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

    /**
     * 上下文超限导致 run 失败时的部分完成标注：保留本 run 已成功落库的分集，
     * 把剧本置为解析失败（parsingStatus=3）并在解析进度中说明可 /continue 续跑
     * 或改用自动分块解析。刻意不对整本执行 fallbackParseStructure——那会把
     * 残留的正则切分结果整本塞回去，污染模型已写好的结构化数据。
     */
    public Mono<Void> markPartialOnContextOverflow(String agentName, ProjectContext project) {
        if (!"script_full_parse".equals(agentName)
                || project == null || project.projectId() == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> markPartialOnContextOverflowBlocking(project.projectId()))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void markPartialOnContextOverflowBlocking(Long projectId) {
        Script script = scriptService.getByProjectId(projectId);
        if (script == null) {
            return;
        }
        int persistedEpisodes = scriptService.listEpisodes(script.getId()).size();
        scriptService.updateParsingStatus(
                script.getId(),
                3,
                "已保存 " + persistedEpisodes
                        + " 集后因剧本超出模型上下文中断，已落库的分集已保留；可继续解析（/continue）或改用自动分块解析");
    }
}

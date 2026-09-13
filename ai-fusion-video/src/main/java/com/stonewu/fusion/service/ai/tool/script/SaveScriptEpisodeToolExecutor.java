package com.stonewu.fusion.service.ai.tool.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.ToolExecutor;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.script.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 保存集记录工具（save_script_episode）
 * <p>
 * 创建/更新集记录（标题、概述、原文），自动计算 sort_order。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaveScriptEpisodeToolExecutor implements ToolExecutor {

    private final ScriptService scriptService;
    private final ToolResourceAccessGuard accessGuard;

    @Override
    public String getToolName() {
        return "save_script_episode";
    }

    @Override
    public String getDisplayName() {
        return "保存集记录";
    }

    @Override
    public String getToolDescription() {
        return """
                创建或更新剧本的一集记录。如果该集数已存在则更新，不存在则创建。
                scriptId、episodeNumber、title 是正常调用时必须提供的业务参数；如果模型遗漏参数，系统会根据当前项目上下文进行一次受控兜底，禁止重复发送空参数。
                建议传入 sortOrder 排序值进行排序（默认设置为集号），若不传入则默认使用集号进行排序。
                返回值中包含 episode_version，请在后续调用 save_script_scene_items 时传入此值。
                """;
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "scriptId": {
                            "type": "integer",
                            "description": "剧本ID"
                        },
                        "episodeNumber": {
                            "type": "integer",
                            "description": "集数编号（如 1, 2, 3）"
                        },
                        "title": {
                            "type": "string",
                            "description": "集标题（如 '第一集' 或 '第一集：夜叩门'）"
                        },
                        "synopsis": {
                            "type": "string",
                            "description": "本集剧情概述（100-200字）"
                        },
                        "rawContent": {
                            "type": "string",
                            "description": "本集原始剧本文本"
                        },
                        "sourceType": {
                            "type": "integer",
                            "description": "内容来源：0-手动 1-AI创作 2-文本解析"
                        },
                        "sortOrder": {
                            "type": "integer",
                            "description": "剧本分集排序值。默认应设为与该集的 episodeNumber（集号）相同的值（例如第1集传 1，第2集传 2）。"
                        }
                    },
                    "description": "正常调用必须传入 scriptId、episodeNumber、title。为了兼容不支持严格函数参数的本地模型，缺参由执行器返回可恢复结果，不在框架层直接拦截。"
                }
                """;
    }

    @Override
    public String execute(String toolInput, ToolExecutionContext context) {
        try {
            JSONObject params = JSONUtil.parseObj(toolInput);
            Long scriptId = params.getLong("scriptId");
            Integer episodeNumber = params.getInt("episodeNumber");
            String title = params.getStr("title");
            String synopsis = params.getStr("synopsis");
            String rawContent = firstNonBlank(
                    params.getStr("rawContent"),
                    // 兼容旧提示词/旧模型曾输出的字段名，统一落到数据库的 raw_content。
                    params.getStr("originalText"));
            Integer sourceType = params.getInt("sourceType");
            Integer sortOrder = params.getInt("sortOrder");

            if (scriptId == null && context.getProjectId() != null) {
                Script script = scriptService.getByProjectId(context.getProjectId());
                if (script != null) {
                    scriptId = script.getId();
                }
            }

            if (scriptId == null) {
                return JSONUtil.createObj().set("status", "error")
                        .set("message", "缺少 scriptId，且当前任务没有可用的项目上下文；请先调用 get_project_script 获取真实 scriptId，不要重试空参数").toString();
            }

            accessGuard.requireScript(scriptId, context.getUserId());

            // 完整剧本解析偶尔会被兼容模型生成为 {}。已有原文时直接走确定性兜底，
            // 确保“提示成功但剧本为空”不会再次发生；无原文的创作任务则明确报错，避免凭空建集。
            if (episodeNumber == null && (title == null || title.isBlank())
                    && (rawContent == null || rawContent.isBlank())) {
                Script script = scriptService.getById(scriptId);
                if (script.getRawContent() != null && !script.getRawContent().isBlank()) {
                    Script parsed = scriptService.fallbackParseStructure(scriptId);
                    JSONArray episodes = new JSONArray();
                    scriptService.listEpisodes(parsed.getId()).forEach(episode -> episodes.add(
                            JSONUtil.createObj()
                                    .set("scriptEpisodeId", episode.getId())
                                    .set("episodeNumber", episode.getEpisodeNumber())
                                    .set("title", episode.getTitle())
                                    .set("episode_version", episode.getVersion())));
                    return JSONUtil.createObj()
                            .set("status", "success")
                            .set("recovered", true)
                            .set("episodes", episodes)
                            .set("message", "模型未提供分集参数，已根据剧本原文完成确定性分集解析；请调用 get_script_structure 获取最新结构，不要重复保存空参数")
                            .toString();
                }
                return JSONUtil.createObj().set("status", "error")
                        .set("message", "缺少 episodeNumber 和 title，当前剧本也没有原文可用于兜底解析；请补齐参数后再调用").toString();
            }

            if (episodeNumber == null) {
                episodeNumber = 1;
            }
            if (title == null || title.isBlank()) {
                title = "第" + episodeNumber + "集";
            }
            if (sourceType == null && rawContent != null && !rawContent.isBlank()) {
                sourceType = 2;
            }
            ScriptEpisode episode = scriptService.saveEpisode(scriptId, episodeNumber, title,
                    synopsis, rawContent, sourceType, sortOrder);

            JSONObject resultObj = JSONUtil.createObj()
                    .set("scriptEpisodeId", episode.getId())
                    .set("episodeNumber", episodeNumber)
                    .set("title", title)
                    .set("episode_version", episode.getVersion())
                    .set("message", String.format("第%d集 \"%s\" 保存成功", episodeNumber, title));

            return resultObj.toString();
        } catch (Exception e) {
            log.error("保存集记录失败", e);
            return JSONUtil.createObj().set("status", "error").set("message", "保存失败: " + e.getMessage()).toString();
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}

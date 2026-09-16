package com.stonewu.fusion.service.ai.tool.script;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.config.AgentScopeV2Properties;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.ToolExecutor;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.script.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 查询项目剧本元数据工具（get_project_script）
 * <p>
 * 一个项目只有一个剧本，返回该剧本的基础信息、分集概览以及剧本原文首段。
 * <p>
 * 原文不再全量返回：历史上超长剧本（如 74 万字）把整本原文塞进单次工具结果，
 * 下一轮模型请求必然超出上下文并触发供应商 400。现在只返回第一段 + 分段元信息
 * （totalChars/totalSegments/segment/hint），由模型按 hint 调用
 * read_script_segment 逐段续读。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetProjectScriptToolExecutor implements ToolExecutor {

    private final ScriptService scriptService;
    private final ProjectService projectService;
    private final AgentScopeV2Properties properties;

    @Override
    public String getToolName() {
        return "get_project_script";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "查询项目剧本信息";
    }

    @Override
    public String getToolDescription() {
        return """
                查询指定项目的剧本元数据（一个项目只有一个剧本）。
                返回剧本ID、标题、总集数、解析状态、故事梗概、分集概览，以及剧本原文的第一段（分段返回）。
                原文较长时（totalSegments > 1）content 只是第 1 段，请按 hint 调用 read_script_segment(segment=N) 逐段续读，边读边落库。
                """;
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "projectId": {
                            "type": "integer",
                            "description": "项目ID（必填）"
                        }
                    },
                    "required": ["projectId"]
                }
                """;
    }

    @Override
    public String execute(String toolInput, ToolExecutionContext context) {
        try {
            JSONObject params = JSONUtil.parseObj(toolInput);
            Long projectId = params.getLong("projectId");
            if (projectId == null) {
                return JSONUtil.createObj().set("status", "error").set("message", "缺少 projectId").toString();
            }

            Long userId = context.getUserId();

            if (!projectService.canAccessProject(projectId, userId)) {
                return JSONUtil.createObj().set("status", "error")
                        .set("message", "无权访问该项目").toString();
            }

            Script script = scriptService.getByProjectId(projectId);
            if (script == null) {
                return JSONUtil.createObj()
                        .set("projectId", projectId)
                        .set("status", "empty")
                        .set("message", "该项目下尚无剧本").toString();
            }

            String raw = script.getRawContent();
            int segmentChars = properties.getScript().getSegmentChars();
            int totalSegments = ScriptSegmentSplitter.segmentCount(raw, segmentChars);

            JSONObject result = JSONUtil.createObj()
                    .set("projectId", projectId)
                    .set("scriptId", script.getId())
                    .set("title", script.getTitle())
                    .set("totalEpisodes", script.getTotalEpisodes())
                    .set("parsingStatus", script.getParsingStatus())
                    .set("storySynopsis", script.getStorySynopsis())
                    .set("genre", script.getGenre())
                    .set("totalChars", raw == null ? 0 : raw.length())
                    .set("totalSegments", totalSegments);
            appendSegmentPayload(result, raw, segmentChars, totalSegments);

            // 附带分集概览
            List<ScriptEpisode> episodes = scriptService.listEpisodes(script.getId());
            JSONArray episodeList = new JSONArray();
            for (ScriptEpisode ep : episodes) {
                episodeList.add(JSONUtil.createObj()
                        .set("scriptEpisodeId", ep.getId())
                        .set("episodeNumber", ep.getEpisodeNumber())
                        .set("title", ep.getTitle())
                        .set("totalScenes", ep.getTotalScenes()));
            }
            result.set("episodes", episodeList);

            return result.toString();
        } catch (Exception e) {
            log.error("查询项目剧本信息失败", e);
            return JSONUtil.createObj().set("status", "error").set("message", "查询失败: " + e.getMessage()).toString();
        }
    }

    /**
     * 附加原文首段（或空原文说明）。原文只有一段时 content 即完整原文，不附加续读提示。
     */
    private void appendSegmentPayload(
            JSONObject result, String raw, int segmentChars, int totalSegments) {
        if (totalSegments == 0) {
            result.set("segment", 0)
                    .set("content", "")
                    .set("message", "剧本原文为空，没有可读取的内容");
            return;
        }
        ScriptSegmentSplitter.Segment first = ScriptSegmentSplitter.segment(raw, segmentChars, 1);
        result.set("segment", first.index())
                .set("content", first.content());
        if (totalSegments > 1) {
            result.set("hint", "剧本原文共 " + result.getInt("totalChars") + " 字，已按段落边界分为 "
                    + totalSegments + " 段，本次仅返回第 1/" + totalSegments + " 段。"
                    + "请调用 read_script_segment(segment=2) 继续读取后续段；"
                    + "边读边落库（update_script_info / save_script_episode），不要等全文读完才开始写。");
        }
    }
}

package com.stonewu.fusion.service.ai.tool.script;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.config.AgentScopeV2Properties;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.ToolExecutor;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.script.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * 读取剧本原文指定分段工具（read_script_segment）
 * <p>
 * 与 get_project_script 的分段返回配套：段号从 1 开始，最大为该剧本的总分段数。
 * 越界时返回包含总分段数的明确错误，便于模型自行纠正段号。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReadScriptSegmentToolExecutor implements ToolExecutor {

    private final ScriptService scriptService;
    private final ProjectService projectService;
    private final ToolResourceAccessGuard accessGuard;
    private final AgentScopeV2Properties properties;

    @Override
    public String getToolName() {
        return "read_script_segment";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "读取剧本分段";
    }

    @Override
    public String getToolDescription() {
        return """
                按段读取项目剧本原文的后续内容。get_project_script 只返回第 1 段，长剧本必须用本工具逐段续读。
                segment 从 1 开始，最大为 get_project_script 返回的 totalSegments（总分段数）；越界会返回明确错误。
                建议边读边落库（update_script_info / save_script_episode），不要等全文读完。
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
                            "description": "项目ID（与 scriptId 二选一，优先使用）"
                        },
                        "scriptId": {
                            "type": "integer",
                            "description": "剧本ID（缺少 projectId 时使用）"
                        },
                        "segment": {
                            "type": "integer",
                            "description": "分段序号，从 1 开始，最大为 totalSegments"
                        },
                        "segmentIndex": {
                            "type": "integer",
                            "description": "分段序号的别名；未传 segment 时使用"
                        }
                    },
                    "required": []
                }
                """;
    }

    @Override
    public String execute(String toolInput, ToolExecutionContext context) {
        try {
            JSONObject params = JSONUtil.parseObj(toolInput);
            Long projectId = params.getLong("projectId");
            Long scriptId = params.getLong("scriptId");
            Integer segment = firstNonBlank(params.getInt("segment"), params.getInt("segmentIndex"));
            if (segment == null) {
                return JSONUtil.createObj().set("status", "error")
                        .set("message", "缺少 segment 参数（分段序号从 1 开始，最大为总分段数）").toString();
            }

            Script script = resolveScript(projectId, scriptId, context);
            if (script == null) {
                return JSONUtil.createObj().set("status", "error")
                        .set("message", "缺少 projectId（或 scriptId），无法定位剧本").toString();
            }

            String raw = script.getRawContent();
            int segmentChars = properties.getScript().getSegmentChars();
            ScriptSegmentSplitter.Segment result;
            try {
                result = ScriptSegmentSplitter.segment(raw, segmentChars, segment);
            } catch (IllegalArgumentException outOfRange) {
                return JSONUtil.createObj().set("status", "error")
                        .set("message", outOfRange.getMessage()).toString();
            }
            int totalSegments = ScriptSegmentSplitter.segmentCount(raw, segmentChars);

            JSONObject payload = JSONUtil.createObj()
                    .set("status", "success")
                    .set("projectId", script.getProjectId())
                    .set("scriptId", script.getId())
                    .set("segment", result.index())
                    .set("totalSegments", totalSegments)
                    .set("totalChars", raw == null ? 0 : raw.length())
                    .set("fromChar", result.fromChar())
                    .set("toChar", result.toChar())
                    .set("content", result.content());
            if (result.index() < totalSegments) {
                payload.set("hint", "本段已读完，还有 " + (totalSegments - result.index())
                        + " 段未读。继续调用 read_script_segment(segment=" + (result.index() + 1)
                        + ")；边读边落库，已到最后一段（segment=" + totalSegments + "）时不要再调用。");
            } else {
                payload.set("hint", "这已经是最后一段（" + totalSegments + "/" + totalSegments
                        + "），原文已全部读完，请继续完成剩余分集的落库。");
            }
            return payload.toString();
        } catch (Exception e) {
            log.error("读取剧本分段失败", e);
            return JSONUtil.createObj().set("status", "error").set("message", "读取失败: " + e.getMessage()).toString();
        }
    }

    /**
     * 优先用 projectId 走项目权限校验定位剧本；只传 scriptId 时走剧本级权限守卫。
     */
    private Script resolveScript(Long projectId, Long scriptId, ToolExecutionContext context) {
        if (projectId != null) {
            if (!projectService.canAccessProject(projectId, context.getUserId())) {
                throw new IllegalStateException("无权访问该项目");
            }
            return scriptService.getByProjectId(projectId);
        }
        if (scriptId != null) {
            return accessGuard.requireScript(scriptId, context.getUserId());
        }
        return null;
    }

    private Integer firstNonBlank(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }
}

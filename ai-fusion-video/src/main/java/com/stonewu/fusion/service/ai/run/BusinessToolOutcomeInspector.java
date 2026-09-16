package com.stonewu.fusion.service.ai.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.stonewu.fusion.service.ai.run.model.AgentEventEnvelope;

import java.util.Set;

/**
 * 从事件流中识别业务工具成功/失败的启发式判定（纯函数）。
 * <p>
 * 从 {@link DefaultRunExecutionSupervisor} 拆出：supervisor 处于行数预算
 * 白名单棘轮之下，此处集中承载事件 payload 的字段嗅探逻辑，行为保持不变。
 */
final class BusinessToolOutcomeInspector {

    private BusinessToolOutcomeInspector() {
    }

    static void recordSuccessfulBusinessTool(
            Set<String> requiredBusinessTools,
            AgentEventEnvelope event,
            Set<String> successfulBusinessTools,
            Set<String> failedBusinessToolCalls) {
        if (event.rawEventType().endsWith("TOOL_RESULT_TEXT_DELTA")
                || event.rawEventType().endsWith("TOOL_RESULT_DATA_DELTA")) {
            String delta = firstText(event.payload(), "delta", "content", "text");
            if (event.toolCallId() != null && looksLikeToolFailure(delta)) {
                failedBusinessToolCalls.add(event.toolCallId());
            }
            return;
        }
        if (!"TOOL_FINISHED".equals(event.outputType())) {
            return;
        }
        if (requiredBusinessTools.isEmpty()) {
            return;
        }
        JsonNode payload = event.payload();
        String toolName = firstText(payload, "toolCallName", "toolName", "name");
        String state = firstText(payload, "state", "status", "toolStatus");
        if (toolName != null && requiredBusinessTools.contains(toolName)
                && "SUCCESS".equalsIgnoreCase(state)
                && !failedBusinessToolCalls.contains(event.toolCallId())
                && !looksLikeToolFailure(firstText(payload, "toolResult", "content", "text"))) {
            successfulBusinessTools.add(toolName);
        }
    }

    static boolean looksLikeToolFailure(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("error:")
                || normalized.contains("\"status\":\"error\"")
                || normalized.contains("\"status\": \"error\"")
                || normalized.contains("parameter validation failed")
                || normalized.contains("tool execution failed");
    }

    static String firstText(JsonNode object, String... fields) {
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (value != null && value.isTextual() && !value.textValue().isBlank()) {
                return value.textValue();
            }
        }
        return null;
    }
}

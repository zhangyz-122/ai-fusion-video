package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies explicit platform bindings to a deep copy of an immutable workflow version. */
@Component
@RequiredArgsConstructor
public class ComfyUiWorkflowRenderer {

    private final ObjectMapper objectMapper;
    private final ComfyUiWorkflowDocumentService documentService;

    public ObjectNode render(int modelType,
                             ComfyUiWorkflowVersion version,
                             Map<String, Object> values) {
        if (version == null) {
            throw new BusinessException(400, "ComfyUI 工作流版本不能为空");
        }
        ObjectNode workflow = documentService.parseApiWorkflow(version.getApiWorkflowJson()).deepCopy();
        List<ComfyUiInputBinding> bindings = documentService.parseInputBindings(
                modelType, version.getApiWorkflowJson(), version.getInputBindingsJson());
        pruneUnusedReferenceImageBranches(workflow, bindings, values);
        for (ComfyUiInputBinding binding : bindings) {
            if (values == null || !values.containsKey(binding.businessField())) {
                continue;
            }
            Object rawValue = values.get(binding.businessField());
            if (rawValue == null) {
                continue;
            }
            if (isUnusedReferenceImageSlot(rawValue, binding)) {
                continue;
            }
            Object selectedValue = selectIndexedValue(rawValue, binding);
            JsonNode renderedValue = convertValue(selectedValue, binding);
            ObjectNode node = (ObjectNode) workflow.get(binding.nodeId());
            ((ObjectNode) node.get("inputs")).set(binding.inputName(), renderedValue);
        }
        return workflow;
    }

    /**
     * Indexed reference-image bindings are optional slots in H3 workflows.
     * Disconnect unused branches instead of duplicating a supplied image or
     * leaving a placeholder image connected to the conditioning node.
     */
    private void pruneUnusedReferenceImageBranches(ObjectNode workflow,
                                                    List<ComfyUiInputBinding> bindings,
                                                    Map<String, Object> values) {
        int supplied = 0;
        Object raw = values == null ? null : values.get("referenceImages");
        if (raw instanceof List<?> list) {
            supplied = list.size();
        } else if (raw != null && !raw.toString().isBlank()) {
            supplied = 1;
        }
        for (ComfyUiInputBinding binding : bindings) {
            if (!"referenceImages".equals(binding.businessField())
                    || !"uploaded_image".equals(binding.valueType())
                    || binding.index() == null
                    || binding.index() < supplied) {
                continue;
            }
            removeLinkedBranch(workflow, binding.nodeId());
        }
    }

    private boolean isUnusedReferenceImageSlot(Object rawValue, ComfyUiInputBinding binding) {
        return "referenceImages".equals(binding.businessField())
                && "uploaded_image".equals(binding.valueType())
                && binding.index() != null
                && rawValue instanceof List<?> list
                && binding.index() >= list.size();
    }

    /** Remove the graph branch from a source LoadImage node to its consumer. */
    private void removeLinkedBranch(ObjectNode workflow, String sourceNodeId) {
        Set<String> disconnected = new HashSet<>();
        disconnected.add(sourceNodeId);
        boolean changed;
        do {
            changed = false;
            Iterator<Map.Entry<String, JsonNode>> nodes = workflow.fields();
            while (nodes.hasNext()) {
                Map.Entry<String, JsonNode> entry = nodes.next();
                if (!(entry.getValue() instanceof ObjectNode node)
                        || !(node.get("inputs") instanceof ObjectNode inputs)) {
                    continue;
                }
                boolean removed = false;
                Iterator<Map.Entry<String, JsonNode>> inputFields = inputs.fields();
                while (inputFields.hasNext()) {
                    Map.Entry<String, JsonNode> input = inputFields.next();
                    if (isLinkTo(input.getValue(), disconnected)) {
                        inputFields.remove();
                        removed = true;
                    }
                }
                if (removed && !containsLink(inputs)) {
                    changed |= disconnected.add(entry.getKey());
                }
            }
        } while (changed);
    }

    private boolean isLinkTo(JsonNode value, Set<String> nodeIds) {
        return value != null && value.isArray() && value.size() >= 1
                && value.get(0).isTextual() && nodeIds.contains(value.get(0).asText());
    }

    private boolean containsLink(ObjectNode inputs) {
        Iterator<JsonNode> values = inputs.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (value.isArray() && value.size() >= 1 && value.get(0).isTextual()) {
                return true;
            }
        }
        return false;
    }

    private Object selectIndexedValue(Object rawValue, ComfyUiInputBinding binding) {
        if (binding.index() == null) {
            if (rawValue instanceof List<?> values
                    && binding.valueType().startsWith("uploaded_")
                    && values.size() == 1) {
                return values.getFirst();
            }
            return rawValue;
        }
        if (!(rawValue instanceof List<?> values) || binding.index() >= values.size()) {
            throw new BusinessException(400,
                    "工作流输入 " + binding.businessField() + " 缺少索引 " + binding.index());
        }
        return values.get(binding.index());
    }

    private JsonNode convertValue(Object value, ComfyUiInputBinding binding) {
        try {
            return switch (binding.valueType()) {
                case "string", "uploaded_image", "uploaded_video", "uploaded_audio" ->
                        objectMapper.getNodeFactory().textNode(requireText(value, binding));
                case "integer" -> objectMapper.getNodeFactory().numberNode(toLong(value, binding));
                case "number" -> objectMapper.getNodeFactory().numberNode(toDouble(value, binding));
                case "boolean" -> objectMapper.getNodeFactory().booleanNode(toBoolean(value, binding));
                case "string_list" -> stringList(value, binding);
                default -> throw new BusinessException(400,
                        "不支持的 ComfyUI 输入绑定类型: " + binding.valueType());
            };
        } catch (NumberFormatException e) {
            throw new BusinessException(400,
                    "工作流输入 " + binding.businessField() + " 不是有效数字");
        }
    }

    private String requireText(Object value, ComfyUiInputBinding binding) {
        String text = value == null ? "" : value.toString();
        if (text.isBlank()) {
            throw new BusinessException(400, "工作流输入不能为空: " + binding.businessField());
        }
        return text;
    }

    private long toLong(Object value, ComfyUiInputBinding binding) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(requireText(value, binding));
    }

    private double toDouble(Object value, ComfyUiInputBinding binding) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(requireText(value, binding));
    }

    private boolean toBoolean(Object value, ComfyUiInputBinding binding) {
        if (value instanceof Boolean bool) return bool;
        String text = requireText(value, binding);
        if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
            throw new BusinessException(400,
                    "工作流输入 " + binding.businessField() + " 不是有效布尔值");
        }
        return Boolean.parseBoolean(text);
    }

    private ArrayNode stringList(Object value, ComfyUiInputBinding binding) {
        if (!(value instanceof List<?> values)) {
            throw new BusinessException(400,
                    "工作流输入 " + binding.businessField() + " 必须是列表");
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (Object item : values) {
            if (item == null || item.toString().isBlank()) {
                throw new BusinessException(400,
                        "工作流输入 " + binding.businessField() + " 包含空值");
            }
            result.add(item.toString());
        }
        return result;
    }
}

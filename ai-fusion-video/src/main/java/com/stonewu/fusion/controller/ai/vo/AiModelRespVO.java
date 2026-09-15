package com.stonewu.fusion.controller.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "AI模型响应")
@Data
public class AiModelRespVO {
    private Long id;
    private String name;
    private String code;
    private String modelProtocol;
    private String capabilityPresetCode;
    private Integer modelType;
    private String icon;
    private String description;
    private Integer sort;
    private Integer status;
    private String config;
    private Integer maxConcurrency;
    private Boolean defaultModel;
    private Boolean supportVision;
    private List<String> multimodalInputTypes;
    private Map<String, List<String>> multimodalInputTransports;
    private Boolean supportReasoning;
    private List<String> reasoningEffortLevels;
    private Integer contextWindow;
    private Long apiConfigId;
    private Long comfyuiWorkflowId;
    @Schema(description = "是否支持工具调用：true 支持，false 不支持（无法完成 Agent 完整解析），null 未知（如 Ollama 不在线）")
    private Boolean supportsToolCalls;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

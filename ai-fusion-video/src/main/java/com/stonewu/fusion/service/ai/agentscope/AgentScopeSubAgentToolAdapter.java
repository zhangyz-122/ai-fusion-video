package com.stonewu.fusion.service.ai.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.config.ai.AiAgentDefinition;
import com.stonewu.fusion.enums.ai.AgentRunStatus;
import com.stonewu.fusion.service.ai.agentscope.context.AgentRunContext;
import com.stonewu.fusion.service.ai.agentscope.context.CancellationContext;
import com.stonewu.fusion.service.ai.agentscope.context.ProjectContext;
import com.stonewu.fusion.service.ai.agentscope.context.ToolPermissionContext;
import com.stonewu.fusion.service.ai.agentscope.kernel.AgentKernelSpec;
import com.stonewu.fusion.service.ai.agentscope.kernel.AgentKernelSpecFactory;
import com.stonewu.fusion.service.ai.agentscope.tool.AbstractPlatformAgentTool;
import com.stonewu.fusion.service.ai.agentscope.tool.AgentScopeToolSchema;
import com.stonewu.fusion.service.ai.agentscope.tool.PlatformSubAgentCommand;
import com.stonewu.fusion.service.ai.agentscope.tool.PlatformSubAgentRun;
import com.stonewu.fusion.service.ai.agentscope.tool.PlatformSubAgentRunPort;
import com.stonewu.fusion.service.ai.run.RunLeaseGuard;
import com.stonewu.fusion.service.ai.run.DirectAssetImageGenerationService;
import com.stonewu.fusion.service.ai.run.DirectStoryboardFrameGenerationService;
import com.stonewu.fusion.service.ai.run.DirectStoryboardVideoGenerationService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Executes a durable platform-managed child Harness run as an AgentScope V2 tool call. */
public final class AgentScopeSubAgentToolAdapter extends AbstractPlatformAgentTool {

    private final AiAgentDefinition.SubAgentToolDef definition;
    private final AgentKernelSpec parentSpec;
    private final AgentKernelSpecFactory specFactory;
    private final Supplier<PlatformSubAgentRunPort> childRuns;
    private final RunLeaseGuard leaseGuard;
    private final ObjectMapper objectMapper;
    private final DirectAssetImageGenerationService directAssetImageGenerationService;
    private final DirectStoryboardFrameGenerationService directStoryboardFrameGenerationService;
    private final DirectStoryboardVideoGenerationService directStoryboardVideoGenerationService;

    public AgentScopeSubAgentToolAdapter(
            AiAgentDefinition.SubAgentToolDef definition,
            AgentKernelSpec parentSpec,
            AgentScopeToolSchema.PreparedSchema schema,
            AgentKernelSpecFactory specFactory,
            Supplier<PlatformSubAgentRunPort> childRuns,
            RunLeaseGuard leaseGuard,
            ObjectMapper objectMapper,
            DirectAssetImageGenerationService directAssetImageGenerationService,
            DirectStoryboardFrameGenerationService directStoryboardFrameGenerationService,
            DirectStoryboardVideoGenerationService directStoryboardVideoGenerationService) {
        super(builder(definition, schema));
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.parentSpec = Objects.requireNonNull(parentSpec, "parentSpec must not be null");
        this.specFactory = Objects.requireNonNull(specFactory, "specFactory must not be null");
        this.childRuns = Objects.requireNonNull(childRuns, "childRuns must not be null");
        this.leaseGuard = Objects.requireNonNull(leaseGuard, "leaseGuard must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.directAssetImageGenerationService = directAssetImageGenerationService;
        this.directStoryboardFrameGenerationService = directStoryboardFrameGenerationService;
        this.directStoryboardVideoGenerationService = directStoryboardVideoGenerationService;
    }

    /** Backward-compatible constructor for focused adapter tests and non-asset callers. */
    public AgentScopeSubAgentToolAdapter(
            AiAgentDefinition.SubAgentToolDef definition,
            AgentKernelSpec parentSpec,
            AgentScopeToolSchema.PreparedSchema schema,
            AgentKernelSpecFactory specFactory,
            Supplier<PlatformSubAgentRunPort> childRuns,
            RunLeaseGuard leaseGuard,
            ObjectMapper objectMapper) {
        this(definition, parentSpec, schema, specFactory, childRuns,
                leaseGuard, objectMapper, null, null, null);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.defer(() -> {
            ToolUseBlock toolUse = requireToolUse(param);
            RuntimeContext runtime = requireRuntimeContext(param);
            AgentRunContext run = requireContext(runtime, AgentRunContext.class);
            CancellationContext cancellation = requireContext(runtime, CancellationContext.class);
            ToolPermissionContext permission = requireContext(
                    runtime, ToolPermissionContext.class);
            ProjectContext project = runtime.get(ProjectContext.class);
            Map<String, Object> input = Objects.requireNonNull(
                    param.getInput(), "AgentScope sub-agent tool input must not be null");
            String message = inputMessage(input);

            // Asset image generation has a deterministic data dependency: a variant must
            // reuse its initial image. Keep this decision outside the language model so
            // it cannot silently fall back to a white-background text-to-image request.
            if ("generate_asset_image".equals(getName())
                    && directAssetImageGenerationService != null) {
                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext toolContext =
                        requireContext(runtime,
                                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext.class);
                Mono<String> invocation = Mono.fromCallable(() -> directAssetImageGenerationService.execute(
                                message,
                                com.stonewu.fusion.service.ai.ToolExecutionContext.builder()
                                        .userId(toolContext.userId())
                                        .ownerType(toolContext.ownerType())
                                        .ownerId(toolContext.ownerId())
                                        .build()))
                        .subscribeOn(Schedulers.boundedElastic());
                return cancellation.checkpoint()
                        .then(assertLease(run))
                        .then(invocation)
                        .flatMap(result -> cancellation.checkpoint()
                                .then(assertLease(run))
                                .thenReturn(directResult(param, result)))
                        .timeout(remaining(run));
            }

            if ("generate_storyboard_frame".equals(getName())
                    && directStoryboardFrameGenerationService != null) {
                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext toolContext =
                        requireContext(runtime,
                                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext.class);
                Mono<String> invocation = Mono.fromCallable(() -> directStoryboardFrameGenerationService.execute(
                                message,
                                com.stonewu.fusion.service.ai.ToolExecutionContext.builder()
                                        .userId(toolContext.userId())
                                        .ownerType(toolContext.ownerType())
                                        .ownerId(toolContext.ownerId())
                                        .build()))
                        .subscribeOn(Schedulers.boundedElastic());
                return cancellation.checkpoint()
                        .then(assertLease(run))
                        .then(invocation)
                        .flatMap(result -> cancellation.checkpoint()
                                .then(assertLease(run))
                                .thenReturn(directResult(param, result)))
                        .timeout(remaining(run));
            }

            if ("generate_storyboard_video".equals(getName())
                    && directStoryboardVideoGenerationService != null) {
                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext toolContext =
                        requireContext(runtime,
                                com.stonewu.fusion.service.ai.agentscope.context.ToolExecutionContext.class);
                String directMessage = directInputMessage(input);
                Mono<String> invocation = Mono.fromCallable(() -> directStoryboardVideoGenerationService.execute(
                                directMessage,
                                com.stonewu.fusion.service.ai.ToolExecutionContext.builder()
                                        .userId(toolContext.userId())
                                        .ownerType(toolContext.ownerType())
                                        .ownerId(toolContext.ownerId())
                                        .build(),
                                project == null ? null : project.projectId()))
                        .subscribeOn(Schedulers.boundedElastic());
                return cancellation.checkpoint()
                        .then(assertLease(run))
                        .then(invocation)
                        .flatMap(result -> cancellation.checkpoint()
                                .then(assertLease(run))
                                .thenReturn(directResult(param, result)))
                        .timeout(remaining(run));
            }

            AgentKernelSpec childSpec = specFactory.createChild(
                    parentSpec, definition, project, input);
            PlatformSubAgentCommand command = new PlatformSubAgentCommand(
                    run.runId(),
                    toolUse.getId(),
                    run.ownerInstanceId(),
                    run.ownerEpoch(),
                    getName(),
                    childSpec,
                    List.of(new UserMessage(message)),
                    project,
                    permission.mode(),
                    run.deadline());
            return cancellation.checkpoint()
                    .then(assertLease(run))
                    .then(Mono.defer(() -> {
                        PlatformSubAgentRunPort childRunPort = childRuns.get();
                        if (childRunPort == null) {
                            return Mono.error(new IllegalStateException(
                                    "Platform sub-agent run service is unavailable"));
                        }
                        return childRunPort.start(command)
                                .flatMap(childRunPort::awaitCompletion);
                    }))
                    .flatMap(child -> cancellation.checkpoint()
                            .then(assertLease(run))
                            .thenReturn(projectResult(param, child)))
                    .timeout(remaining(run));
        });
    }

    private ToolResultBlock projectResult(
            ToolCallParam param, PlatformSubAgentRun child) {
        String result = childResult(child);
        return child.status() == AgentRunStatus.COMPLETED
                ? textResult(param, result)
                : errorResult(param, result);
    }

    private ToolResultBlock directResult(ToolCallParam param, String result) {
        try {
            JsonNode root = objectMapper.readTree(result);
            if ("error".equalsIgnoreCase(root.path("status").asText())) {
                return errorResult(param, result);
            }
        } catch (JsonProcessingException ignored) {
            // Preserve the raw executor response when it is not JSON.
        }
        return textResult(param, result);
    }

    private String inputMessage(Map<String, Object> input) {
        Object explicit = input.get("message");
        if (explicit instanceof String text && !text.isBlank()) {
            return text;
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException(
                    "Platform sub-agent tool input must not be empty: " + getName());
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(
                    "Platform sub-agent tool input is not serializable: " + getName(), failure);
        }
    }

    /**
     * Direct media executors need the complete structured call, including business
     * identifiers that a model may send alongside an optional human-readable message.
     */
    private String directInputMessage(Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(
                    "Platform sub-agent tool input is not serializable: " + getName(), failure);
        }
    }

    private String childResult(PlatformSubAgentRun child) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parentRunId", child.parentRunId());
        result.put("parentToolCallId", child.parentToolCallId());
        result.put("childRunId", child.childRunId());
        result.put("agentName", child.agentName());
        result.put("status", child.status().name());
        result.put("executionMode", "PLATFORM_MANAGED_CHILD_RUN");
        result.put("resultDelivery", "PARENT_TOOL_RESULT");
        result.put("result", child.result());
        result.put("error", child.error());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Failed to serialize platform child run", failure);
        }
    }

    private Mono<Void> assertLease(AgentRunContext run) {
        return leaseGuard.assertLease(run.runId(), run.ownerInstanceId(), run.ownerEpoch());
    }

    private Duration remaining(AgentRunContext run) {
        Duration remaining = Duration.between(Instant.now(), run.deadline());
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalStateException(
                    "Agent run deadline expired before child Agent completion");
        }
        return remaining;
    }

    private RuntimeContext requireRuntimeContext(ToolCallParam param) {
        if (param.getRuntimeContext() == null) {
            throw new IllegalArgumentException(
                    "AgentScope RuntimeContext is required for sub-agent tool: " + getName());
        }
        return param.getRuntimeContext();
    }

    private <T> T requireContext(RuntimeContext runtime, Class<T> type) {
        T value = runtime.get(type);
        if (value == null) {
            throw new IllegalStateException(
                    "AgentScope RuntimeContext is missing " + type.getSimpleName()
                            + " for sub-agent tool " + getName());
        }
        return value;
    }

    private static ToolBase.Builder builder(
            AiAgentDefinition.SubAgentToolDef definition,
            AgentScopeToolSchema.PreparedSchema schema) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (definition.getToolName() == null || definition.getToolName().isBlank()
                || definition.getDescription() == null || definition.getDescription().isBlank()) {
            throw new IllegalArgumentException(
                    "Platform sub-agent tool name and description are required");
        }
        return ToolBase.builder()
                .name(definition.getToolName())
                .description(definition.getDescription())
                .inputSchema(schema.value())
                .readOnly(false)
                .concurrencySafe(true);
    }
}

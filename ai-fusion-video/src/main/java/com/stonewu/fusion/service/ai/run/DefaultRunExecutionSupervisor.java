package com.stonewu.fusion.service.ai.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stonewu.fusion.enums.ai.AgentRunStatus;
import com.stonewu.fusion.enums.ai.AgentRuntimeErrorCode;
import com.stonewu.fusion.enums.ai.AgentTerminalOutputType;
import com.stonewu.fusion.service.ai.agentscope.context.AgentRunContext;
import com.stonewu.fusion.service.ai.agentscope.context.AgentScopeRuntimeContextRequest;
import com.stonewu.fusion.service.ai.agentscope.context.ParentAgentRunContext;
import com.stonewu.fusion.service.ai.agentscope.kernel.AgentKernelSpec;
import com.stonewu.fusion.service.ai.agentscope.kernel.HarnessLeaseCache;
import com.stonewu.fusion.service.ai.agentscope.state.StateStoreSlot;
import com.stonewu.fusion.service.ai.run.kernel.AgentKernelSnapshot;
import com.stonewu.fusion.service.ai.run.kernel.AgentKernelSnapshotBuilder;
import com.stonewu.fusion.service.ai.run.kernel.RunConfigUnavailableException;
import com.stonewu.fusion.service.ai.run.model.AgentEventEnvelope;
import com.stonewu.fusion.service.ai.run.model.CommittedAgentEvent;
import com.stonewu.fusion.service.ai.run.model.ExecutionStopReason;
import com.stonewu.fusion.service.ai.run.model.ResumeAgentExecutionCommand;
import com.stonewu.fusion.service.ai.run.model.RunTerminalRequest;
import com.stonewu.fusion.service.ai.run.model.StartAgentExecutionCommand;
import com.stonewu.fusion.service.ai.run.model.WaitingCheckpoint;
import com.stonewu.fusion.service.ai.run.model.PendingConfirmation;
import io.agentscope.core.message.Msg;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public final class DefaultRunExecutionSupervisor implements RunExecutionSupervisor {

    private final AgentExecutionFactory executionFactory;
    private final OwnedExecutionRegistry executions;
    private final AgentEventChunkCoalescer chunks;
    private final AgentEventJournal journal;
    private final RunTerminalCoordinator terminals;
    private final AgentMessageProjectionService projections;
    private final HarnessLeaseCache kernelLeaseCache;
    private final AgentKernelSnapshotBuilder snapshotBuilder;
    private final RunShutdownCancellationPort shutdownCancellation;
    private final RunLeaseGuard leases;
    private final AgentRunRedisSignalService signals;
    private final AgentEventEnvelopeSanitizer sanitizer;
    private final AgentWaitingStatePort waitingState;
    private DeterministicPipelineRecoveryService deterministicRecovery;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicReference<Mono<Void>> shutdownSignal = new AtomicReference<>();
    private AgentRuntimeMetrics metrics = AgentRuntimeMetrics.noop();

    @Autowired
    void setMetrics(AgentRuntimeMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Autowired
    void setDeterministicRecovery(DeterministicPipelineRecoveryService deterministicRecovery) {
        this.deterministicRecovery = Objects.requireNonNull(
                deterministicRecovery, "deterministicRecovery must not be null");
    }

    public DefaultRunExecutionSupervisor(
            AgentExecutionFactory executionFactory,
            OwnedExecutionRegistry executions,
            AgentEventChunkCoalescer chunks,
            AgentEventJournal journal,
            RunTerminalCoordinator terminals,
            AgentMessageProjectionService projections,
            HarnessLeaseCache kernelLeaseCache,
            AgentKernelSnapshotBuilder snapshotBuilder,
            RunShutdownCancellationPort shutdownCancellation,
            RunLeaseGuard leases,
            AgentRunRedisSignalService signals,
            AgentEventEnvelopeSanitizer sanitizer,
            AgentWaitingStatePort waitingState) {
        this.executionFactory = Objects.requireNonNull(
                executionFactory, "executionFactory must not be null");
        this.executions = Objects.requireNonNull(executions, "executions must not be null");
        this.chunks = Objects.requireNonNull(chunks, "chunks must not be null");
        this.journal = Objects.requireNonNull(journal, "journal must not be null");
        this.terminals = Objects.requireNonNull(terminals, "terminals must not be null");
        this.projections = Objects.requireNonNull(
                projections, "projections must not be null");
        this.kernelLeaseCache = Objects.requireNonNull(
                kernelLeaseCache, "kernelLeaseCache must not be null");
        this.snapshotBuilder = Objects.requireNonNull(
                snapshotBuilder, "snapshotBuilder must not be null");
        this.shutdownCancellation = Objects.requireNonNull(
                shutdownCancellation, "shutdownCancellation must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.signals = Objects.requireNonNull(signals, "signals must not be null");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer must not be null");
        this.waitingState = Objects.requireNonNull(
                waitingState, "waitingState must not be null");
    }

    @Override
    public Mono<Void> start(StartAgentExecutionCommand command) {
        return Mono.defer(() -> {
            requireAccepting();
            StartAgentExecutionCommand safeCommand = Objects.requireNonNull(
                    command, "command must not be null");
            requireStartSnapshot(safeCommand);
            return startResolved(
                    safeCommand.run().runId(),
                    safeCommand.run().ownerInstanceId(),
                    safeCommand.run().ownerEpoch(),
                    safeCommand.run().agentStateSessionId(),
                    safeCommand.run().deadline(),
                     safeCommand.messages(),
                     safeCommand.kernelSpec(),
                     safeCommand.runtimeContextRequest(),
                     safeCommand.kernelSnapshot());
        });
    }

    @Override
    public Mono<Void> resume(ResumeAgentExecutionCommand command) {
        return Mono.defer(() -> {
            requireAccepting();
            ResumeAgentExecutionCommand safeCommand = Objects.requireNonNull(
                    command, "command must not be null");
            requireResumeSnapshot(safeCommand);
            return executionFactory.resolve(safeCommand.kernelSnapshot())
                    .flatMap(spec -> startResolved(
                            safeCommand.run().runId(),
                            safeCommand.run().newOwnerInstanceId(),
                            safeCommand.run().newOwnerEpoch(),
                            safeCommand.run().sessionId(),
                            safeCommand.run().deadline(),
                             safeCommand.messages(),
                             spec,
                             safeCommand.runtimeContextRequest(),
                             safeCommand.kernelSnapshot()))
                    .onErrorResume(RunConfigUnavailableException.class, failure ->
                            terminalFailure(
                                    safeCommand.run().runId(),
                                    safeCommand.run().newOwnerInstanceId(),
                                    safeCommand.run().newOwnerEpoch(),
                                    safeCommand.runtimeContextRequest(),
                                    safeCommand.run().sessionId(),
                                    AgentRuntimeErrorCode.RUN_CONFIG_UNAVAILABLE,
                                    failure.getMessage()));
        });
    }

    private Mono<Void> startResolved(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            String stateSessionId,
            Instant deadline,
             List<Msg> messages,
             AgentKernelSpec spec,
             AgentScopeRuntimeContextRequest runtimeRequest,
             AgentKernelSnapshot kernelSnapshot) {
        requireRuntimeIdentity(
                runId, ownerInstanceId, ownerEpoch,
                stateSessionId, deadline, runtimeRequest);
        AtomicReference<String> pauseType = new AtomicReference<>();
        AtomicLong lastCommittedSequence = new AtomicLong(-1);
        Set<String> successfulBusinessTools = new LinkedHashSet<>();
        Set<String> failedBusinessToolCalls = new HashSet<>();
        Set<String> required = requiredBusinessTools(spec.agentDefinitionStableKey());
        return executionFactory.start(
                        runId,
                        ownerInstanceId,
                        ownerEpoch,
                        stateSessionId,
                        messages,
                        spec,
                        runtimeRequest,
                        deadline)
                .flatMap(execution -> executions.registerAndLaunch(
                        execution,
                        deadline,
                         chunks::coalesce,
                         events -> events.concatMap(
                                         event -> {
                                             BusinessToolOutcomeInspector.recordSuccessfulBusinessTool(
                                                     required, event, successfulBusinessTools,
                                                     failedBusinessToolCalls);
                                             return appendOwned(execution, event)
                                                 .flatMap(committed -> afterAppend(
                                                         execution,
                                                         event,
                                                         committed,
                                                         pauseType,
                                                         lastCommittedSequence));
                                         }, 1)
                                 .then(),
                                 ignored -> signals.cancellations(runId)
                                .next()
                                .flatMap(cancelledRunId ->
                                        leases.observeCancellationSignal(
                                                cancelledRunId,
                                                ownerInstanceId,
                                                ownerEpoch))
                                .onErrorResume(failure -> Mono.empty()),
                         outcome -> completeExecution(
                                 execution,
                                 outcome,
                                 kernelSnapshot,
                                 runtimeRequest,
                                 pauseType.get(),
                                 lastCommittedSequence.get(),
                                 successfulBusinessTools)))
                .onErrorResume(
                        OwnedExecutionRegistry.ExecutionAlreadyOwnedException.class,
                        Mono::error)
                .onErrorResume(failure -> terminalFailure(
                                runId,
                                ownerInstanceId,
                                ownerEpoch,
                                runtimeRequest,
                                stateSessionId,
                                classifyStartFailure(failure),
                                failure.getMessage())
                        .then(Mono.error(failure)));
    }

    private Mono<CommittedAgentEvent> appendOwned(
             AgentExecution execution,
             AgentEventEnvelope event) {
        return appendRequired(
                        execution.runId(),
                        execution.ownerInstanceId(),
                        execution.ownerEpoch(),
                        event,
                        false)
                .flatMap(committed -> mirrorToParent(execution, committed)
                        .thenReturn(committed));
    }

    private Mono<CommittedAgentEvent> afterAppend(
            AgentExecution execution,
            AgentEventEnvelope event,
            CommittedAgentEvent committed,
            AtomicReference<String> pauseType,
            AtomicLong lastCommittedSequence) {
        lastCommittedSequence.set(committed.sequence());
        if (!"REQUIRE_USER_CONFIRM".equals(event.rawEventType())) {
            return Mono.just(committed);
        }
        pauseType.set(event.rawEventType());
        PendingConfirmation candidate = confirmationCandidate(event);
        return waitingState.recordConfirmationCandidate(execution.runId(), candidate)
                .doOnSuccess(ignored -> lastCommittedSequence.set(
                        Math.addExact(committed.sequence(), 1L)))
                .thenReturn(committed);
    }

    private PendingConfirmation confirmationCandidate(AgentEventEnvelope event) {
        JsonNode candidate = event.payload().path("_platformConfirmationCandidate");
        JsonNode decisions = candidate.path("decisionIds");
        if (!candidate.isObject() || !decisions.isArray() || decisions.isEmpty()) {
            throw new IllegalStateException(
                    "Agent confirmation event has no durable candidate payload");
        }
        Set<String> decisionIds = new LinkedHashSet<>();
        decisions.forEach(value -> {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalStateException(
                        "Agent confirmation candidate has an invalid decision id");
            }
            decisionIds.add(value.textValue());
        });
        try {
            return new PendingConfirmation(
                    requiredText(candidate, "replyId"),
                    decisionIds,
                    requiredText(candidate, "pendingToolCallsJson"),
                    requiredText(candidate, "suspendedToolCallsJson"),
                    Instant.parse(requiredText(candidate, "expiresAt")));
        } catch (DateTimeParseException invalidExpiry) {
            throw new IllegalStateException(
                    "Agent confirmation candidate expiry is invalid", invalidExpiry);
        }
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException(
                    "Agent confirmation candidate is missing " + field);
        }
        return value.textValue();
    }

    private Mono<CommittedAgentEvent> appendRequired(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            AgentEventEnvelope event,
            boolean parentWrite) {
        return journal.appendOwned(runId, ownerInstanceId, ownerEpoch, event)
                .flatMap(committed -> committed
                        .map(c -> c.publishRequired()
                                ? signals.publishWakeup(runId, c.sequence())
                                        .onErrorResume(failure -> Mono.empty())
                                        .thenReturn(c)
                                : Mono.just(c))
                        .orElseGet(() -> Mono.error(parentWrite
                                ? new ParentOwnerLostException(runId)
                                : new OwnerLostException(runId))));
    }

    private Mono<Void> mirrorToParent(
            AgentExecution execution,
            CommittedAgentEvent childEvent) {
        ParentAgentRunContext parent = execution.parentRun();
        if (parent == null) {
            return Mono.empty();
        }
        return appendRequired(
                        parent.runId(),
                        parent.ownerInstanceId(),
                        parent.ownerEpoch(),
                        mirroredChildEvent(parent, childEvent),
                        true)
                .then();
    }

    private AgentEventEnvelope mirroredChildEvent(
            ParentAgentRunContext parent,
            CommittedAgentEvent childEvent) {
        AgentEventEnvelope original = childEvent.envelope();
        JsonNode originalPayload = original.payload();
        if (!originalPayload.isObject()) {
            throw new IllegalStateException(
                    "Child Agent event payload must be a JSON object: "
                            + original.rawEventId());
        }
        ObjectNode payload = (ObjectNode) originalPayload;
        payload.put("_platformMirroredChildEvent", true);
        payload.put("childRunId", childEvent.runId());
        payload.put("childSequence", childEvent.sequence());
        String mirrorMaterial = childEvent.runId() + '\0' + original.rawEventId();
        String mirroredRawEventId = "child-" + UUID.nameUUIDFromBytes(
                        mirrorMaterial.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return new AgentEventEnvelope(
                mirroredRawEventId,
                original.rawEventType(),
                "main/" + parent.agentName(),
                original.replyId(),
                original.blockId(),
                original.toolCallId(),
                parent.toolCallId(),
                parent.agentName(),
                original.outputType(),
                payload,
                original.createdAt());
    }

    private Mono<Void> completeExecution(
            AgentExecution execution,
            AgentExecutionHandle.Outcome outcome,
            AgentKernelSnapshot kernelSnapshot,
            AgentScopeRuntimeContextRequest runtimeRequest,
            String pauseType,
            long lastCommittedSequence,
            Set<String> successfulBusinessTools) {
        metrics.providerClosed();
        AgentRunStatus providerStatus = switch (outcome.kind()) {
            case COMPLETED -> AgentRunStatus.COMPLETED;
            case INTERRUPTED -> outcome.stopReason() == ExecutionStopReason.CANCEL_REQUESTED
                    ? AgentRunStatus.CANCELLED
                    : AgentRunStatus.FAILED;
            case OVERFLOW, SOURCE_FAILURE, JOURNAL_FAILURE -> AgentRunStatus.FAILED;
        };
        metrics.providerTerminal(providerStatus);
        if (outcome.kind() == AgentExecutionHandle.OutcomeKind.COMPLETED) {
            String validationError = validateBusinessCompletion(
                    kernelSnapshot.payload().agentDefinitionStableKey(),
                    successfulBusinessTools);
            if (validationError != null) {
                return terminalFailure(
                        execution,
                        AgentRuntimeErrorCode.TOOL_VALIDATION_FAILED,
                        validationError);
            }
        }
        return switch (outcome.kind()) {
            case COMPLETED -> "REQUIRE_USER_CONFIRM".equals(pauseType)
                    ? enterWaitingConfirmation(
                            execution, kernelSnapshot, lastCommittedSequence)
                    : recoverThenComplete(execution, kernelSnapshot, runtimeRequest);
            case OVERFLOW -> terminalFailure(
                    execution,
                    AgentRuntimeErrorCode.AGENT_EVENT_BACKPRESSURE_OVERFLOW,
                    "Agent event ingress exceeded its bounded capacity");
            case SOURCE_FAILURE -> contextOverflowTerminal(
                    execution, kernelSnapshot, runtimeRequest, outcome,
                    successfulBusinessTools);
            case JOURNAL_FAILURE -> outcome.failure() instanceof OwnerLostException
                    ? Mono.empty()
                    : terminalFailure(
                            execution,
                            AgentRuntimeErrorCode.EVENT_PERSIST_FAILED,
                            outcome.failure().getMessage());
            case INTERRUPTED -> outcome.stopReason() == ExecutionStopReason.DEADLINE
                    ? terminalFailure(
                            execution,
                            AgentRuntimeErrorCode.MODEL_TIMEOUT,
                            "Agent run deadline expired")
                    : Mono.empty();
        };
    }

    /**
     * SOURCE_FAILURE 的上下文超限特判：供应商“超出最大消息 tokens/上下文长度”类 400
     * 不透传原始报错，而是写成可行动的人话；本 run 已成功落库过分集时，先保留已落库
     * 内容并注明部分完成（刻意不做整本兜底解析），再落地失败终态。
     */
    private Mono<Void> contextOverflowTerminal(
            AgentExecution execution,
            AgentKernelSnapshot kernelSnapshot,
            AgentScopeRuntimeContextRequest runtimeRequest,
            AgentExecutionHandle.Outcome outcome,
            Set<String> successfulBusinessTools) {
        Throwable failure = outcome.failure();
        if (!ScriptContextOverflowError.matches(failure)) {
            return terminalFailure(
                    execution,
                    AgentRuntimeErrorCode.AGENTSCOPE_INTERNAL_ERROR,
                    failure.getMessage());
        }
        boolean episodePersisted = successfulBusinessTools.contains("save_script_episode");
        Mono<Void> markPartial = episodePersisted && deterministicRecovery != null
                ? deterministicRecovery
                        .markPartialOnContextOverflow(
                                kernelSnapshot.payload().agentDefinitionStableKey(),
                                runtimeRequest.project())
                        // 部分完成标注失败不阻断失败终态落地
                        .onErrorResume(ignored -> Mono.empty())
                : Mono.empty();
        return markPartial.then(terminalFailure(
                execution,
                AgentRuntimeErrorCode.AGENTSCOPE_INTERNAL_ERROR,
                ScriptContextOverflowError.terminalMessage(episodePersisted)));
    }

    private Mono<Void> recoverThenComplete(
            AgentExecution execution,
            AgentKernelSnapshot kernelSnapshot,
            AgentScopeRuntimeContextRequest runtimeRequest) {
        if (deterministicRecovery == null) {
            return terminalCompleted(execution);
        }
        return deterministicRecovery.recover(
                        kernelSnapshot.payload().agentDefinitionStableKey(),
                        runtimeRequest.project())
                .then(terminalCompleted(execution))
                .onErrorResume(failure -> terminalFailure(
                        execution,
                        AgentRuntimeErrorCode.AGENTSCOPE_INTERNAL_ERROR,
                        "结果落库失败：" + safeMessage(failure)));
    }

    private String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    /**
     * A provider finishing its text response is not the same thing as a
     * pipeline finishing its business work. Keep this check at the durable
     * terminal boundary so refresh/reconnect cannot turn a no-op into a
     * successful historical task.
     */
    private String validateBusinessCompletion(
            String agentDefinitionStableKey,
            Set<String> successfulBusinessTools) {
        Set<String> required = requiredBusinessTools(agentDefinitionStableKey);
        if (required.isEmpty()) {
            return null;
        }
        // 这两个总流程有确定性的原文兜底：模型即使不会调用工具，前端也会
        // 在 DONE 后按数据库中的剧本原文补齐可编辑结构，再进行结果校验。
        // 不能在这里提前终止，否则兜底回调没有机会执行。
        if (hasDeterministicFallback(agentDefinitionStableKey)) {
            return null;
        }
        if (required.stream().noneMatch(successfulBusinessTools::contains)) {
            return "模型已结束输出，但没有完成必须的写入操作（需要调用："
                    + String.join(" 或 ", required)
                    + "）。请更换支持工具调用的模型后重试。";
        }
        return null;
    }

    private boolean hasDeterministicFallback(String agentName) {
        return "script_full_parse".equals(agentName)
                || "script_to_storyboard".equals(agentName);
    }

    private Set<String> requiredBusinessTools(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return Set.of();
        }
        return switch (agentName) {
            case "script_full_parse", "story_to_script" ->
                    Set.of("save_script_episode", "save_script_scene_items");
            case "script_episode_parse", "episode_scene_writer" ->
                    Set.of("save_script_scene_items");
            case "script_to_storyboard" ->
                    Set.of("save_storyboard_episode", "save_storyboard_scene_shots");
            case "episode_storyboard_writer" ->
                    Set.of("save_storyboard_scene_shots");
            case "asset_image_gen", "asset_image_executor" ->
                    Set.of("update_asset_image");
            case "storyboard_frame_gen", "storyboard_frame_executor" ->
                    Set.of("update_storyboard_item_frame");
            case "storyboard_video_gen", "storyboard_video_executor" ->
                    Set.of("update_storyboard_item_video");
            default -> Set.of();
        };
    }

    private Mono<Void> enterWaitingConfirmation(
            AgentExecution execution,
            AgentKernelSnapshot kernelSnapshot,
            long lastCommittedSequence) {
        if (lastCommittedSequence < 0) {
            return terminalFailure(
                    execution,
                    AgentRuntimeErrorCode.AGENTSCOPE_INTERNAL_ERROR,
                    "Agent confirmation pause has no durable checkpoint");
        }
        WaitingCheckpoint checkpoint = new WaitingCheckpoint(
                execution.stateSessionId(),
                kernelSnapshot.fingerprint(),
                kernelSnapshot.snapshotJson(),
                lastCommittedSequence);
        return waitingState.enterWaitingConfirmation(
                        execution.runId(), execution.ownerEpoch(), checkpoint)
                .then()
                .onErrorResume(failure -> terminalFailure(
                        execution,
                        AgentRuntimeErrorCode.EVENT_PERSIST_FAILED,
                        failure.getMessage()));
    }

    @Override
    public Mono<Boolean> interruptOwned(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            ExecutionStopReason reason) {
        return executions.interruptOwned(
                runId, ownerInstanceId, ownerEpoch, reason);
    }

    @Override
    public Mono<Void> shutdown(Duration drainTimeout) {
        Objects.requireNonNull(drainTimeout, "drainTimeout must not be null");
        if (drainTimeout.isZero() || drainTimeout.isNegative()) {
            return Mono.error(new IllegalArgumentException(
                    "drainTimeout must be greater than zero"));
        }
        Mono<Void> existing = shutdownSignal.get();
        if (existing != null) {
            return existing;
        }
        Mono<Void> candidate = Mono.defer(() -> {
                    accepting.set(false);
                    return executions.awaitEmpty(drainTimeout)
                            .onErrorResume(TimeoutException.class, ignored ->
                                    Flux.fromIterable(executions.snapshot())
                                            .concatMap(handle -> shutdownCancellation
                                                    .request(handle.runId())
                                                    .then(handle.interrupt(
                                                            ExecutionStopReason.SHUTDOWN))
                                                    .onErrorResume(failure -> Mono.empty()))
                                            .then())
                            .then(kernelLeaseCache.drainAndClose(drainTimeout));
                })
                .cache();
        return shutdownSignal.compareAndSet(null, candidate)
                ? candidate
                : shutdownSignal.get();
    }

    private Mono<Void> terminalCompleted(AgentExecution execution) {
        return terminals.terminateOwned(
                        terminalRequest(
                                execution.runId(),
                                execution.userId(),
                                execution.stateSessionId(),
                                AgentRunStatus.COMPLETED,
                                AgentTerminalOutputType.DONE,
                                null,
                                null,
                                execution.parentToolCallId(),
                                execution.agentName()),
                        execution.ownerInstanceId(),
                        execution.ownerEpoch())
                .flatMap(this::projectTerminal);
    }

    private Mono<Void> terminalFailure(
            AgentExecution execution,
            AgentRuntimeErrorCode errorCode,
            String errorMessage) {
        return terminalFailure(
                execution.runId(),
                execution.ownerInstanceId(),
                execution.ownerEpoch(),
                execution.userId(),
                execution.stateSessionId(),
                execution.parentToolCallId(),
                execution.agentName(),
                errorCode,
                errorMessage);
    }

    private Mono<Void> terminalFailure(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            AgentScopeRuntimeContextRequest runtimeRequest,
            String stateSessionId,
            AgentRuntimeErrorCode errorCode,
            String errorMessage) {
        return terminalFailure(
                runId,
                ownerInstanceId,
                ownerEpoch,
                runtimeRequest.authenticatedUser().userId(),
                stateSessionId,
                runtimeRequest.run().parentToolCallId(),
                runtimeRequest.run().agentName(),
                errorCode,
                errorMessage);
    }

    private Mono<Void> terminalFailure(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            long userId,
            String stateSessionId,
            String parentToolCallId,
            String agentName,
            AgentRuntimeErrorCode errorCode,
            String errorMessage) {
        return terminals.terminateOwned(
                        terminalRequest(
                                runId,
                                userId,
                                stateSessionId,
                                AgentRunStatus.FAILED,
                                AgentTerminalOutputType.ERROR,
                                errorCode,
                                sanitizeMessage(errorMessage),
                                parentToolCallId,
                                agentName),
                        ownerInstanceId,
                        ownerEpoch)
                .flatMap(this::projectTerminal);
    }

    private RunTerminalRequest terminalRequest(
            String runId,
            long userId,
            String stateSessionId,
            AgentRunStatus status,
            AgentTerminalOutputType outputType,
            AgentRuntimeErrorCode errorCode,
            String errorMessage,
            String parentToolCallId,
            String agentName) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode()
                .put("outputType", outputType.name())
                .put("finished", true);
        if (errorCode != null) {
            payload.put("errorCode", errorCode.name());
            payload.put("error", errorMessage);
        }
        AgentEventEnvelope envelope = new AgentEventEnvelope(
                "terminal-" + UUID.randomUUID().toString().replace("-", ""),
                "RUN_TERMINAL",
                agentName == null ? "main" : "main/" + agentName,
                null,
                null,
                null,
                parentToolCallId,
                agentName,
                outputType.name(),
                sanitizer.sanitize(payload),
                Instant.now());
        return new RunTerminalRequest(
                runId,
                new StateStoreSlot(String.valueOf(userId), stateSessionId),
                Set.of(AgentRunStatus.RUNNING),
                status,
                outputType,
                errorCode,
                errorMessage,
                envelope);
    }

    private Mono<Void> projectTerminal(
            Optional<CommittedAgentEvent> committed) {
        return committed
                .map(event -> projections.projectThrough(
                        event.runId(), event.sequence()))
                .orElseGet(Mono::empty);
    }

    private AgentRuntimeErrorCode classifyStartFailure(Throwable failure) {
        return failure instanceof RunConfigUnavailableException
                ? AgentRuntimeErrorCode.RUN_CONFIG_UNAVAILABLE
                : AgentRuntimeErrorCode.AGENTSCOPE_INTERNAL_ERROR;
    }

    private String sanitizeMessage(String message) {
        String value = message == null || message.isBlank()
                ? "Agent execution failed"
                : message;
        JsonNode sanitized = sanitizer.sanitize(
                JsonNodeFactory.instance.objectNode().put("message", value));
        String safe = sanitized.path("message").asText("Agent execution failed");
        return safe.length() <= 1024 ? safe : safe.substring(0, 1024);
    }

    private void requireStartSnapshot(StartAgentExecutionCommand command) {
        if (!sameSnapshot(command.run().kernelSnapshot(), command.kernelSnapshot())) {
            throw new IllegalArgumentException(
                    "Start execution snapshot does not match the persisted run snapshot");
        }
        requireSpecMatchesSnapshot(command.kernelSpec(), command.kernelSnapshot());
    }

    private void requireResumeSnapshot(ResumeAgentExecutionCommand command) {
        if (!Objects.equals(command.run().kernelFingerprint(),
                    command.kernelSnapshot().fingerprint())
                || !Objects.equals(command.run().agentDefinitionSnapshotJson(),
                    command.kernelSnapshot().snapshotJson())) {
            throw new IllegalArgumentException(
                    "Resume execution snapshot does not match the persisted run snapshot");
        }
    }

    private boolean sameSnapshot(AgentKernelSnapshot left, AgentKernelSnapshot right) {
        return Objects.equals(left.fingerprint(), right.fingerprint())
                && Objects.equals(left.snapshotJson(), right.snapshotJson());
    }

    private void requireSpecMatchesSnapshot(
            AgentKernelSpec spec, AgentKernelSnapshot snapshot) {
        AgentKernelSnapshot rebuilt = snapshotBuilder.build(spec);
        if (!sameSnapshot(rebuilt, snapshot)) {
            throw new IllegalArgumentException(
                    "Live Agent kernel does not match the persisted immutable snapshot");
        }
    }

    private void requireRuntimeIdentity(
            String runId,
            String ownerInstanceId,
            long ownerEpoch,
            String stateSessionId,
            Instant deadline,
            AgentScopeRuntimeContextRequest runtimeRequest) {
        AgentRunContext run = runtimeRequest.run();
        if (!Objects.equals(runId, run.runId())
                || !Objects.equals(ownerInstanceId, run.ownerInstanceId())
                || ownerEpoch != run.ownerEpoch()
                || !Objects.equals(
                        stateSessionId,
                        runtimeRequest.conversation().agentStateSessionId())
                || !Objects.equals(deadline, run.deadline())) {
            throw new IllegalArgumentException(
                    "RuntimeContext run identity does not match the execution command");
        }
    }

    private void requireAccepting() {
        if (!accepting.get()) {
            throw new IllegalStateException("Agent runtime is shutting down");
        }
    }

    static final class OwnerLostException extends RuntimeException {
        private OwnerLostException(String runId) {
            super("Agent run ownership was lost: " + runId);
        }
    }

    static final class ParentOwnerLostException extends RuntimeException {
        private ParentOwnerLostException(String runId) {
            super("Parent Agent run ownership was lost: " + runId);
        }
    }
}

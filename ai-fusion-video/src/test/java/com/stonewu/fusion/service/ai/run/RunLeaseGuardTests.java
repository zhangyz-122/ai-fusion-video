package com.stonewu.fusion.service.ai.run;

import com.stonewu.fusion.config.AgentScopeRuntimeProperties;
import com.stonewu.fusion.entity.ai.AgentRun;
import com.stonewu.fusion.enums.ai.AgentRunStatus;
import com.stonewu.fusion.repository.ai.AgentRunRepository;
import com.stonewu.fusion.service.ai.agentscope.runtime.AgentRuntimeSchedulers;
import com.stonewu.fusion.service.ai.run.model.ExecutionStopReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 租约续期与过期判定：续期成功放行、过期按运行状态分流到中断或取消确认。 */
class RunLeaseGuardTests {

    private static final Duration OWNER_LEASE = Duration.ofSeconds(30);

    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final OwnedExecutionRegistry executions = mock(OwnedExecutionRegistry.class);
    private final OwnedCancellationHandler ownedCancellations =
            mock(OwnedCancellationHandler.class);
    private final AgentRuntimeSchedulers schedulers =
            new AgentRuntimeSchedulers(new AgentScopeRuntimeProperties());
    private final RunLeaseGuard guard =
            new RunLeaseGuard(runs, executions, ownedCancellations, schedulers);

    @AfterEach
    void tearDown() {
        schedulers.close();
    }

    @Test
    void successfulRenewalKeepsExecutionOwned() {
        when(runs.renewOwnedLease("run-1", "node-a", 1L, OWNER_LEASE)).thenReturn(true);

        StepVerifier.create(guard.heartbeat("run-1", "node-a", 1L, OWNER_LEASE))
                .verifyComplete();

        verifyNoInteractions(executions, ownedCancellations);
    }

    @Test
    void expiredLeaseOnRunningRunFencesOwnerAndInterruptsExecution() {
        when(runs.renewOwnedLease("run-1", "node-a", 1L, OWNER_LEASE)).thenReturn(false);
        when(runs.findRun("run-1")).thenReturn(run(AgentRunStatus.RUNNING));
        when(executions.interruptOwned(
                        "run-1", "node-a", 1L, ExecutionStopReason.OWNER_FENCED))
                .thenReturn(Mono.just(true));

        StepVerifier.create(guard.heartbeat("run-1", "node-a", 1L, OWNER_LEASE))
                .verifyComplete();

        // RUNNING 且租约过期：按所有权失守中断本地执行，终态由对账流程收敛
        verify(executions).interruptOwned(
                "run-1", "node-a", 1L, ExecutionStopReason.OWNER_FENCED);
        verifyNoInteractions(ownedCancellations);
    }

    @Test
    void expiredLeaseOnCancelRequestedRunAcknowledgesCancellationInsteadOfFencing() {
        when(runs.renewOwnedLease("run-1", "node-a", 1L, OWNER_LEASE)).thenReturn(false);
        when(runs.findRun("run-1")).thenReturn(run(AgentRunStatus.CANCEL_REQUESTED));
        when(ownedCancellations.acknowledgeAndFinalize("run-1", "node-a", 1L))
                .thenReturn(Mono.just(true));

        StepVerifier.create(guard.heartbeat("run-1", "node-a", 1L, OWNER_LEASE))
                .verifyComplete();

        // 取消请求已发出且 owner 未变：视为取消确认，不再中断执行
        verify(ownedCancellations).acknowledgeAndFinalize("run-1", "node-a", 1L);
        verify(executions, never())
                .interruptOwned(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void heartbeatDatabaseFailureFencesExecutionAndPropagatesError() {
        when(runs.renewOwnedLease(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(executions.interruptOwned(
                        eq("run-1"), eq("node-a"), eq(1L),
                        eq(ExecutionStopReason.OWNER_FENCED)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(guard.heartbeat("run-1", "node-a", 1L, OWNER_LEASE))
                .expectError(IllegalStateException.class)
                .verify();

        // 无法确认租约时按 fail-safe 处理：中断执行并向上传播失败
        verify(executions).interruptOwned(
                "run-1", "node-a", 1L, ExecutionStopReason.OWNER_FENCED);
    }

    @Test
    void assertLeaseRejectsRunWithoutValidOwnedLease() {
        when(runs.hasValidOwnedLease("run-1", "node-a", 1L)).thenReturn(false);

        StepVerifier.create(guard.assertLease("run-1", "node-a", 1L))
                .expectError(RunLeaseGuard.OwnerFencedException.class)
                .verify();
    }

    private AgentRun run(AgentRunStatus status) {
        return AgentRun.builder()
                .runId("run-1")
                .status(status.name())
                .ownerInstanceId("node-a")
                .ownerEpoch(1L)
                .deadlineAt(LocalDateTime.now(ZoneOffset.UTC).plus(Duration.ofMinutes(30)))
                .build();
    }
}

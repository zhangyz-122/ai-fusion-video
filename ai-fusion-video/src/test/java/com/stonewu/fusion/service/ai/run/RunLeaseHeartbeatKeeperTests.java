package com.stonewu.fusion.service.ai.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.config.AgentScopeV2Properties;
import com.stonewu.fusion.service.ai.run.model.AgentEventEnvelope;
import com.stonewu.fusion.service.ai.run.model.ExecutionStopReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunLeaseHeartbeatKeeperTests {

    private static final Duration TEST_OWNER_LEASE = Duration.ofMillis(60);

    private final ScheduledThreadPoolExecutor executor =
            RunLeaseHeartbeatKeeper.heartbeatExecutor();
    private final RunLeaseGuard leases = mock(RunLeaseGuard.class);
    private final RunLeaseHeartbeatKeeper keeper =
            new RunLeaseHeartbeatKeeper(leases, TEST_OWNER_LEASE, executor);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void heartbeatIntervalIsOneThirdOfConfiguredOwnerLease() {
        RunLeaseHeartbeatKeeper productionKeeper =
                new RunLeaseHeartbeatKeeper(leases, new AgentScopeV2Properties());

        assertThat(productionKeeper.heartbeatInterval()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void activeExecutionRenewsLeasePeriodicallyDuringLongBlockingCalls() {
        when(leases.heartbeat(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenReturn(Mono.empty());

        keeper.start("run-1", "node-a", 1L);

        // 活跃执行期间以 TTL/3 间隔持续续期，不依赖任何外部调度线程
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(leases, atLeast(3))
                        .heartbeat("run-1", "node-a", 1L, TEST_OWNER_LEASE));
        keeper.stop("run-1");
    }

    @Test
    void handleRemovalAfterRunFinishesStopsLeaseRenewal() {
        AtomicInteger renewals = new AtomicInteger();
        when(leases.heartbeat(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    renewals.incrementAndGet();
                    return Mono.empty();
                });
        OwnedExecutionRegistry registry = new OwnedExecutionRegistry(
                new ObjectMapper(), 64, 1024 * 1024,
                Schedulers.parallel(), Clock.systemUTC());
        registry.setLeaseHeartbeats(keeper);
        registry.registerAndLaunch(
                        new AgentExecution(
                                "run-1",
                                "node-a",
                                1L,
                                7L,
                                "session-1",
                                Flux.<AgentEventEnvelope>never(),
                                reason -> Mono.empty(),
                                () -> { }),
                        Instant.now().plus(Duration.ofSeconds(30)),
                        flux -> flux,
                        events -> events.then(),
                        outcome -> Mono.empty())
                .block(Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(2)).until(() -> renewals.get() >= 2);

        registry.interruptOwned(
                        "run-1", "node-a", 1L, ExecutionStopReason.SHUTDOWN)
                .block(Duration.ofSeconds(1));
        await().atMost(Duration.ofSeconds(2)).until(() -> registry.size() == 0);

        // run 结束移除句柄后停止续期，租约在 TTL 后自然过期、可被其他实例接管
        int stoppedAt = renewals.get();
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .until(() -> renewals.get() == stoppedAt);
        assertThat(keeper.activeHeartbeats()).isZero();
    }

    @Test
    void lostLeaseStopsHeartbeatAfterFencingInsteadOfRetrying() {
        when(leases.heartbeat(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("lease lost")));

        keeper.start("run-1", "node-a", 1L);

        // 首次续期失败说明租约已丢失（RunLeaseGuard 已按 OWNER_FENCED 中断执行），
        // 心跳必须停止，不允许对已失控的 run 继续写库
        await().atMost(Duration.ofSeconds(2)).until(() -> keeper.activeHeartbeats() == 0);
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .until(() -> keeper.activeHeartbeats() == 0);
    }

    @Test
    void closeCancelsAllHeartbeatsAndIgnoresLaterStarts() {
        when(leases.heartbeat(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenReturn(Mono.empty());
        keeper.start("run-1", "node-a", 1L);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(leases, atLeastOnce())
                        .heartbeat("run-1", "node-a", 1L, TEST_OWNER_LEASE));

        keeper.close();

        assertThat(keeper.activeHeartbeats()).isZero();
        // 进程退出路径：关闭后不再接受新的心跳注册，也不抛出异常
        keeper.start("run-2", "node-a", 1L);
        assertThat(keeper.activeHeartbeats()).isZero();
    }

    @Test
    void startToleratesShutdownRaceBetweenCheckAndScheduling() {
        // 模拟 isShutdown 检查通过之后、调度执行之前线程池被 close() 关闭的停机竞态
        ScheduledThreadPoolExecutor racingExecutor =
                new ScheduledThreadPoolExecutor(1, RunLeaseHeartbeatKeeperTests::daemonThread) {
                    @Override
                    public ScheduledFuture<?> scheduleAtFixedRate(
                            Runnable task, long initialDelay, long period, TimeUnit unit) {
                        throw new RejectedExecutionException("executor shut down");
                    }
                };
        RunLeaseHeartbeatKeeper racingKeeper =
                new RunLeaseHeartbeatKeeper(leases, TEST_OWNER_LEASE, racingExecutor);

        // 停机竞态下注册静默放弃，不向调用方抛出异常
        racingKeeper.start("run-9", "node-a", 1L);

        assertThat(racingKeeper.activeHeartbeats()).isZero();
        racingExecutor.shutdownNow();
    }

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "racing-heartbeat");
        thread.setDaemon(true);
        return thread;
    }

    @Test
    void noopKeeperToleratesLifecycleCallsWithoutExecutor() {
        RunLeaseHeartbeatKeeper noop = RunLeaseHeartbeatKeeper.noop();

        noop.start("run-1", "node-a", 1L);
        noop.stop("run-1");
        noop.close();

        assertThat(noop.activeHeartbeats()).isZero();
    }
}

package com.stonewu.fusion.service.ai.run;

import com.stonewu.fusion.config.AgentScopeV2Properties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Agent run 活跃执行期间的数据库租约心跳。
 *
 * <p>租约 TTL（{@code fusion.agentscope.v2.execution.owner-lease}，默认 30 秒）远小于
 * 一次长阻塞模型调用的耗时，而历史实现只依赖共享的 Spring @Scheduled 维护线程续期，
 * 一旦该线程被其他定时任务阻塞超过 TTL，正在正常执行的 run 就会被对账流程以
 * 「Agent run owner lease expired」误杀。本组件为每个本地持有的执行注册独立的
 * TTL/3 固定间隔续期任务：执行注册即开始续期，句柄移除（完成、失败、中断、停机）
 * 即停止；心跳线程为守护线程，进程真正崩溃后不再续期，其他实例仍能在 TTL 后接管，
 * 故障转移语义与租约判定（{@link RunLeaseGuard}）保持不变。
 */
@Component
@Slf4j
public final class RunLeaseHeartbeatKeeper {

    private final Supplier<RunLeaseGuard> leases;
    private final Duration ownerLease;
    private final Duration heartbeatInterval;
    private final ScheduledThreadPoolExecutor executor;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> heartbeats =
            new ConcurrentHashMap<>();

    /**
     * 通过 {@link ObjectProvider} 延迟解析 {@link RunLeaseGuard}：RunLeaseGuard 构造期
     * 依赖 OwnedExecutionRegistry，注册表又在属性注入阶段依赖本组件，直接构造器注入会
     * 形成 guard → registry → keeper → guard 的创建环；首次心跳发生在全部单例就绪之后，
     * 延迟解析不影响运行时语义。
     */
    @Autowired
    RunLeaseHeartbeatKeeper(
            ObjectProvider<RunLeaseGuard> leases, AgentScopeV2Properties properties) {
        this(leases::getObject, ownerLease(properties),
                heartbeatInterval(ownerLease(properties)), heartbeatExecutor());
    }

    RunLeaseHeartbeatKeeper(RunLeaseGuard leases, AgentScopeV2Properties properties) {
        this(leases, ownerLease(properties), heartbeatExecutor());
    }

    RunLeaseHeartbeatKeeper(
            RunLeaseGuard leases,
            Duration ownerLease,
            ScheduledThreadPoolExecutor executor) {
        this(leases == null ? null : () -> leases,
                ownerLease, heartbeatInterval(ownerLease), executor);
    }

    private RunLeaseHeartbeatKeeper(
            Supplier<RunLeaseGuard> leases,
            Duration ownerLease,
            Duration heartbeatInterval,
            ScheduledThreadPoolExecutor executor) {
        this.leases = leases;
        this.ownerLease = Objects.requireNonNull(ownerLease, "ownerLease must not be null");
        this.heartbeatInterval = Objects.requireNonNull(
                heartbeatInterval, "heartbeatInterval must not be null");
        this.executor = executor;
    }

    /** 注册表默认持有的空实现，未注入真实心跳组件时不做任何事。 */
    static RunLeaseHeartbeatKeeper noop() {
        return new RunLeaseHeartbeatKeeper(null, Duration.ZERO, Duration.ZERO, null);
    }

    /** 执行被本地注册持有后开始按 TTL/3 间隔续期。 */
    void start(String runId, String ownerInstanceId, long ownerEpoch) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        ScheduledFuture<?> future;
        try {
            future = executor.scheduleAtFixedRate(
                    renewalTask(runId, ownerInstanceId, ownerEpoch),
                    heartbeatInterval.toMillis(),
                    heartbeatInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rejectedAfterShutdown) {
            // 停机竞态：isShutdown 检查与调度之间线程池可能刚好被 close() 关闭，
            // 此时进程正在退出、心跳已无意义，静默放弃本次注册（close 会统一清理）。
            log.debug("Agent run lease heartbeat skipped after shutdown: runId={}", runId);
            return;
        }
        ScheduledFuture<?> previous = heartbeats.put(runId, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /** 执行句柄移除（完成、失败、中断、停机）后停止续期，租约到期后可被其他实例接管。 */
    void stop(String runId) {
        ScheduledFuture<?> future = heartbeats.remove(runId);
        if (future != null) {
            future.cancel(false);
        }
    }

    int activeHeartbeats() {
        return heartbeats.size();
    }

    Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    private Runnable renewalTask(String runId, String ownerInstanceId, long ownerEpoch) {
        return () -> {
            try {
                renewLease(runId, ownerInstanceId, ownerEpoch);
            } catch (Throwable failure) {
                // 任务内异常会让固定周期任务被静默取消，这里显式记录并停止续期。
                log.warn("Agent run lease heartbeat failed: runId={}, type={}",
                        runId, failure.getClass().getSimpleName());
                stop(runId);
            }
        };
    }

    private void renewLease(String runId, String ownerInstanceId, long ownerEpoch) {
        Objects.requireNonNull(leases, "leases must not be null");
        // 此刻全部单例已就绪，延迟解析不会触发创建环；数据库访问由 RunLeaseGuard
        // 调度到 journal 线程，心跳线程只负责触发订阅。
        leases.get()
                .heartbeat(runId, ownerInstanceId, ownerEpoch, ownerLease)
                .doOnError(failure -> {
                    // 续期失败说明租约已丢失或数据库不可达，RunLeaseGuard 已按
                    // OWNER_FENCED 中断本地执行；停止该 run 的心跳，终态交由对账收敛。
                    log.warn("Agent run lease heartbeat stopped: runId={}, type={}",
                            runId, failure.getClass().getSimpleName());
                    stop(runId);
                })
                .onErrorResume(failure -> Mono.empty())
                .subscribe();
    }

    @PreDestroy
    void close() {
        if (executor == null) {
            return;
        }
        heartbeats.values().forEach(future -> future.cancel(false));
        heartbeats.clear();
        executor.shutdownNow();
    }

    static ScheduledThreadPoolExecutor heartbeatExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "agent-lease-heartbeat-"
                    + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Duration ownerLease(AgentScopeV2Properties properties) {
        return Objects.requireNonNull(properties, "properties must not be null")
                .getExecution()
                .getOwnerLease();
    }

    private static Duration heartbeatInterval(Duration ownerLease) {
        Objects.requireNonNull(ownerLease, "ownerLease must not be null");
        if (ownerLease.isZero() || ownerLease.isNegative()) {
            throw new IllegalArgumentException("ownerLease must be greater than zero");
        }
        Duration interval = ownerLease.dividedBy(3);
        return interval.isZero() ? Duration.ofMillis(1) : interval;
    }
}

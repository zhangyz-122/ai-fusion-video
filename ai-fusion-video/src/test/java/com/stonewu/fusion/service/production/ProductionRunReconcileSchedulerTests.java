package com.stonewu.fusion.service.production;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRunReconcileSchedulerTests {

    @Mock
    private ProductionRunMapper runMapper;

    @Mock
    private ProductionStepMapper stepMapper;

    @Mock
    private VideoTaskMapper videoTaskMapper;

    @Mock
    private ProductionRunService productionRunService;

    @BeforeAll
    static void initLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProductionRun.class);
        TableInfoHelper.initTableInfo(assistant, ProductionStep.class);
        TableInfoHelper.initTableInfo(assistant, VideoTask.class);
    }

    @Test
    void reconcilesStaleWaitingRunWhenVideoTaskReachedTerminalState() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(generateStep(201L, 1L, 501L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).status(2).build()));

        scheduler().reconcileStaleWaitingRuns();

        verify(productionRunService).reconcile(1L, 99L);
    }

    @Test
    void reconcilesStaleWaitingRunWhenVideoTaskFailed() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(generateStep(201L, 1L, 501L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).status(3).build()));

        scheduler().reconcileStaleWaitingRuns();

        verify(productionRunService).reconcile(1L, 99L);
    }

    @Test
    void skipsRunWhoseVideoTaskIsStillExecuting() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(generateStep(201L, 1L, 501L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).status(1).build()));

        scheduler().reconcileStaleWaitingRuns();

        verifyNoInteractions(productionRunService);
    }

    @Test
    void skipsRunWithoutGenerateVideoStep() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of());

        scheduler().reconcileStaleWaitingRuns();

        verifyNoInteractions(videoTaskMapper, productionRunService);
    }

    @Test
    void skipsRunWhenVideoTaskRowIsMissing() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(generateStep(201L, 1L, 501L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of());

        scheduler().reconcileStaleWaitingRuns();

        verifyNoInteractions(productionRunService);
    }

    @Test
    void reconcileFailureDoesNotBlockRemainingStaleRuns() {
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(1L, 99L), waitingRun(2L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(
                generateStep(201L, 1L, 501L),
                generateStep(202L, 2L, 502L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).status(2).build(),
                VideoTask.builder().id(502L).status(2).build()));
        when(productionRunService.reconcile(1L, 99L)).thenThrow(new RuntimeException("boom"));

        scheduler().reconcileStaleWaitingRuns();

        verify(productionRunService).reconcile(1L, 99L);
        verify(productionRunService).reconcile(2L, 99L);
    }

    @Test
    void doesNothingWhenNoStaleWaitingRunExists() {
        when(runMapper.selectList(any())).thenReturn(List.of());

        scheduler().reconcileStaleWaitingRuns();

        verifyNoInteractions(stepMapper, videoTaskMapper, productionRunService);
    }

    @Test
    void toleratesVideoTaskRowWithNullStatusAndStillSyncsTerminalRunsInSameBatch() {
        // 线上故障形态：同一批滞留运行里混入一条 status 为 NULL 的任务行，
        // 旧逻辑 Collectors.toMap 遇 null value 抛无消息 NPE，整批扫描崩溃，
        // 导致任务已 failed 的运行（如 run 14 / task 36）永远无法自动同步。
        when(runMapper.selectList(any())).thenReturn(List.of(waitingRun(14L, 99L), waitingRun(15L, 99L)));
        when(stepMapper.selectList(any())).thenReturn(List.of(
                generateStep(201L, 14L, 36L),
                generateStep(202L, 15L, 37L)));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(37L).status(null).build(),
                VideoTask.builder().id(36L).status(3).build()));

        scheduler().reconcileStaleWaitingRuns();

        // 任务已失败的 run 14 正常进入 reconcile（task status=3 终态），可被标记 FAILED 并 repair
        verify(productionRunService).reconcile(14L, 99L);
        // 未知状态的任务按非终态跳过，不触发同步也不抛异常
        verify(productionRunService, never()).reconcile(eq(15L), eq(99L));
    }

    @Test
    void scanFailureIsContainedAndLoggedWithFullStackTrace() {
        Logger schedulerLogger = (Logger) LoggerFactory.getLogger(ProductionRunReconcileScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        schedulerLogger.addAppender(appender);
        try {
            // 无消息 NPE（线上日志只剩空 error= 的形态）
            when(runMapper.selectList(any())).thenThrow(new NullPointerException());

            assertThatCode(() -> scheduler().reconcileStaleWaitingRuns())
                    .doesNotThrowAnyException();
        } finally {
            schedulerLogger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("滞留运行扫描失败");
            // 修复要求：扫描失败日志必须携带完整堆栈（异常对象），无消息 NPE 也可定位
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(NullPointerException.class.getName());
        });
    }

    private ProductionRunReconcileScheduler scheduler() {
        return new ProductionRunReconcileScheduler(runMapper, stepMapper, videoTaskMapper, productionRunService);
    }

    private ProductionRun waitingRun(Long id, Long userId) {
        ProductionRun run = ProductionRun.builder()
                .id(id)
                .userId(userId)
                .storyboardItemId(11L)
                .status(ProductionRunService.RUN_WAITING_GENERATION)
                .build();
        run.setUpdateTime(LocalDateTime.now().minusMinutes(ProductionRunReconcileScheduler.STALE_MINUTES + 1));
        return run;
    }

    private ProductionStep generateStep(Long id, Long runId, Long videoTaskId) {
        return ProductionStep.builder()
                .id(id)
                .runId(runId)
                .stepType(ProductionRunService.STEP_GENERATE_VIDEO)
                .videoTaskId(videoTaskId)
                .build();
    }
}

package com.stonewu.fusion.service.production;

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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

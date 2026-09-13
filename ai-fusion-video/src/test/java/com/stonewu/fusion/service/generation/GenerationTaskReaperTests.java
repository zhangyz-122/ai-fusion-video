package com.stonewu.fusion.service.generation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.generation.ImageTaskMapper;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionStepMapper;
import com.stonewu.fusion.service.production.ProductionRepairRouter;
import com.stonewu.fusion.service.production.ProductionRunService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationTaskReaperTests {

    private static final String TIMEOUT_MESSAGE = "任务滞留超过 2 小时，已自动标记失败";

    @Mock
    private ImageTaskMapper imageTaskMapper;

    @Mock
    private VideoTaskMapper videoTaskMapper;

    @Mock
    private ProductionRunMapper productionRunMapper;

    @Mock
    private ProductionStepMapper productionStepMapper;

    @Mock
    private ProductionRepairRouter repairRouter;

    @BeforeAll
    static void initLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ImageTask.class);
        TableInfoHelper.initTableInfo(assistant, VideoTask.class);
        TableInfoHelper.initTableInfo(assistant, ProductionRun.class);
        TableInfoHelper.initTableInfo(assistant, ProductionStep.class);
    }

    @Test
    void marksWaitingProductionRunFailedWhenItsVideoTaskIsReaped() {
        when(imageTaskMapper.selectList(any())).thenReturn(List.of());
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).build()));
        ProductionStep step = ProductionStep.builder()
                .id(401L).runId(301L)
                .stepType(ProductionRunService.STEP_GENERATE_VIDEO)
                .videoTaskId(501L)
                .build();
        when(productionStepMapper.selectList(any())).thenReturn(List.of(step));
        ProductionRun run = ProductionRun.builder()
                .id(301L).userId(99L)
                .status(ProductionRunService.RUN_WAITING_GENERATION)
                .build();
        when(productionRunMapper.selectList(any())).thenReturn(List.of(run));

        reaper().reapStaleTasks();

        verify(repairRouter).recordFailure(same(run), same(step),
                eq("VIDEO_TASK_TIMEOUT"), eq(TIMEOUT_MESSAGE));

        ArgumentCaptor<LambdaUpdateWrapper<ProductionStep>> stepCaptor = wrapperCaptor();
        verify(productionStepMapper).update(isNull(), stepCaptor.capture());
        assertThat(stepCaptor.getValue().getSqlSet())
                .contains("status", "error_code", "error_message");
        assertThat(stepCaptor.getValue().getParamNameValuePairs().values())
                .contains(ProductionRunService.STEP_FAILED, "VIDEO_TASK_TIMEOUT", TIMEOUT_MESSAGE);

        ArgumentCaptor<LambdaUpdateWrapper<ProductionRun>> runCaptor = wrapperCaptor();
        verify(productionRunMapper).update(isNull(), runCaptor.capture());
        assertThat(runCaptor.getValue().getSqlSet())
                .contains("status", "failure_code", "failure_message");
        assertThat(runCaptor.getValue().getParamNameValuePairs().values())
                .contains(ProductionRunService.RUN_FAILED, "VIDEO_TASK_TIMEOUT", TIMEOUT_MESSAGE);
    }

    @Test
    void doesNotTouchProductionRunWhenNoStepReferencesTheReapedTask() {
        when(imageTaskMapper.selectList(any())).thenReturn(List.of());
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).build()));
        when(productionStepMapper.selectList(any())).thenReturn(List.of());

        reaper().reapStaleTasks();

        verifyNoInteractions(productionRunMapper, repairRouter);
        verify(productionStepMapper, never()).update(any(), any());
    }

    @Test
    void doesNotTouchRunThatAlreadyLeftWaitingGeneration() {
        when(imageTaskMapper.selectList(any())).thenReturn(List.of());
        when(videoTaskMapper.selectList(any())).thenReturn(List.of(
                VideoTask.builder().id(501L).build()));
        when(productionStepMapper.selectList(any())).thenReturn(List.of(
                ProductionStep.builder().id(401L).runId(301L)
                        .stepType(ProductionRunService.STEP_GENERATE_VIDEO)
                        .videoTaskId(501L).build()));
        when(productionRunMapper.selectList(any())).thenReturn(List.of());

        reaper().reapStaleTasks();

        verifyNoInteractions(repairRouter);
        verify(productionStepMapper, never()).update(any(), any());
        verify(productionRunMapper, never()).update(any(), any());
    }

    @Test
    void imageTaskReapingDoesNotTouchProductionTables() {
        when(imageTaskMapper.selectList(any())).thenReturn(List.of(
                ImageTask.builder().id(901L).build()));
        when(videoTaskMapper.selectList(any())).thenReturn(List.of());

        reaper().reapStaleTasks();

        verify(imageTaskMapper).update(any(), any());
        verify(videoTaskMapper, never()).update(any(), any());
        verifyNoInteractions(productionRunMapper, productionStepMapper, repairRouter);
    }

    private GenerationTaskReaper reaper() {
        return new GenerationTaskReaper(imageTaskMapper, videoTaskMapper,
                productionRunMapper, productionStepMapper, repairRouter);
    }

    @SuppressWarnings("unchecked")
    private <T> ArgumentCaptor<LambdaUpdateWrapper<T>> wrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}

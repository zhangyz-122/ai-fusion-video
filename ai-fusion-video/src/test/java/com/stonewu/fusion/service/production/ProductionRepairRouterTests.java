package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.ProductionRepairAttempt;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.production.ProductionStep;
import com.stonewu.fusion.mapper.production.ProductionRepairAttemptMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRepairRouterTests {

    @Mock
    private ProductionRepairAttemptMapper attemptMapper;

    @Test
    void transientFailureGetsBoundedSameWorkflowRetry() {
        ProductionRepairRouter.Decision decision = router().decide("VIDEO_TASK_FAILED", 0, 2);

        assertThat(decision.route()).isEqualTo(ProductionRepairRouter.RETRY_SAME_WORKFLOW);
        assertThat(decision.status()).isEqualTo(ProductionRepairRouter.PLANNED);
        assertThat(decision.retryAllowed()).isTrue();
        assertThat(decision.attemptNo()).isEqualTo(1);
    }

    @Test
    void retryBudgetBlocksFurtherAutomaticRepair() {
        ProductionRepairRouter.Decision decision = router().decide("VIDEO_TASK_FAILED", 2, 2);

        assertThat(decision.status()).isEqualTo(ProductionRepairRouter.BLOCKED);
        assertThat(decision.retryAllowed()).isFalse();
        assertThat(decision.reason()).contains("预算");
    }

    @Test
    void contentFailureRoutesToManualReview() {
        ProductionRepairRouter.Decision decision = router().decide("DIMENSION_INVALID", 0, 2);

        assertThat(decision.route()).isEqualTo(ProductionRepairRouter.MANUAL_REVIEW);
        assertThat(decision.status()).isEqualTo(ProductionRepairRouter.BLOCKED);
    }

    @Test
    void recordFailureIsIdempotentAndKeepsParentStep() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(attemptMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ProductionRepairAttempt attempt = invocation.getArgument(0);
            attempt.setId(91L);
            return 1;
        }).when(attemptMapper).insert(any(ProductionRepairAttempt.class));

        ProductionRepairAttempt attempt = router().recordFailure(
                ProductionRun.builder().id(7L).build(),
                ProductionStep.builder().id(8L).build(),
                "VIDEO_TASK_FAILED", "ComfyUI 暂时不可用");

        assertThat(attempt.getId()).isEqualTo(91L);
        assertThat(attempt.getRunId()).isEqualTo(7L);
        assertThat(attempt.getSourceStepId()).isEqualTo(8L);
        assertThat(attempt.getRoute()).isEqualTo(ProductionRepairRouter.RETRY_SAME_WORKFLOW);
        assertThat(attempt.getIdempotencyKey()).isEqualTo("8:1:VIDEO_TASK_FAILED");
        verify(attemptMapper).insert(any(ProductionRepairAttempt.class));
    }

    private ProductionRepairRouter router() {
        return new ProductionRepairRouter(attemptMapper);
    }
}

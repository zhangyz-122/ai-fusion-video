package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.production.vo.ProductionStartReqVO;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShotReadinessServiceTests {

    private final ShotReadinessService service = new ShotReadinessService();

    @Test
    void readyWhenPromptFirstFrameDurationAndBiblePresent() {
        ShotReadiness readiness = service.evaluate(readyItem());

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
    }

    @Test
    void blocksMissingFirstFrameDurationAndBible() {
        StoryboardItem item = StoryboardItem.builder()
                .id(1L)
                .content("一场雨")
                .build();

        ShotReadiness readiness = service.evaluate(item);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers())
                .extracting(ShotReadiness.Blocker::code)
                .containsExactly("MISSING_FIRST_FRAME", "MISSING_DURATION", "MISSING_BIBLE");
    }

    @Test
    void blocksDurationOverFiveSeconds() {
        StoryboardItem item = readyItem();
        item.setDuration(new BigDecimal("15"));

        ShotReadiness readiness = service.evaluate(item);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers())
                .extracting(ShotReadiness.Blocker::code)
                .containsExactly("DURATION_TOO_LONG");
    }

    @Test
    void requestOverrideCanSupplyFirstFrameAndDuration() {
        StoryboardItem item = StoryboardItem.builder()
                .id(1L)
                .videoPrompt("推门")
                .characterIds("[12]")
                .build();
        ProductionStartReqVO request = new ProductionStartReqVO();
        request.setFirstFrameImageUrl("/media/first.png");
        request.setDuration(5);

        ShotReadiness readiness = service.evaluate(item, request);

        assertThat(readiness.ready()).isTrue();
    }

    @Test
    void requireReadyThrowsJoinedMessage() {
        StoryboardItem item = StoryboardItem.builder().id(1L).build();

        assertThatThrownBy(() -> service.requireReady(item, new ProductionStartReqVO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("镜头未就绪")
                .hasMessageContaining("缺少锁定首帧");
    }

    private static StoryboardItem readyItem() {
        return StoryboardItem.builder()
                .id(1L)
                .videoPrompt("林雾夏推开木门")
                .firstFrameImageUrl("/media/frames/1.png")
                .duration(new BigDecimal("5"))
                .sceneAssetItemId(8L)
                .build();
    }
}

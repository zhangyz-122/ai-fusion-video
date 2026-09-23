package com.stonewu.fusion.service.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.production.ProductionTake;
import com.stonewu.fusion.entity.production.QcResult;
import com.stonewu.fusion.entity.production.StoryEvent;
import com.stonewu.fusion.entity.production.StoryStateSnapshot;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.production.QcResultMapper;
import com.stonewu.fusion.mapper.production.StoryEventMapper;
import com.stonewu.fusion.mapper.production.StoryStateSnapshotMapper;
import com.stonewu.fusion.service.production.StoryStateService.StateDelta;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryStateServiceTests {

    @Mock
    private StoryEventMapper eventMapper;

    @Mock
    private StoryStateSnapshotMapper snapshotMapper;

    @Mock
    private ProductionTakeMapper takeMapper;

    @Mock
    private QcResultMapper qcResultMapper;

    @Mock
    private StoryboardService storyboardService;

    @Test
    void commitRequiresASelectedTake() {
        when(storyboardService.getItemById(5L)).thenReturn(item(null));
        when(storyboardService.getById(20L)).thenReturn(Storyboard.builder().id(20L).projectId(1L).build());
        StoryStateService service = service();

        assertThatThrownBy(() -> service.commitShot(5L, List.of(characterDelta()), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未选定候选");
        verify(eventMapper, never()).insert(any(StoryEvent.class));
    }

    @Test
    void commitRejectsSelectedTakeThatDidNotPassQc() {
        when(storyboardService.getItemById(5L)).thenReturn(item(41L));
        when(storyboardService.getById(20L)).thenReturn(Storyboard.builder().id(20L).projectId(1L).build());
        when(takeMapper.selectById(41L)).thenReturn(ProductionTake.builder()
                .id(41L).storyboardItemId(5L).build());
        when(qcResultMapper.selectOne(any())).thenReturn(qc(QcResult.REVIEW_REQUIRED));
        StoryStateService service = service();

        assertThatThrownBy(() -> service.commitShot(5L, List.of(characterDelta()), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未通过质检");
    }

    @Test
    void commitIsIdempotentForTheSameShotAndSubject() {
        stubCommittedShot();
        StoryEvent existing = StoryEvent.builder().id(99L).projectId(1L)
                .idempotencyKey("5:CHARACTER:林川:SET").build();
        when(eventMapper.selectOne(any())).thenReturn(existing);

        List<StoryEvent> committed = service().commitShot(5L, List.of(characterDelta()), 3L);

        assertThat(committed).containsExactly(existing);
        verify(eventMapper, never()).insert(any(StoryEvent.class));
    }

    @Test
    void commitDerivesProjectEpisodeAndIdempotencyKey() {
        stubCommittedShot();
        when(eventMapper.selectOne(any())).thenReturn(null);

        service().commitShot(5L, List.of(characterDelta()), 3L);

        ArgumentCaptor<StoryEvent> captor = ArgumentCaptor.forClass(StoryEvent.class);
        verify(eventMapper).insert(captor.capture());
        StoryEvent event = captor.getValue();
        assertThat(event.getProjectId()).isEqualTo(1L);
        assertThat(event.getEpisodeId()).isEqualTo(2L);
        assertThat(event.getStoryboardItemId()).isEqualTo(5L);
        assertThat(event.getCreatedBy()).isEqualTo(3L);
        assertThat(event.getIdempotencyKey()).isEqualTo("5:CHARACTER:林川:SET");
    }

    @Test
    void listSubjectsCannotBeOverwrittenAndScalarSubjectsCannotBeAppended() {
        stubCommittedShot();
        StoryStateService service = service();

        assertThatThrownBy(() -> service.commitShot(5L, List.of(new StateDelta(
                StoryEvent.SUBJECT_FACT, "secret", StoryEvent.CHANGE_SET, "钥匙在抽屉", null)), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能追加");
        assertThatThrownBy(() -> service.commitShot(5L, List.of(new StateDelta(
                StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_ADD, "湿透", null)), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能覆盖");
    }

    @Test
    void stateFoldsLaterEventsOverEarlierOnesAndAppendsListSections() {
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event(10L, StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_SET, "衣物干燥"),
                event(11L, StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_SET, "衣物湿透"),
                event(12L, StoryEvent.SUBJECT_FACT, "钥匙位置", StoryEvent.CHANGE_ADD, "抽屉"),
                event(13L, StoryEvent.SUBJECT_OPEN_LOOP, "谁拿了钥匙", StoryEvent.CHANGE_ADD, "RESOLVED")));

        Map<String, Object> state = service().state(1L, 2L);

        assertThat(asMap(asMap(state.get("characters")).get("林川")))
                .containsEntry("value", "衣物湿透")
                .containsEntry("eventId", 11L);
        assertThat(asList(state.get("facts"))).hasSize(1);
        assertThat(asList(state.get("openLoops"))).hasSize(1);
    }

    @Test
    void snapshotRefusesToOverwriteADivergentRebuild() {
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event(10L, StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_SET, "衣物湿透")));
        when(snapshotMapper.selectOne(any())).thenReturn(StoryStateSnapshot.builder()
                .id(77L).projectId(1L).episodeId(2L).afterEventId(10L).stateHash("stale").build());

        assertThatThrownBy(() -> service().snapshot(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void snapshotIsReusedWhenTheRebuildMatchesTheStoredHash() {
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event(10L, StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_SET, "衣物湿透")));
        StoryStateService service = service();
        service.snapshot(1L, 2L);
        ArgumentCaptor<StoryStateSnapshot> inserted = ArgumentCaptor.forClass(StoryStateSnapshot.class);
        verify(snapshotMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStateHash()).hasSize(64);
        assertThat(inserted.getValue().getAfterEventId()).isEqualTo(10L);

        when(snapshotMapper.selectOne(any())).thenReturn(inserted.getValue());

        assertThat(service.snapshot(1L, 2L).getId()).isEqualTo(inserted.getValue().getId());
        verify(snapshotMapper, never()).updateById(any(StoryStateSnapshot.class));
    }

    private void stubCommittedShot() {
        when(storyboardService.getItemById(5L)).thenReturn(item(41L));
        when(storyboardService.getById(20L)).thenReturn(Storyboard.builder().id(20L).projectId(1L).build());
        when(takeMapper.selectById(41L)).thenReturn(ProductionTake.builder()
                .id(41L).storyboardItemId(5L).build());
        when(qcResultMapper.selectOne(any())).thenReturn(qc(QcResult.PASS));
    }

    private StoryStateService service() {
        return new StoryStateService(eventMapper, snapshotMapper, takeMapper, qcResultMapper,
                storyboardService, new ObjectMapper());
    }

    private static StoryboardItem item(Long selectedTakeId) {
        return StoryboardItem.builder()
                .id(5L).storyboardId(20L).storyboardEpisodeId(2L).selectedTakeId(selectedTakeId).build();
    }

    private static QcResult qc(String status) {
        return QcResult.builder().takeId(41L).runId(7L).storyboardItemId(5L).status(status).build();
    }

    private static StateDelta characterDelta() {
        return new StateDelta(StoryEvent.SUBJECT_CHARACTER, "林川", StoryEvent.CHANGE_SET, "衣物湿透", null);
    }

    private static StoryEvent event(Long id, String subjectType, String key, String changeKind, String value) {
        return StoryEvent.builder()
                .id(id).projectId(1L).episodeId(2L).storyboardItemId(5L)
                .subjectType(subjectType).subjectKey(key).changeKind(changeKind).value(value)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}

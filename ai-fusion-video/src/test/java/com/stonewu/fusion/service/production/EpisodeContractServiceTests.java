package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.production.EpisodeContract;
import com.stonewu.fusion.entity.production.StoryEvent;
import com.stonewu.fusion.mapper.production.EpisodeContractMapper;
import com.stonewu.fusion.service.production.EpisodeContractService.ContractInput;
import com.stonewu.fusion.service.production.EpisodeContractService.Violation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EpisodeContractServiceTests {

    @Mock
    private EpisodeContractMapper contractMapper;

    @Mock
    private StoryStateService storyStateService;

    private EpisodeContractService service() {
        return new EpisodeContractService(contractMapper, storyStateService);
    }

    @Test
    void lintWithoutContractReportsMissingBaseline() {
        when(contractMapper.selectOne(any())).thenReturn(null);

        assertThat(service().lint(1L, 2L))
                .extracting(Violation::code)
                .containsExactly(EpisodeContractService.CONTRACT_MISSING);
    }

    @Test
    void lintReportsUnmetOutputStateAndUnknownSubjects() {
        stubContract("{\"CHARACTER:林川\":\"衣物湿透\",\"CHARACTER:陈默\":\"已离场\"}", null, null);
        when(storyStateService.state(1L, 2L)).thenReturn(stateOf("林川", "衣物干燥"));
        when(storyStateService.events(1L, 2L)).thenReturn(List.of());

        List<Violation> violations = service().lint(1L, 2L);

        assertThat(violations).extracting(Violation::code).containsExactlyInAnyOrder(
                EpisodeContractService.OUTPUT_STATE_MISMATCH,
                EpisodeContractService.UNKNOWN_SUBJECT);
    }

    @Test
    void lintPassesWhenCommittedEventsSatisfyTheContract() {
        stubContract("{\"CHARACTER:林川\":\"衣物湿透\"}", null, null);
        when(storyStateService.state(1L, 2L)).thenReturn(stateOf("林川", "衣物湿透"));
        when(storyStateService.events(1L, 2L)).thenReturn(List.of());

        assertThat(service().lint(1L, 2L)).isEmpty();
    }

    @Test
    void lintChecksBeatsAgainstCommittedEventValues() {
        stubContract(null, "[\"钥匙\",\"湿透\"]", null);
        when(storyStateService.state(1L, 2L)).thenReturn(new LinkedHashMap<>());
        when(storyStateService.events(1L, 2L)).thenReturn(List.of(
                StoryEvent.builder().id(10L).subjectType(StoryEvent.SUBJECT_FACT)
                        .subjectKey("钥匙位置").value("钥匙在抽屉").build()));

        List<Violation> violations = service().lint(1L, 2L);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(EpisodeContractService.REQUIRED_BEAT_MISSING);
        assertThat(violations.get(0).detail()).contains("湿透").doesNotContain("钥匙");
    }

    @Test
    void lintRequiresOpenLoopsToBeClosed() {
        stubContract(null, null, "[\"谁拿了钥匙\",\"失踪的司机\"]");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("characters", new LinkedHashMap<String, Object>());
        List<Object> loops = new ArrayList<>();
        loops.add(Map.of("key", "谁拿了钥匙", "value", "提出疑问", "eventId", 11L));
        loops.add(Map.of("key", "谁拿了钥匙", "value", EpisodeContractService.OPEN_LOOP_RESOLVED, "eventId", 12L));
        state.put("openLoops", loops);
        when(storyStateService.state(1L, 2L)).thenReturn(state);
        when(storyStateService.events(1L, 2L)).thenReturn(List.of());

        assertThat(service().lint(1L, 2L))
                .extracting(Violation::detail)
                .containsExactly("要求的悬念从未被登记: 失踪的司机");
    }

    @Test
    void defineIncrementsRevisionOnRedelivery() {
        EpisodeContract existing = EpisodeContract.builder()
                .id(5L).projectId(1L).episodeId(2L).revision(3).build();
        when(contractMapper.selectOne(any())).thenReturn(existing);

        EpisodeContract saved = service().define(1L, 2L,
                new ContractInput(null, "{}", "[]", null), 7L);

        assertThat(saved.getRevision()).isEqualTo(4);
        assertThat(saved.getDefinedBy()).isEqualTo(7L);
        verify(contractMapper).updateById(existing);
        verify(contractMapper, never()).insert(any(EpisodeContract.class));
    }

    @Test
    void defineRejectsMalformedDeclarations() {
        EpisodeContractService service = service();

        assertThatThrownBy(() -> service.define(1L, 2L,
                new ContractInput("不是JSON", null, null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是 JSON 对象");
        assertThatThrownBy(() -> service.define(1L, 2L,
                new ContractInput(null, null, "{\"a\":1}", null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是 JSON 数组");
    }

    private void stubContract(String outputStateJson, String requiredBeatsJson, String mustResolveJson) {
        when(contractMapper.selectOne(any())).thenReturn(EpisodeContract.builder()
                .id(5L).projectId(1L).episodeId(2L)
                .outputStateJson(outputStateJson)
                .requiredBeatsJson(requiredBeatsJson)
                .mustResolveJson(mustResolveJson)
                .build());
    }

    private static Map<String, Object> stateOf(String character, String value) {
        Map<String, Object> characters = new LinkedHashMap<>();
        characters.put(character, Map.of("value", value, "eventId", 10L));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("characters", characters);
        state.put("props", new LinkedHashMap<String, Object>());
        state.put("locations", new LinkedHashMap<String, Object>());
        state.put("facts", new ArrayList<>());
        state.put("openLoops", new ArrayList<>());
        return state;
    }
}

package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.production.StoryEvent;
import com.stonewu.fusion.entity.production.StoryStateSnapshot;
import com.stonewu.fusion.mapper.production.StoryEventMapper;
import com.stonewu.fusion.mapper.production.StoryStateSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * PR-021/022: Story Event Store + State Snapshot
 * append-only：事件不可修改/删除
 * replay：从 events 重放出完整状态
 * snapshot：定期固化，避免全量重放
 */
@Service
@RequiredArgsConstructor
public class StoryEventService {

    private final StoryEventMapper eventMapper;
    private final StoryStateSnapshotMapper snapshotMapper;

    private static final Set<String> VALID_EVENT_TYPES = Set.of(
        "CHARACTER_STATE_CHANGED", "PROP_STATE_CHANGED", "RELATION_CHANGED",
        "LOCATION_CHANGED", "FACT_REVEALED", "OPEN_LOOP_CREATED",
        "OPEN_LOOP_RESOLVED", "WARDROBE_CHANGED", "INJURY_CHANGED");

    /** 追加事件（幂等：同 idempotency_key 不重复插入） */
    @Transactional
    public StoryEvent append(StoryEvent event) {
        if (!VALID_EVENT_TYPES.contains(event.getEventType())) {
            throw new IllegalArgumentException("Invalid event type: " + event.getEventType());
        }
        if (event.getIdempotencyKey() != null) {
            Long count = eventMapper.selectCount(
                new LambdaQueryWrapper<StoryEvent>()
                    .eq(StoryEvent::getIdempotencyKey, event.getIdempotencyKey()));
            if (count > 0) return event;
        }
        eventMapper.insert(event);
        return event;
    }

    /** 重放：从 events 恢复状态快照 */
    public Map<String, Object> replay(Long projectId, Long episodeId) {
        List<StoryEvent> events = eventMapper.selectList(
            new LambdaQueryWrapper<StoryEvent>()
                .eq(StoryEvent::getProjectId, projectId)
                .eq(episodeId != null, StoryEvent::getEpisodeId, episodeId)
                .orderByAsc(StoryEvent::getId));
        return foldEvents(events);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> foldEvents(List<StoryEvent> events) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("characters", new LinkedHashMap<String, Object>());
        state.put("props", new LinkedHashMap<String, Object>());
        state.put("relations", new ArrayList<>());
        state.put("locations", new LinkedHashMap<String, Object>());
        state.put("facts", new ArrayList<>());
        state.put("openLoops", new ArrayList<>());
        for (StoryEvent e : events) {
            switch (e.getEventType()) {
                case "CHARACTER_STATE_CHANGED" -> mergeState(state, "characters", e.getCharacterName(), e.getNewValue());
                case "PROP_STATE_CHANGED" -> mergeState(state, "props", e.getPropName(), e.getNewValue());
                case "LOCATION_CHANGED" -> mergeState(state, "locations", e.getLocationName(), e.getNewValue());
                case "FACT_REVEALED" -> ((List<Object>) state.computeIfAbsent("facts", k -> new ArrayList<>())).add(e.getNewValue());
                case "OPEN_LOOP_CREATED" -> ((List<Object>) state.computeIfAbsent("openLoops", k -> new ArrayList<>())).add(Map.of("desc", e.getNewValue(), "status", "open"));
                case "OPEN_LOOP_RESOLVED" -> ((List<Object>) state.computeIfAbsent("openLoops", k -> new ArrayList<>())).add(Map.of("desc", e.getNewValue(), "status", "resolved"));
                default -> { }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private void mergeState(Map<String, Object> state, String section, String key, String value) {
        if (key == null) return;
        Map<String, Object> sec = (Map<String, Object>) state.computeIfAbsent(section, k -> new LinkedHashMap<>());
        sec.put(key, value);
    }

    /** 创建快照 */
    @Transactional
    public StoryStateSnapshot createSnapshot(Long projectId, Long episodeId) {
        Map<String, Object> state = replay(projectId, episodeId);
        String json = com.fasterxml.jackson.databind.node.TextNode.valueOf(state.toString()).toString();
        String hash = sha256(json);
        StoryStateSnapshot snap = new StoryStateSnapshot();
        snap.setProjectId(projectId);
        snap.setEpisodeId(episodeId);
        snap.setStateJson(json);
        snap.setHashVersion(hash);
        snapshotMapper.insert(snap);
        return snap;
    }

    private String sha256(String data) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

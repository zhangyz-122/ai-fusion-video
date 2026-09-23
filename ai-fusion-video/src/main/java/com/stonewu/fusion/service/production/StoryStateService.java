package com.stonewu.fusion.service.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.stonewu.fusion.service.storyboard.StoryboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PR-021 / PR-022：剧情状态事实源。
 *
 * <p>事件 append-only，状态由事件按 id 升序折叠得到；快照只是折叠结果的可重建缓存。
 * 提交入口强制校验契约不变量：镜头必须已选定一个质检 PASS 的候选。
 * 契约定义见 dev-docs/2-in-progress/20260923-story-state/story-state-contract.md。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryStateService {

    public static final long PROJECT_LEVEL_EPISODE = 0L;

    private static final Set<String> SUBJECT_TYPES = Set.of(
            StoryEvent.SUBJECT_CHARACTER, StoryEvent.SUBJECT_PROP, StoryEvent.SUBJECT_LOCATION,
            StoryEvent.SUBJECT_FACT, StoryEvent.SUBJECT_OPEN_LOOP);

    private static final Set<String> LIST_SUBJECTS = Set.of(
            StoryEvent.SUBJECT_FACT, StoryEvent.SUBJECT_OPEN_LOOP);

    private static final Map<String, String> SECTION_BY_SUBJECT = Map.of(
            StoryEvent.SUBJECT_CHARACTER, "characters",
            StoryEvent.SUBJECT_PROP, "props",
            StoryEvent.SUBJECT_LOCATION, "locations",
            StoryEvent.SUBJECT_FACT, "facts",
            StoryEvent.SUBJECT_OPEN_LOOP, "openLoops");

    private static final Set<String> CHANGE_KINDS = Set.of(StoryEvent.CHANGE_SET, StoryEvent.CHANGE_ADD);

    private final StoryEventMapper eventMapper;
    private final StoryStateSnapshotMapper snapshotMapper;
    private final ProductionTakeMapper takeMapper;
    private final QcResultMapper qcResultMapper;
    private final StoryboardService storyboardService;
    private final ObjectMapper objectMapper;

    public record StateDelta(String subjectType,
                             String subjectKey,
                             String changeKind,
                             String value,
                             Long predecessorEventId) {
    }

    /**
     * 提交一个镜头声明的剧情状态变更。同一镜头重复提交同一主体变更是幂等的。
     */
    @Transactional
    @CacheEvict(value = "storyState", allEntries = true)
    public List<StoryEvent> commitShot(Long storyboardItemId, List<StateDelta> deltas, Long userId) {
        if (deltas == null || deltas.isEmpty()) {
            throw new BusinessException(400, "剧情状态变更不能为空");
        }
        StoryboardItem item = requireItem(storyboardItemId);
        Storyboard storyboard = storyboardService.getById(item.getStoryboardId());
        if (storyboard == null) {
            throw new BusinessException(404, "分镜不存在: " + item.getStoryboardId());
        }
        requireSelectedAndPassed(item);

        long episodeId = item.getStoryboardEpisodeId() == null
                ? PROJECT_LEVEL_EPISODE : item.getStoryboardEpisodeId();
        List<StoryEvent> committed = new ArrayList<>();
        for (StateDelta delta : deltas) {
            validate(delta);
            String idempotencyKey = idempotencyKey(storyboardItemId, delta);
            StoryEvent existing = eventMapper.selectOne(new LambdaQueryWrapper<StoryEvent>()
                    .eq(StoryEvent::getProjectId, storyboard.getProjectId())
                    .eq(StoryEvent::getIdempotencyKey, idempotencyKey));
            if (existing != null) {
                committed.add(existing);
                continue;
            }
            StoryEvent event = StoryEvent.builder()
                    .projectId(storyboard.getProjectId())
                    .episodeId(episodeId)
                    .storyboardItemId(storyboardItemId)
                    .subjectType(delta.subjectType())
                    .subjectKey(delta.subjectKey())
                    .changeKind(delta.changeKind())
                    .value(delta.value())
                    .predecessorEventId(delta.predecessorEventId())
                    .idempotencyKey(idempotencyKey)
                    .createdBy(userId)
                    .build();
            eventMapper.insert(event);
            committed.add(event);
        }
        log.info("[storyState] 镜头 {} 提交剧情变更 {} 条", storyboardItemId, committed.size());
        return committed;
    }

    /**
     * 折叠当前剧情状态。{@code episodeId} 为 null 时返回项目全量，
     * 否则返回该分集事件与项目级事件的并集。
     */
    @Cacheable(value = "storyState",
            key = "'state:' + #projectId + ':' + (#episodeId == null ? 'all' : #episodeId)")
    public Map<String, Object> state(Long projectId, Long episodeId) {
        return fold(projectId, episodeId);
    }

    /** 固化当前折叠位置，用于避免全量重放并检测状态漂移。 */
    @Transactional
    @CacheEvict(value = "storyState", allEntries = true)
    public StoryStateSnapshot snapshot(Long projectId, Long episodeId) {
        long afterEventId = lastEventId(projectId, episodeId);
        String json = writeJson(fold(projectId, episodeId));
        String hash = sha256(json);
        StoryStateSnapshot existing = snapshotMapper.selectOne(new LambdaQueryWrapper<StoryStateSnapshot>()
                .eq(StoryStateSnapshot::getProjectId, projectId)
                .eq(StoryStateSnapshot::getEpisodeId, normalizedEpisode(episodeId))
                .eq(StoryStateSnapshot::getAfterEventId, afterEventId));
        if (existing != null) {
            if (hash.equals(existing.getStateHash())) {
                return existing;
            }
            // 同一事件位置折叠出不同状态说明重建逻辑漂移了，必须显式暴露而不是静默覆盖。
            throw new BusinessException(500, "剧情状态快照与事件重放不一致: project=" + projectId
                    + ", episode=" + existing.getEpisodeId() + ", afterEvent=" + afterEventId);
        }
        StoryStateSnapshot snapshot = StoryStateSnapshot.builder()
                .projectId(projectId)
                .episodeId(normalizedEpisode(episodeId))
                .afterEventId(afterEventId)
                .stateJson(json)
                .stateHash(hash)
                .build();
        snapshotMapper.insert(snapshot);
        return snapshot;
    }

    private Map<String, Object> fold(Long projectId, Long episodeId) {
        List<StoryEvent> events = eventMapper.selectList(new LambdaQueryWrapper<StoryEvent>()
                .eq(StoryEvent::getProjectId, projectId)
                .in(episodeId != null, StoryEvent::getEpisodeId, episodeScope(episodeId))
                .orderByAsc(StoryEvent::getId));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("characters", new LinkedHashMap<String, Object>());
        state.put("props", new LinkedHashMap<String, Object>());
        state.put("locations", new LinkedHashMap<String, Object>());
        state.put("facts", new ArrayList<>());
        state.put("openLoops", new ArrayList<>());
        for (StoryEvent event : events) {
            apply(state, event);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> state, StoryEvent event) {
        String section = SECTION_BY_SUBJECT.get(event.getSubjectType());
        if (section == null) {
            return;
        }
        Object bucket = state.get(section);
        if (bucket instanceof List<?> list) {
            ((List<Object>) list).add(Map.of(
                    "key", event.getSubjectKey(),
                    "value", event.getValue() == null ? "" : event.getValue(),
                    "eventId", event.getId()));
            return;
        }
        ((Map<String, Object>) bucket).put(event.getSubjectKey(), Map.of(
                "value", event.getValue() == null ? "" : event.getValue(),
                "eventId", event.getId()));
    }

    /** 契约不变量：只有选定候选且其质检结论为 PASS 的镜头才能提交剧情。 */
    private void requireSelectedAndPassed(StoryboardItem item) {
        Long takeId = item.getSelectedTakeId();
        if (takeId == null) {
            throw new BusinessException(409, "镜头尚未选定候选，不能提交剧情状态");
        }
        ProductionTake take = takeMapper.selectById(takeId);
        if (take == null || !item.getId().equals(take.getStoryboardItemId())) {
            throw new BusinessException(409, "镜头选定的候选已失效，不能提交剧情状态");
        }
        QcResult qc = qcResultMapper.selectOne(new LambdaQueryWrapper<QcResult>()
                .eq(QcResult::getTakeId, takeId));
        if (qc == null || !QcResult.PASS.equals(qc.getStatus())) {
            throw new BusinessException(409, "选定候选未通过质检，不能提交剧情状态");
        }
    }

    private StoryboardItem requireItem(Long storyboardItemId) {
        StoryboardItem item = storyboardService.getItemById(storyboardItemId);
        if (item == null) {
            throw new BusinessException(404, "分镜条目不存在: " + storyboardItemId);
        }
        return item;
    }

    private void validate(StateDelta delta) {
        if (delta == null || !StringUtils.hasText(delta.subjectKey()) || !StringUtils.hasText(delta.value())) {
            throw new BusinessException(400, "剧情状态变更缺少主体或取值");
        }
        if (delta.subjectKey().length() > 128) {
            throw new BusinessException(400, "剧情主体名称超过 128 字符");
        }
        if (!SUBJECT_TYPES.contains(delta.subjectType())) {
            throw new BusinessException(400, "未知的剧情主体类型: " + delta.subjectType());
        }
        if (!CHANGE_KINDS.contains(delta.changeKind())) {
            throw new BusinessException(400, "未知的剧情变更方式: " + delta.changeKind());
        }
        boolean listSubject = LIST_SUBJECTS.contains(delta.subjectType());
        if (listSubject != StoryEvent.CHANGE_ADD.equals(delta.changeKind())) {
            throw new BusinessException(400, "FACT 与 OPEN_LOOP 只能追加，其余主体只能覆盖");
        }
    }

    private String idempotencyKey(Long storyboardItemId, StateDelta delta) {
        return storyboardItemId + ":" + delta.subjectType() + ":"
                + delta.subjectKey() + ":" + delta.changeKind();
    }

    private List<Long> episodeScope(Long episodeId) {
        return List.of(episodeId, PROJECT_LEVEL_EPISODE);
    }

    private long normalizedEpisode(Long episodeId) {
        return episodeId == null ? PROJECT_LEVEL_EPISODE : episodeId;
    }

    private long lastEventId(Long projectId, Long episodeId) {
        List<StoryEvent> latest = eventMapper.selectList(new LambdaQueryWrapper<StoryEvent>()
                .eq(StoryEvent::getProjectId, projectId)
                .in(episodeId != null, StoryEvent::getEpisodeId, episodeScope(episodeId))
                .orderByDesc(StoryEvent::getId)
                .last("LIMIT 1"));
        return latest.isEmpty() ? 0L : latest.get(0).getId();
    }

    private String writeJson(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "剧情状态无法序列化: " + exception.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}

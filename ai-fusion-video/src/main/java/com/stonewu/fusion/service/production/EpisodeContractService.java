package com.stonewu.fusion.service.production;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.production.EpisodeContract;
import com.stonewu.fusion.entity.production.StoryEvent;
import com.stonewu.fusion.mapper.production.EpisodeContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PR-023：分集剧情契约与其 lint。
 *
 * <p>契约只是判定基线，剧情事实仍以 {@code afv_story_event} 为准；lint 完全由已提交事件
 * 与契约声明比对得出，不引入第二套状态推断。这里不加缓存：提交事件会改变 lint 结果，
 * 而事件提交方并不持有本服务的缓存键，跨缓存失效比省下的几次查询更贵。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodeContractService {

    public static final String CONTRACT_MISSING = "EPISODE_CONTRACT_MISSING";
    public static final String OUTPUT_STATE_MISMATCH = "STORY_OUTPUT_STATE_MISMATCH";
    public static final String REQUIRED_BEAT_MISSING = "STORY_REQUIRED_BEAT_MISSING";
    public static final String OPEN_LOOP_UNRESOLVED = "STORY_OPEN_LOOP_UNRESOLVED";
    public static final String UNKNOWN_SUBJECT = "STORY_UNKNOWN_SUBJECT";

    public static final String OPEN_LOOP_RESOLVED = "RESOLVED";

    private static final Map<String, String> SECTION_BY_SUBJECT = Map.of(
            StoryEvent.SUBJECT_CHARACTER, "characters",
            StoryEvent.SUBJECT_PROP, "props",
            StoryEvent.SUBJECT_LOCATION, "locations",
            StoryEvent.SUBJECT_FACT, "facts",
            StoryEvent.SUBJECT_OPEN_LOOP, "openLoops");

    private final EpisodeContractMapper contractMapper;
    private final StoryStateService storyStateService;

    public record ContractInput(String outputStateJson,
                                String requiredBeatsJson,
                                String mustResolveJson) {
    }

    public record Violation(String code, String detail) {
    }

    @Transactional
    public EpisodeContract define(Long projectId, Long episodeId, ContractInput input, Long userId) {
        if (projectId == null || episodeId == null) {
            throw new BusinessException(400, "剧情契约必须同时指定项目与分集");
        }
        requireJson(input.outputStateJson(), "outputState");
        requireJsonArray(input.requiredBeatsJson(), "requiredBeats");
        requireJsonArray(input.mustResolveJson(), "mustResolve");

        EpisodeContract existing = find(projectId, episodeId);
        if (existing == null) {
            EpisodeContract contract = EpisodeContract.builder()
                    .projectId(projectId)
                    .episodeId(episodeId)
                    .outputStateJson(input.outputStateJson())
                    .requiredBeatsJson(input.requiredBeatsJson())
                    .mustResolveJson(input.mustResolveJson())
                    .revision(1)
                    .definedBy(userId)
                    .build();
            contractMapper.insert(contract);
            return contract;
        }
        existing.setOutputStateJson(input.outputStateJson());
        existing.setRequiredBeatsJson(input.requiredBeatsJson());
        existing.setMustResolveJson(input.mustResolveJson());
        existing.setRevision(existing.getRevision() == null ? 1 : existing.getRevision() + 1);
        existing.setDefinedBy(userId);
        contractMapper.updateById(existing);
        return existing;
    }

    public EpisodeContract current(Long projectId, Long episodeId) {
        return find(projectId, episodeId);
    }

    /** 用已提交剧情事件校验分集契约，返回全部违规项。 */
    public List<Violation> lint(Long projectId, Long episodeId) {
        EpisodeContract contract = find(projectId, episodeId);
        if (contract == null) {
            return List.of(new Violation(CONTRACT_MISSING, "分集 " + episodeId + " 尚未定义剧情契约"));
        }
        List<Violation> violations = new ArrayList<>();
        List<StoryEvent> events = storyStateService.events(projectId, episodeId);
        Map<String, Object> state = storyStateService.state(projectId, episodeId);

        JSONObject output = parseObject(contract.getOutputStateJson());
        for (String declaration : output.keySet()) {
            String[] subject = splitSubject(declaration);
            String actual = actualValue(state, subject[0], subject[1]);
            String expected = output.getStr(declaration);
            if (actual == null) {
                violations.add(new Violation(UNKNOWN_SUBJECT,
                        "契约引用的主体没有已提交的剧情事件: " + declaration));
            } else if (expected != null && !expected.equals(actual)) {
                violations.add(new Violation(OUTPUT_STATE_MISMATCH,
                        declaration + " 期望 " + expected + "，实际 " + actual));
            }
        }

        for (String beat : parseArray(contract.getRequiredBeatsJson()).toList(String.class)) {
            if (!StringUtils.hasText(beat)) {
                continue;
            }
            boolean matched = events.stream().anyMatch(event -> event.getValue() != null
                    && event.getValue().contains(beat));
            if (!matched) {
                violations.add(new Violation(REQUIRED_BEAT_MISSING, "要求的剧情节拍没有出现在已提交事件中: " + beat));
            }
        }

        for (String loopKey : parseArray(contract.getMustResolveJson()).toList(String.class)) {
            if (!StringUtils.hasText(loopKey)) {
                continue;
            }
            String closed = actualValue(state, StoryEvent.SUBJECT_OPEN_LOOP, loopKey);
            if (closed == null) {
                violations.add(new Violation(OPEN_LOOP_UNRESOLVED, "要求的悬念从未被登记: " + loopKey));
            } else if (!OPEN_LOOP_RESOLVED.equalsIgnoreCase(closed)) {
                violations.add(new Violation(OPEN_LOOP_UNRESOLVED, "要求的悬念仍未闭合: " + loopKey));
            }
        }
        return violations;
    }

    @SuppressWarnings("unchecked")
    private String actualValue(Map<String, Object> state, String subjectType, String subjectKey) {
        String section = SECTION_BY_SUBJECT.get(subjectType);
        if (section == null) {
            return null;
        }
        Object bucket = state.get(section);
        if (bucket instanceof Map<?, ?> map) {
            Object entry = ((Map<String, Object>) map).get(subjectKey);
            return entry instanceof Map<?, ?> value ? String.valueOf(((Map<String, Object>) value).get("value")) : null;
        }
        if (bucket instanceof List<?> list) {
            String latest = null;
            for (Object element : list) {
                if (element instanceof Map<?, ?> entry && subjectKey.equals(entry.get("key"))) {
                    latest = String.valueOf(entry.get("value"));
                }
            }
            return latest;
        }
        return null;
    }

    private EpisodeContract find(Long projectId, Long episodeId) {
        return contractMapper.selectOne(new LambdaQueryWrapper<EpisodeContract>()
                .eq(EpisodeContract::getProjectId, projectId)
                .eq(EpisodeContract::getEpisodeId, episodeId));
    }

    /** 契约声明的键形如 {@code CHARACTER:林川}。 */
    private String[] splitSubject(String declaration) {
        int separator = declaration.indexOf(':');
        if (separator <= 0) {
            throw new BusinessException(400, "剧情契约主体键必须写成 类型:名称，收到 " + declaration);
        }
        return new String[]{declaration.substring(0, separator), declaration.substring(separator + 1)};
    }

    private JSONObject parseObject(String json) {
        return StringUtils.hasText(json) ? JSONUtil.parseObj(json) : new JSONObject();
    }

    private JSONArray parseArray(String json) {
        return StringUtils.hasText(json) ? JSONUtil.parseArray(json) : new JSONArray();
    }

    private void requireJson(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            JSONUtil.parseObj(json);
        } catch (RuntimeException exception) {
            throw new BusinessException(400, field + " 必须是 JSON 对象");
        }
    }

    private void requireJsonArray(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            JSONUtil.parseArray(json);
        } catch (RuntimeException exception) {
            throw new BusinessException(400, field + " 必须是 JSON 数组");
        }
    }
}

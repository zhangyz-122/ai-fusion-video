package com.stonewu.fusion.service.production;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.controller.production.vo.ProductionStartReqVO;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 镜头生产门禁：没有提示词、首帧、合法时长或角色/场景绑定，不允许开 Run。
 */
@Service
public class ShotReadinessService {

    public static final BigDecimal MAX_DURATION_SECONDS = new BigDecimal("5");

    public ShotReadiness evaluate(StoryboardItem item) {
        return evaluate(item, null, null, null);
    }

    public ShotReadiness evaluate(StoryboardItem item, ProductionStartReqVO request) {
        if (request == null) {
            return evaluate(item);
        }
        return evaluate(item, request.getPrompt(), request.getFirstFrameImageUrl(), request.getDuration());
    }

    public ShotReadiness evaluate(
            StoryboardItem item,
            String promptOverride,
            String firstFrameOverride,
            Integer durationOverride
    ) {
        List<ShotReadiness.Blocker> blockers = new ArrayList<>();

        String prompt = firstNonBlank(promptOverride, item.getVideoPrompt(), item.getContent());
        if (!StringUtils.hasText(prompt)) {
            blockers.add(new ShotReadiness.Blocker("MISSING_PROMPT", "缺少视频提示词或画面内容"));
        }

        String firstFrame = firstNonBlank(
                firstFrameOverride,
                item.getFirstFrameImageUrl(),
                item.getGeneratedImageUrl(),
                item.getImageUrl()
        );
        if (!StringUtils.hasText(firstFrame)) {
            blockers.add(new ShotReadiness.Blocker("MISSING_FIRST_FRAME", "缺少锁定首帧"));
        }

        BigDecimal duration = resolveDuration(item, durationOverride);
        if (duration == null) {
            blockers.add(new ShotReadiness.Blocker("MISSING_DURATION", "请先设定镜头时长（不超过 5 秒）"));
        } else if (duration.compareTo(BigDecimal.ZERO) <= 0) {
            blockers.add(new ShotReadiness.Blocker("DURATION_INVALID", "镜头时长必须大于 0 秒"));
        } else if (duration.compareTo(MAX_DURATION_SECONDS) > 0) {
            blockers.add(new ShotReadiness.Blocker("DURATION_TOO_LONG", "镜头时长不能超过 5 秒"));
        }

        if (!hasBible(item)) {
            blockers.add(new ShotReadiness.Blocker("MISSING_BIBLE", "请先绑定角色或场景资产"));
        }

        return ShotReadiness.blocked(blockers);
    }

    public void requireReady(StoryboardItem item, ProductionStartReqVO request) {
        evaluate(item, request).requireReady();
    }

    private static BigDecimal resolveDuration(StoryboardItem item, Integer durationOverride) {
        if (durationOverride != null) {
            return BigDecimal.valueOf(durationOverride.longValue());
        }
        return item.getDuration();
    }

    private static boolean hasBible(StoryboardItem item) {
        if (item.getSceneAssetItemId() != null) {
            return true;
        }
        String raw = item.getCharacterIds();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            JSONArray array = JSONUtil.parseArray(raw.trim());
            return array != null && !array.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

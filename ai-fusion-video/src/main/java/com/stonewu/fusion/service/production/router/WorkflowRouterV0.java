package com.stonewu.fusion.service.production.router;

import com.stonewu.fusion.entity.production.WorkflowProfile;
import com.stonewu.fusion.service.production.WorkflowProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * PR-026: Workflow Router V0 — 可解释的 Profile 路由器。
 * Hard Constraints 过滤 → 质量评分 → 选择最优 Profile。
 * 不做黑箱 learned router。
 */
@Service
@RequiredArgsConstructor
public class WorkflowRouterV0 {

    private final WorkflowProfileService profileService;

    public RouteResult route(RouteRequest req) {
        List<WorkflowProfile> candidates = profileService.listEnabled().stream()
            .filter(p -> supportsMediaType(p, req.mediaType()))
            .filter(p -> supportsDuration(p, req.maxDurationSeconds()))
            .toList();

        if (candidates.isEmpty()) return new RouteResult(null, "no eligible profile", List.of());

        WorkflowProfile best = candidates.stream()
            .max(Comparator.comparingInt(this::score))
            .orElse(candidates.get(0));

        return new RouteResult(best, "selected by score", List.of());
    }

    private boolean supportsMediaType(WorkflowProfile p, String mediaType) {
        return p.getMediaType() == null || p.getMediaType().equalsIgnoreCase(mediaType);
    }

    private boolean supportsDuration(WorkflowProfile p, Integer maxDuration) {
        if (p.getMaxDurationSeconds() == null || maxDuration == null) return true;
        return p.getMaxDurationSeconds() >= maxDuration;
    }

    private int score(WorkflowProfile p) {
        int score = 0;
        if ("PREMIUM".equals(p.getQualityTier())) score += 3;
        else if ("STANDARD".equals(p.getQualityTier())) score += 2;
        else score += 1;
        if ("ECONOMY".equals(p.getCostTier())) score += 2;
        return score;
    }

    public record RouteRequest(String mediaType, Integer maxDurationSeconds) {}
    public record RouteResult(WorkflowProfile profile, String reason, List<String> trace) {}
}

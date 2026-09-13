package com.stonewu.fusion.service.production;

import com.stonewu.fusion.common.BusinessException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 镜头能否进入 ProductionRun 的派生结果。不落库，避免和第二套状态打架。
 */
public record ShotReadiness(boolean ready, List<Blocker> blockers) {

    public record Blocker(String code, String message) {
    }

    public static ShotReadiness ok() {
        return new ShotReadiness(true, List.of());
    }

    public static ShotReadiness blocked(List<Blocker> blockers) {
        List<Blocker> copy = List.copyOf(blockers);
        return new ShotReadiness(copy.isEmpty(), copy);
    }

    public void requireReady() {
        if (ready) {
            return;
        }
        String message = blockers.stream().map(Blocker::message).collect(Collectors.joining("；"));
        throw new BusinessException(400, "镜头未就绪：" + message);
    }
}

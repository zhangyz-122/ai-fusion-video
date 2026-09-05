package com.stonewu.fusion.service.production.repair;

import org.springframework.stereotype.Service;

/**
 * PR-028: Retry/Repair Router — 失败不无限循环，每次 retry 有 parent/reason。
 */
@Service
public class RepairRouter {

    private static final int MAX_RETRIES = 3;

    public enum Action { RETRY_SAME, RETRY_NEW_SEED, SWITCH_WORKFLOW, HUMAN_REVIEW }

    public Action route(String errorCode, int attemptCount) {
        if (attemptCount >= MAX_RETRIES) return Action.HUMAN_REVIEW;
        return switch (errorCode) {
            case "IDENTITY_DRIFT", "MULTI_CHAR_MERGE" -> Action.RETRY_NEW_SEED;
            case "TEMPORAL_FLICKER" -> Action.RETRY_SAME;
            case "HAND_ERROR", "MOTION_ERROR" -> Action.RETRY_NEW_SEED;
            case "WORLD_STATE_CONFLICT", "CONTINUITY_ERROR" -> Action.HUMAN_REVIEW;
            default -> attemptCount < 2 ? Action.RETRY_SAME : Action.HUMAN_REVIEW;
        };
    }
}

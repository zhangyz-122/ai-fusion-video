package com.stonewu.fusion.service.production.gate;

import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * PR-027: First Frame Gate — 首帧 FAIL 不启动昂贵视频任务。
 */
@Service
public class FirstFrameGate {

    public record GateInput(String firstFrameUrl, String continuityContext) {}
    public record GateResult(boolean allowed, String reason) {}

    public GateResult evaluate(GateInput input) {
        if (input.firstFrameUrl() == null || input.firstFrameUrl().isBlank()) {
            return new GateResult(false, "first frame URL is missing — video generation blocked");
        }
        if (input.continuityContext() != null && input.continuityContext().contains("CONTINUITY_ERROR")) {
            return new GateResult(false, "continuity error detected in previous shot");
        }
        return new GateResult(true, "first frame ready");
    }
}

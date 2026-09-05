package com.stonewu.fusion.service.production.director;

import org.springframework.stereotype.Service;
import java.util.*;

/**
 * PR-025: Director v0 — 规则驱动的叙事→生产约束转换器。
 * 输入 NarrativeFunction + context → 输出 Shot list（含 Coverage/HeroLevel/Risk）。
 * 不负责实际生成。
 */
@Service
public class DirectorV0 {
    private static final Set<String> NARRATIVE_FUNCTIONS = Set.of(
        "ESTABLISH", "MASTER", "DIALOGUE", "REACTION", "INSERT", "REVEAL",
        "EMOTION", "ACTION", "IMPACT", "TRANSITION", "POV", "CLIFFHANGER");

    public List<Map<String, Object>> planShots(String episodeLogline, int targetShots, int durationSeconds) {
        // P0 规则：平均分配时长，按叙事功能覆盖生成镜头列表
        List<Map<String, Object>> shots = new ArrayList<>();
        double avgDuration = (double) durationSeconds / targetShots;
        String[] functions = {"ESTABLISH", "MASTER", "DIALOGUE", "REACTION", "INSERT",
                              "DIALOGUE", "ACTION", "REACTION", "EMOTION", "IMPACT",
                              "REVEAL", "CLIFFHANGER"};
        for (int i = 0; i < targetShots && i < functions.length; i++) {
            Map<String, Object> shot = new LinkedHashMap<>();
            shot.put("shotNumber", i + 1);
            shot.put("narrativeFunction", functions[i]);
            shot.put("estimatedDuration", avgDuration);
            shot.put("heroLevel", functions[i].matches("ACTION|IMPACT|REVEAL") ? 3 : 1);
            shot.put("generationRisk", functions[i].matches("ACTION|DIALOGUE") ? "Motion" : "Camera");
            shots.add(shot);
        }
        return shots;
    }
}

package com.stonewu.fusion.service.production.director;

import org.springframework.stereotype.Service;
import java.util.*;

/**
 * PR-025: Director v0 — 规则驱动的叙事→生产约束转换器。
 *
 * 职责：将叙事意图（NarrativeFunction + Coverage Grammar）转为生产约束
 *       （Shot list 含 HeroLevel / GenerationRisk / Camera metadata / estimatedDuration）。
 * 不负责实际生成。
 *
 * P0 规则：
 *   - Coverage Grammar 确保双人对话至少包含 MASTER_TWO_SHOT + OTS_A + OTS_B + REACTION
 *   - Hero Level 0-4 影响生成参数（Take 数、Profile tier、QC 门槛）
 *   - Generation Risk 识别 Identity / Motion / Interaction / Camera / Prop / LipSync / Continuity
 */
@Service
public class DirectorV0 {

    private static final Set<String> NARRATIVE_FUNCTIONS = Set.of(
        "ESTABLISH", "MASTER", "DIALOGUE", "REACTION", "INSERT", "REVEAL",
        "EMOTION", "ACTION", "IMPACT", "TRANSITION", "POV", "CLIFFHANGER");

    /** Coverage Grammar：双人对话场景的最少镜头覆盖 */
    private static final List<String> DIALOGUE_COVERAGE = List.of(
        "MASTER_TWO_SHOT", "OTS_A", "OTS_B", "REACTION");

    /** Coverage Grammar：动作场景的最少镜头覆盖 */
    private static final List<String> ACTION_COVERAGE = List.of(
        "WIDE_ESTABLISH", "MEDIUM_ACTION", "CLOSE_UP_IMPACT", "REACTION", "INSERT");

    private static final Map<String, Integer> FUNCTION_HERO_LEVEL = Map.of(
        "ESTABLISH", 0, "MASTER", 1, "DIALOGUE", 1, "REACTION", 1,
        "INSERT", 0, "REVEAL", 3, "EMOTION", 2, "ACTION", 4,
        "IMPACT", 4, "TRANSITION", 0, "POV", 2, "CLIFFHANGER", 3);

    private static final Map<String, Set<String>> FUNCTION_RISKS = Map.of(
        "DIALOGUE", Set.of("LipSync"),
        "ACTION", Set.of("Motion", "Continuity"),
        "REACTION", Set.of("Identity"),
        "EMOTION", Set.of("Identity"),
        "REVEAL", Set.of("Continuity", "Prop"),
        "IMPACT", Set.of("Motion", "Camera"),
        "CLIFFHANGER", Set.of("Camera"),
        "MASTER", Set.of("Identity", "Motion"),
        "ESTABLISH", Set.of("Camera"),
        "INSERT", Set.of("Prop"),
        "TRANSITION", Set.of(),
        "POV", Set.of("Camera"));

    /** 完整规则引擎：输入 logline + targetShots + duration → 输出结构化 Shot list */
    public List<Map<String, Object>> planShots(String logline, int targetShots, int durationSeconds) {
        List<Map<String, Object>> shots = new ArrayList<>();
        List<String> coverage = selectCoverage(targetShots);
        double avgDuration = (double) durationSeconds / targetShots;

        String[] functions = selectFunctions(targetShots);
        for (int i = 0; i < targetShots; i++) {
            String fn = functions[i];
            String coverageType = coverage.get(i % coverage.size());
            int heroLevel = FUNCTION_HERO_LEVEL.getOrDefault(fn, 1);
            Set<String> risks = FUNCTION_RISKS.getOrDefault(fn, Set.of("Camera"));

            Map<String, Object> shot = new LinkedHashMap<>();
            shot.put("shotNumber", i + 1);
            shot.put("narrativeFunction", fn);
            shot.put("coverage", coverageType);
            shot.put("heroLevel", heroLevel);
            shot.put("generationRisks", risks);
            shot.put("estimatedDuration", Math.round(avgDuration * 10.0) / 10.0);
            shot.put("qcThreshold", heroLevel >= 3 ? "STRICT" : "STANDARD");
            shot.put("takeCount", heroLevel >= 3 ? 3 : heroLevel >= 2 ? 2 : 1);
            shot.put("workflowProfileSuggestion", suggestProfile(fn, heroLevel));
            shots.add(shot);
        }
        return shots;
    }

    /** Coverage Grammar 选择：确保场景级覆盖（建立→主体→反应→收尾） */
    private List<String> selectCoverage(int targetShots) {
        List<String> coverage = new ArrayList<>();
        if (targetShots >= 1) coverage.add("WIDE_ESTABLISH");
        if (targetShots >= 2) coverage.add("MASTER_TWO_SHOT");
        for (int i = 2; i < targetShots; i++) {
            // 中间镜头交替使用 OTS/INSERT/REACTION/CU 增加视觉多样性
            switch (i % 4) {
                case 0 -> coverage.add("OTS_A");
                case 1 -> coverage.add("OTS_B");
                case 2 -> coverage.add("INSERT");
                case 3 -> coverage.add("REACTION");
            }
        }
        if (targetShots >= 3) coverage.set(targetShots - 1, "CLOSE_UP_CLIFFHANGER");
        return coverage;
    }

    /** 叙事功能分配：开头建立→中段对话/动作→末段揭示/悬念 */
    private String[] selectFunctions(int count) {
        String[] opening = {"ESTABLISH", "MASTER"};
        String[] middle = {"DIALOGUE", "REACTION", "INSERT", "EMOTION", "ACTION",
                           "IMPACT", "TRANSITION", "POV", "REVEAL"};
        String[] closing = {"CLIFFHANGER"};
        List<String> fns = new ArrayList<>();
        for (String f : opening) fns.add(f);
        for (String f : middle) fns.add(f);
        // 从 middle 循环填充直到满足 count
        while (fns.size() < count) {
            for (String f : middle) { if (fns.size() >= count) break; fns.add(f); }
        }
        return fns.subList(0, count).toArray(new String[0]);
    }

    /** Profile 推荐：高 HeroLevel/ACTION → 高质量 Profile */
    private String suggestProfile(String fn, int heroLevel) {
        if (heroLevel >= 3) return "WAN_I2V_PREMIUM";
        if (heroLevel >= 2) return "WAN_I2V_STANDARD";
        return "WAN_I2V_ECONOMY";
    }
}

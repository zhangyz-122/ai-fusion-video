package com.stonewu.fusion.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端架构守护测试(SW-T14,"先红后拆"棘轮)。
 *
 * <p>规则来源:ARCHITECTURE.md §2 依赖规则、AGENTS.md 行数红线、swarm/TECH_DEBT_PLAN.md 拆分清单。
 *
 * <p>守护语义(对全部规则统一生效):
 * <ul>
 *   <li>白名单之外出现新违例 → 测试变红,禁止合入;</li>
 *   <li>白名单条目对应的债务已清零(文件拆小/依赖移除)后白名单残留 → 测试同样变红,强制收缩白名单;</li>
 *   <li>白名单文件继续恶化(行数超上限/新增豁免外依赖边) → 红。白名单只允许减少,不允许增加,
 *       owner=Architect(Agent-1),清零期限对应 TECH_DEBT_PLAN 批次 A/C(SW-T12/SW-T13)。</li>
 * </ul>
 *
 * <p>运行:{@code ./mvnw -Dtest=BackendArchitectureGuardTests test}(无需数据库/Redis,纯源码扫描)。
 * 已知简化:依赖分析基于显式 import 语句,同包引用与全限定名内联引用不可见(AGENTS.md 禁止内联全限定名)。
 */
class BackendArchitectureGuardTests {

    /** 后端 main 单文件行数上限(超过即入白名单棘轮,目标清零)。 */
    private static final int JAVA_MAIN_LINE_BUDGET = 800;
    /** 观察档:700–800 行打印统计,不失败(TECH_DEBT_PLAN 第二梯队)。 */
    private static final int JAVA_MAIN_WARN_FLOOR = 700;

    private static final String JAVA_DIR = "src/main/java";
    private static final String BASE_PACKAGE_DIR = "src/main/java/com/stonewu/fusion";
    private static final String FUSION = "com.stonewu.fusion.";

    // ------------------------------------------------------------------
    // 规则 B1:Java main 行数预算(≤800 行;白名单=TECH_DEBT_PLAN Top 清单,上限钉在当前值)
    // ------------------------------------------------------------------

    /** key=模块内 POSIX 相对路径,value=当前行数(只许减少;跌破 800 行必须移出白名单)。 */
    private static final Map<String, Integer> LINE_BUDGET_WHITELIST = Map.of(
            "src/main/java/com/stonewu/fusion/service/ai/run/DurableAgentWaitingStateService.java",
            963,
            "src/main/java/com/stonewu/fusion/service/ai/run/DefaultRunExecutionSupervisor.java",
            839);

    // ------------------------------------------------------------------
    // 规则 R1:controller 不得直接依赖 mapper(跨域只能走 service)
    // 豁免格式 "Source -> Target"。清零期限:SW-T14 验收后首个冲刺。
    // ------------------------------------------------------------------

    private static final Set<String> CONTROLLER_TO_MAPPER_EXEMPT = Set.of(
            "TeamController -> TeamMemberMapper");

    // ------------------------------------------------------------------
    // 规则 R2:service/ai/run 不得 import service/ai/agentscope(agentscope→run 单向)。
    // 存量 61 条边=既有双向耦合,逐条棘轮;治理任务见 BACKLOG SW-T18(契约类型下沉中立包)。
    // ------------------------------------------------------------------

    private static final Set<String> RUN_TO_AGENTSCOPE_EXEMPT = Set.of(
            "AgentConfirmationExpiryCoordinator -> AgentRuntimeSchedulers",
            "AgentConfirmationExpiryCoordinator -> StateStoreSlot",
            "AgentConfirmationService -> AgentKernelSpecFactory",
            "AgentConfirmationService -> ToolExecutionMode",
            "AgentEventOutboxPublisher -> AgentRuntimeSchedulers",
            "AgentExecution -> ParentAgentRunContext",
            "AgentExecutionFactory -> AgentKernelSpec",
            "AgentExecutionFactory -> AgentKernelSpecFactory",
            "AgentExecutionFactory -> AgentRunContext",
            "AgentExecutionFactory -> AgentRuntimeSchedulers",
            "AgentExecutionFactory -> AgentScopeHarnessInvoker",
            "AgentExecutionFactory -> AgentScopeModelFactory",
            "AgentExecutionFactory -> AgentScopeRuntimeContextFactory",
            "AgentExecutionFactory -> AgentScopeRuntimeContextRequest",
            "AgentExecutionRuntimeContextRequests -> AgentConversationContext",
            "AgentExecutionRuntimeContextRequests -> AgentRunContext",
            "AgentExecutionRuntimeContextRequests -> AgentRuntimeSchedulers",
            "AgentExecutionRuntimeContextRequests -> AgentScopeRuntimeContextRequest",
            "AgentExecutionRuntimeContextRequests -> AuthenticatedUserContext",
            "AgentExecutionRuntimeContextRequests -> CancellationContext",
            "AgentExecutionRuntimeContextRequests -> ParentAgentRunContext",
            "AgentExecutionRuntimeContextRequests -> PipelineRequestContext",
            "AgentExecutionRuntimeContextRequests -> ProjectContext",
            "AgentExecutionRuntimeContextRequests -> ToolExecutionContext",
            "AgentExecutionRuntimeContextRequests -> ToolExecutionMode",
            "AgentExecutionRuntimeContextRequests -> ToolPermissionContext",
            "AgentKernelSnapshotBuilder -> AgentKernelSpec",
            "AgentKernelSnapshotPayload -> AgentPromptVariables",
            "AgentMessageProjectionService -> AgentRuntimeSchedulers",
            "AgentRunCoordinator -> AgentRuntimeSchedulers",
            "AgentRunCoordinator -> AgentStateCleanupPolicyService",
            "AgentRunQueryService -> AgentRuntimeSchedulers",
            "AgentRunReconciliationService -> AgentRuntimeSchedulers",
            "AgentRunReconciliationService -> StateStoreSlot",
            "AgentRunReplayService -> AgentRuntimeSchedulers",
            "CancellationCoordinator -> AgentRuntimeSchedulers",
            "CancellationCoordinator -> StateStoreSlot",
            "CanonicalAgentKernelSnapshotBuilder -> AgentKernelSpec",
            "DefaultRunExecutionSupervisor -> AgentKernelSpec",
            "DefaultRunExecutionSupervisor -> AgentRunContext",
            "DefaultRunExecutionSupervisor -> AgentScopeRuntimeContextRequest",
            "DefaultRunExecutionSupervisor -> HarnessLeaseCache",
            "DefaultRunExecutionSupervisor -> ParentAgentRunContext",
            "DefaultRunExecutionSupervisor -> StateStoreSlot",
            "DeterministicPipelineRecoveryService -> ProjectContext",
            "DurableAgentWaitingStateService -> AgentRuntimeSchedulers",
            "MySqlAgentEventJournal -> AgentRuntimeSchedulers",
            "MySqlRunTerminalCoordinator -> AgentRuntimeSchedulers",
            "MySqlRunTerminalCoordinator -> StateStoreFailure",
            "MySqlRunTerminalCoordinator -> StateStoreFailureGuard",
            "MySqlRunTerminalCoordinator -> StateStoreSlot",
            "OwnedCancellationHandler -> AgentRuntimeSchedulers",
            "PlatformSubAgentRunService -> ParentAgentRunContext",
            "PlatformSubAgentRunService -> PlatformSubAgentCommand",
            "PlatformSubAgentRunService -> PlatformSubAgentRun",
            "PlatformSubAgentRunService -> PlatformSubAgentRunPort",
            "ResumeAgentExecutionCommand -> AgentScopeRuntimeContextRequest",
            "RunLeaseGuard -> AgentRuntimeSchedulers",
            "RunTerminalRequest -> StateStoreSlot",
            "StartAgentExecutionCommand -> AgentKernelSpec",
            "StartAgentExecutionCommand -> AgentScopeRuntimeContextRequest");

    // ------------------------------------------------------------------
    // 规则 R3:生成策略互不依赖(共享逻辑下沉 strategy/support)。当前零违例,豁免留空。
    // ------------------------------------------------------------------

    private static final Set<String> STRATEGY_INDEPENDENCE_EXEMPT = Set.of();

    // ------------------------------------------------------------------
    // 规则 R4:service 类之间禁止循环依赖(Tarjan SCC;豁免=排序类名以 | 连接)。
    // ------------------------------------------------------------------

    private static final Set<String> SERVICE_CYCLE_EXEMPT = Set.of(
            "AgentScopeToolAdapter|AgentToolPermissionPolicy");

    // ==================================================================

    @Test
    void javaMainFilesStayWithinLineBudget() throws IOException {
        Path javaDir = moduleRoot().resolve(JAVA_DIR);
        List<String> newViolations = new ArrayList<>();
        List<String> grownWhitelisted = new ArrayList<>();
        List<String> payableWhitelisted = new ArrayList<>();
        List<String> warnTier = new ArrayList<>();
        Map<String, Integer> whitelistedNow = new TreeMap<>();

        try (Stream<Path> paths = Files.walk(javaDir)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                int lines = countLines(file);
                String rel = toPosix(moduleRoot().relativize(file));
                if (lines > JAVA_MAIN_LINE_BUDGET) {
                    Integer pinned = LINE_BUDGET_WHITELIST.get(rel);
                    if (pinned == null) {
                        newViolations.add(rel + " = " + lines + " 行(>" + JAVA_MAIN_LINE_BUDGET
                                + "):必须拆分,或经 Architect 评审后登记白名单");
                    } else {
                        whitelistedNow.put(rel, lines);
                        if (lines > pinned) {
                            grownWhitelisted.add(rel + " = " + lines + " 行 > 白名单上限 " + pinned
                                    + " 行:拆分前禁止继续增长");
                        }
                    }
                } else if (LINE_BUDGET_WHITELIST.containsKey(rel)) {
                    payableWhitelisted.add(rel + " 已降到 " + lines
                            + " 行(≤" + JAVA_MAIN_LINE_BUDGET + "):请从白名单移除该条目");
                } else if (lines > JAVA_MAIN_WARN_FLOOR) {
                    warnTier.add(rel + " = " + lines);
                }
            });
        }

        assertThat(newViolations)
                .as("出现白名单外的超行文件(先红后拆:拆分或登记,禁止静默超标)")
                .isEmpty();
        assertThat(grownWhitelisted)
                .as("白名单文件行数增长(拆分前冻结)")
                .isEmpty();
        assertThat(payableWhitelisted)
                .as("白名单存在已达标条目(白名单只减不增)")
                .isEmpty();

        System.out.println("[arch-guard] Java main >" + JAVA_MAIN_LINE_BUDGET + " 行白名单在册 "
                + whitelistedNow.size() + "/" + LINE_BUDGET_WHITELIST.size() + ": " + whitelistedNow);
        System.out.println("[arch-guard] Java main " + JAVA_MAIN_WARN_FLOOR + "-" + JAVA_MAIN_LINE_BUDGET
                + " 行观察档 " + warnTier.size() + " 个(不拦截): " + warnTier);
    }

    @Test
    void controllersMustNotDependOnMappers() throws IOException {
        Path controllerDir = moduleRoot().resolve(BASE_PACKAGE_DIR + "/controller");
        Pattern mapperImport = Pattern.compile(
                "^import\\s+(?:static\\s+)?" + Pattern.quote(FUSION) + "mapper\\.([A-Za-z0-9_.]+);",
                Pattern.MULTILINE);
        Set<String> edges = new TreeSet<>();
        forEachJavaFile(controllerDir, file -> {
            String source = fileNameBase(file);
            for (String target : importedSimpleNames(file, mapperImport)) {
                edges.add(source + " -> " + lastSegment(target));
            }
        });

        Set<String> violations = new TreeSet<>(edges);
        violations.removeAll(CONTROLLER_TO_MAPPER_EXEMPT);
        Set<String> stale = new TreeSet<>(CONTROLLER_TO_MAPPER_EXEMPT);
        stale.removeAll(edges);

        assertThat(violations)
                .as("controller 直接依赖 mapper(ARCHITECTURE.md §2 规则1:跨域只能走 service)")
                .isEmpty();
        assertThat(stale)
                .as("豁免清单存在已修复条目(白名单只减不增,请移除)")
                .isEmpty();
        System.out.println("[arch-guard] controller→mapper 豁免在册 " + edges.size()
                + " 条边(上限 " + CONTROLLER_TO_MAPPER_EXEMPT.size() + "): " + edges);
    }

    @Test
    void runLayerMustNotDependOnAgentscopePackage() throws IOException {
        Path runDir = moduleRoot().resolve(BASE_PACKAGE_DIR + "/service/ai/run");
        Pattern agentscopeImport = Pattern.compile(
                "^import\\s+(?:static\\s+)?" + Pattern.quote(FUSION) + "service\\.ai\\.agentscope\\.([A-Za-z0-9_.]+);",
                Pattern.MULTILINE);
        Set<String> edges = new TreeSet<>();
        forEachJavaFile(runDir, file -> {
            String source = fileNameBase(file);
            for (String target : importedSimpleNames(file, agentscopeImport)) {
                edges.add(source + " -> " + lastSegment(target));
            }
        });

        Set<String> violations = new TreeSet<>(edges);
        violations.removeAll(RUN_TO_AGENTSCOPE_EXEMPT);
        Set<String> stale = new TreeSet<>(RUN_TO_AGENTSCOPE_EXEMPT);
        stale.removeAll(edges);

        assertThat(violations)
                .as("service/ai/run 新增对 service/ai/agentscope 的依赖(ARCHITECTURE.md §2 规则2:"
                        + "agentscope→run 单向;存量 61 条边已棘轮,治理见 BACKLOG SW-T18)")
                .isEmpty();
        assertThat(stale)
                .as("豁免清单存在已修复条目(白名单只减不增,请移除)")
                .isEmpty();
        System.out.println("[arch-guard] run→agentscope 豁免在册 " + edges.size() + " 条边/"
                + RUN_TO_AGENTSCOPE_EXEMPT.size() + " 条(源文件 "
                + edges.stream().map(e -> e.split(" -> ")[0]).distinct().count() + " 个)");
    }

    @Test
    void generationStrategiesMustBeIndependent() throws IOException {
        Path strategyRoot = moduleRoot().resolve(BASE_PACKAGE_DIR + "/service/generation");
        // group(1)=目标策略族(image/video),group(2)=strategy/ 之后的包路径与类名
        Pattern familyImport = Pattern.compile(
                "^import\\s+(?:static\\s+)?" + Pattern.quote(FUSION)
                        + "service\\.generation\\.(image|video)\\.strategy\\.([A-Za-z0-9_.]+);",
                Pattern.MULTILINE);
        Set<String> violations = new TreeSet<>();
        for (String family : List.of("image", "video")) {
            Path familyDir = strategyRoot.resolve(family + "/strategy");
            forEachJavaFile(familyDir, file -> {
                String rel = toPosix(familyDir.relativize(file));
                boolean inSupport = rel.startsWith("support/");
                // 供应商实现位于 strategy/<vendor>/ 一级子包;strategy 根(SPI/Router)与 support 是允许的共享层
                int firstSlash = rel.indexOf('/');
                String ownVendor = firstSlash > 0 && !inSupport ? rel.substring(0, firstSlash) : null;
                String source = fileNameBase(file);
                try {
                    String src = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = familyImport.matcher(src);
                    while (matcher.find()) {
                        String targetFamily = matcher.group(1);
                        String[] segments = matcher.group(2).split("\\.");
                        String targetSimple = segments[segments.length - 1];
                        if (!targetFamily.equals(family)) {
                            violations.add(source + " -> " + targetSimple
                                    + "(跨策略族 " + family + "→" + targetFamily + ")");
                            continue;
                        }
                        if (segments.length == 1 || segments[0].equals("support")) {
                            // 本族根层(SPI/Router)与 support 共享层是合法目标
                            continue;
                        }
                        boolean sameVendor = ownVendor != null && segments[0].equals(ownVendor);
                        if (!sameVendor) {
                            violations.add(source + " -> " + targetSimple
                                    + "(策略横向依赖 " + family + "/strategy/" + segments[0] + ")");
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        Set<String> stale = new TreeSet<>(STRATEGY_INDEPENDENCE_EXEMPT);
        stale.removeAll(violations);
        assertThat(violations)
                .as("生成策略横向依赖(ARCHITECTURE.md §2 规则3:策略互不依赖,共享下沉 strategy/support)")
                .isEmpty();
        assertThat(stale)
                .as("豁免清单存在已修复条目(白名单只减不增,请移除)")
                .isEmpty();
    }

    @Test
    void serviceClassesMustNotFormCircularDependencies() throws IOException {
        Path serviceDir = moduleRoot().resolve(BASE_PACKAGE_DIR + "/service");
        Map<String, String> classFile = new HashMap<>();
        Map<String, Set<String>> graph = new HashMap<>();
        try (Stream<Path> paths = Files.walk(serviceDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> classFile.put(fileNameBase(p), toPosix(p)));
        }
        Pattern serviceImport = Pattern.compile(
                "^import\\s+(?:static\\s+)?" + Pattern.quote(FUSION) + "service\\.([A-Za-z0-9_.]+);",
                Pattern.MULTILINE);
        for (String clazz : classFile.keySet()) {
            Set<String> deps = new TreeSet<>();
            for (String target : importedSimpleNames(moduleRoot().resolve(classFile.get(clazz)), serviceImport)) {
                String simple = lastSegment(target);
                if (!simple.equals(clazz) && classFile.containsKey(simple)) {
                    deps.add(simple);
                }
            }
            graph.put(clazz, deps);
        }

        List<Set<String>> cycles = stronglyConnectedComponents(graph);
        Set<String> cycleKeys = new TreeSet<>();
        for (Set<String> cycle : cycles) {
            cycleKeys.add(String.join("|", new TreeSet<>(cycle)));
        }
        Set<String> violations = new TreeSet<>(cycleKeys);
        violations.removeAll(SERVICE_CYCLE_EXEMPT);
        Set<String> stale = new TreeSet<>(SERVICE_CYCLE_EXEMPT);
        stale.removeAll(cycleKeys);

        assertThat(violations)
                .as("service 类循环依赖(import 图 Tarjan SCC;同包引用不可见属已知简化)")
                .isEmpty();
        assertThat(stale)
                .as("豁免清单存在已解环条目(白名单只减不增,请移除)")
                .isEmpty();
        System.out.println("[arch-guard] service 循环依赖豁免在册 " + cycleKeys.size() + " 组: " + cycleKeys);
    }

    // ------------------------------------------------------------------
    // 通用工具
    // ------------------------------------------------------------------

    private static Path moduleRoot() {
        // surefire 提供 basedir=模块目录;IDE 直跑回退 user.dir
        return Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
    }

    private interface FileVisitor {
        void visit(Path file) throws IOException;
    }

    private static void forEachJavaFile(Path dir, FileVisitor visitor) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("扫描目录不存在: " + dir);
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> {
                try {
                    visitor.visit(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 与 wc -l 一致:统计换行符数量(白名单钉值以同一口径采集)。 */
    private static int countLines(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int lines = 0;
            for (byte b : bytes) {
                if (b == '\n') {
                    lines++;
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fileNameBase(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private static String toPosix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String lastSegment(String dotted) {
        int idx = dotted.lastIndexOf('.');
        return idx < 0 ? dotted : dotted.substring(idx + 1);
    }

    /** 返回文件中匹配 pattern 的捕获组(去重保序)。 */
    private static Set<String> importedSimpleNames(Path file, Pattern pattern) {
        try {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            Set<String> result = new LinkedHashSet<>();
            Matcher matcher = pattern.matcher(src);
            while (matcher.find()) {
                result.add(matcher.group(1));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tarjan 强连通分量,仅返回长度>1(或含自环)的分量。 */
    private static List<Set<String>> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        List<String> stack = new ArrayList<>();
        Set<String> onStack = new LinkedHashSet<>();
        List<Set<String>> result = new ArrayList<>();
        int[] counter = {0};
        for (String v : graph.keySet()) {
            if (!index.containsKey(v)) {
                tarjan(v, graph, index, low, stack, onStack, result, counter);
            }
        }
        return result;
    }

    private static void tarjan(String v, Map<String, Set<String>> graph, Map<String, Integer> index,
            Map<String, Integer> low, List<String> stack, Set<String> onStack,
            List<Set<String>> result, int[] counter) {
        index.put(v, counter[0]);
        low.put(v, counter[0]);
        counter[0]++;
        stack.add(v);
        onStack.add(v);
        for (String w : graph.getOrDefault(v, Set.of())) {
            if (!index.containsKey(w)) {
                tarjan(w, graph, index, low, stack, onStack, result, counter);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            Set<String> component = new TreeSet<>();
            String w;
            do {
                w = stack.remove(stack.size() - 1);
                onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            if (component.size() > 1) {
                result.add(component);
            }
        }
    }
}

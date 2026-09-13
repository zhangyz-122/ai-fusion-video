# PR-001～030 Gap Matrix

状态只使用 `NOT_STARTED`、`PARTIAL`、`IMPLEMENTED`、`VERIFIED`、`SUPERSEDED`、`BLOCKED`。`PARTIAL` 只表示发现相关底座，不表示核心验收通过。

本表初始版本随后由 [IMPLEMENTATION_REBASE_001.md](IMPLEMENTATION_REBASE_001.md) 更新；下列状态已同步当前 Production Vertical Slice、WAN E3 和 Run #5 故障恢复证据，P0 Golden 范围仍未完成。

| PR | 目标 | Status | Code Evidence | Test / Runtime Evidence | Missing / Next Action |
|---|---|---|---|---|---|
| PR-001 | Baseline / repository freeze | PARTIAL | Git/AGENTS/README 存在；无正式 P0 baseline | Git baseline 已采集；工作树 dirty | 建立可提交的 baseline 与变更边界 |
| PR-002 | Production contract freeze | IMPLEMENTED | `dev-docs/2-in-progress/20260913-production-run-take-selection/production-contract.md` | 本地编译通过 | 冻结内容已落地；后续可补 ADR 索引 |
| PR-003 | Run/Step/Take schema | IMPLEMENTED | Entity、Mapper、`V1.1.1.2.0__production_run_take_selection.sql` | Flyway 命名测试通过；DB validate 待执行 | 在可用数据库执行迁移验证 |
| PR-004 | Production orchestration/state machine | VERIFIED | `ProductionRunService`、状态迁移、reconcile、定时恢复 | Run #4/#5 真实完成三候选闭环；Run #5 含故障恢复 | 增加 Redis 重启后的恢复实测 |
| PR-005 | Storyboard production extension | IMPLEMENTED | nullable `selectedTakeId`；普通更新保留 Production-owned 值 | StoryboardServiceTests 通过 | 增加 API 集成验证 |
| PR-006 | ownership guards | VERIFIED | `StoryboardService.guardProductionOwnedFields`；普通 CRUD 保留当前选择，冲突写入拒绝；Agent 更新工具显式拒绝 `selectedTakeId` | 29 个后端回归测试通过；包含 Service 与 Agent guard 用例 | 已完成；Production `selectTake` 仍是唯一写入口 |
| PR-007 | WorkflowProfile | VERIFIED | `WorkflowProfile` 实体/Mapper/Service；仅引用既有 `ComfyUiWorkflow`，保存能力、输入输出契约、依赖和运行时元数据，不复制 graph | 31 个相关后端测试通过；Flyway `1.1.1.3.0` 已在本地 MySQL 应用并检查表存在 | 已完成；版本 eligibility/pin 由 PR-008 接管 |
| PR-008 | Profile resolver / pinned version | VERIFIED | Profile eligibility 校验；Production 启动固定 `workflowProfileId/workflowVersionId` 并写入 Step 快照；VideoConsumer 尊重已固定版本并拒绝 mismatch | 18 个 Profile/Production/Consumer/Workflow 回归测试通过；迁移 `1.1.1.4.0` 已应用，容器健康 | 已完成；WAN Profile 已完成本地登记 |
| PR-009 | WAN_I2V_STANDARD / WAN_FLF_STANDARD / WAN_INFINITETALK | VERIFIED | 三条 API artifact、bindings、dependency/model/runtime manifest、已发布 Workflow Version、canonical Workflow Profile 和视频模型均已登记；本地权重绑定修正为 `fp8_e4m3fn` | `RUN-WAN-I2V-STANDARD-001`、`RUN-WAN-FLF-STANDARD-001`、`RUN-WAN-INFINITETALK-001`：4090/ComfyUI 真实生成视频 | 进入 Production 三候选实跑；再提升正式分辨率/时长 |
| PR-010 | Gate B / existing queue integration | VERIFIED | `ProductionRunService.start` 调用既有 `VideoGenerationConsumer.submitTask`；单输出 workflow 通过 fan-out 绑定 3 个候选 | Run #4：Redis 入队、4090/ComfyUI 真实完成，VideoTask id=26 status=2 | Redis 重启后的恢复实测 |
| PR-011 | VideoItem→ProductionTake ingestion | VERIFIED | `reconcile` 固定 3 个结果并按 run/takeIndex 幂等写入 | Run #4：3 VideoItems、3 ProductionTakes 真实落库 | 增加异常恢复集成证据 |
| PR-012 | selection ownership | VERIFIED | QC PASS gate + `StoryboardItem.selectedTakeId` 单一真相 | Run #4：Take 1 PASS、Run SELECTED、StoryboardItem.selected_take_id=1 | 增加跨用户/API 集成验证 |
| PR-013 | selected Take→existing Compose | VERIFIED | selected Take 优先；无选择保持 Legacy；不完整候选显式失败 | Run #4 真实输出 HTTP 200 MP4；HEVC/AAC、960x544、15.08s | Legacy/Mixed 专项及时长 binding 一致性仍待补 |
| PR-014 | Production API/types | VERIFIED | Production API 启动、详情、reconcile、QC、select、compose | 真实登录会话完成全链路调用 | 增加跨用户/API 集成验证 |
| PR-015 | shot production status UI | VERIFIED | Storyboard 页面显示 Production Run 状态与失败信息 | 真实会话显示生成中、待质检、已选定 | 增加多分镜 overview |
| PR-016 | Take Drawer | VERIFIED | 三候选 Drawer：刷新、同步、QC、Select、Compose | 真实会话完成三候选、QC、Select、Compose | 视频审美 QC 仍需人工标准 |
| PR-017 | Production Overview | NOT_STARTED | 无 Production overview route | NOT_FOUND | Episode→原 StoryboardItem drill-down |
| PR-018 | QCResult/manual gate | VERIFIED | `QcResult` 独立实体/Mapper；`V1.1.1.6.0` 建表并回填历史候选；Production detail 返回独立质检记录；选择接口以 QCResult 为准，兼容旧 qc_status | 17 个 Production/Workflow/Queue 相关测试通过；Flyway `1.1.1.6.0` 已应用；现有 3 条候选已回填 QC 记录 | 自动技术质检指标与 evaluator 由 PR-020 完成 |
| PR-019 | Python QC sidecar | NOT_STARTED | 未发现 health/evaluate sidecar | NOT_FOUND | Python 只做媒体分析，不接管 Queue/SSOT |
| PR-020 | basic auto QC | VERIFIED | `ProductionTechnicalQcService` 复用 ffprobe 检查视频流、时长、尺寸、帧率、编码和音频信息；失败编码与结构化指标写入 QCResult；恢复/回收候选时自动执行 | 4 个 Technical QC fixture 测试通过；Run #5 三个真实 H3 输出均为 technical PASS | 技术 PASS 仍需人工审美复核 |
| PR-021 | StoryEvent store | NOT_STARTED | 无 StoryEvent/append-only API | NOT_FOUND | 建 append-only event ownership |
| PR-022 | state snapshot/replay | NOT_STARTED | 无 snapshot/hash/replay | NOT_FOUND | Event→Snapshot 可重建 |
| PR-023 | EpisodeContract/lint | NOT_STARTED | 无 EpisodeContract/lint | NOT_FOUND | 定义 input/output state 与 knowledge boundary |
| PR-024 | state refs→Storyboard context | NOT_STARTED | 无 stateRefs/continuity baseline | NOT_FOUND | 把 state refs 进入 Production Context |
| PR-025 | Director v0 | PARTIAL | 既有 script/storyboard agents；无 NarrativeFunction/CoverageGrammar/GenerationRisk | 既有 agent tests；无 Director contract | 只输出约束，不直接执行生成 |
| PR-026 | explainable router | PARTIAL | 既有 generation strategy router；无 profile eligibility/score/reason | 无 P0 router evidence | 增加 hard constraints→eligible→score，保留 decision reason |
| PR-027 | First Frame Gate | PARTIAL | Storyboard 有 first/last frame 字段和 frame API | 有 frame-related tests；无 task-creation block test | Gate FAIL 必须在 VideoTask 创建前阻断 |
| PR-028 | RepairRouter / RepairExecutor | VERIFIED | `ProductionRepairRouter` 按失败编码路由同工作流重试、切换工作流、人工复核或终止；`ProductionRepairAttempt` 记录父步骤、尝试序号、预算、原因、幂等键及替代 VideoTask；`ProductionRepairExecutor` 复制原任务并复用既有 `VideoGenerationConsumer`/Redis，成功后推进 Step 谱系；提供显式 `POST /api/production/runs/{runId}/repair` | 14 个 Repair/Production 专项测试通过（另有 4 个 Technical QC 测试）；Run #5 真实 ComfyUI 故障→`PLANNED`→显式 repair→替换 VideoTask #28→3/3 完成→3 Take/QC PASS→selectedTakeId=4→Compose HTTP 200；Flyway `1.1.1.9.0` 已应用 | Redis 重启恢复仍待实测；不自动执行重试 |
| PR-029 | usage/cost metrics | NOT_STARTED | 未发现 generation_usage/FPR/CPAS/GPAS/APSH | NOT_FOUND | 记录 run/step/take/provider/retry/cost |
| PR-030 | P0 Golden E2E | PARTIAL | `outputs/golden/P0_GOLDEN_FIXTURE_001.json` 已定义 12 镜头、36 候选下限和 10 个验收 Gate | Fixture 尚未执行；暂无 final MP4/production evidence | 实现有界 runner，执行 fixture 并发布 Golden Run Capsule |

## 状态计数

```text
NOT_STARTED  : 6
PARTIAL      : 6
IMPLEMENTED : 4
VERIFIED     : 14
SUPERSEDED   : 0
BLOCKED      : 0
```

## Gate Matrix

| Gate | PR | 状态 | 原因 |
|---|---|---|---|
| Gate A | PR-001/002 | PARTIAL | Contract 已冻结；baseline 仍是 dirty worktree |
| Gate B | PR-010 | VERIFIED | Run #4 真实经过 Redis、VideoGenerationConsumer、4090/ComfyUI 并完成 3 候选 |
| Gate C | PR-013 | VERIFIED | selected Take 真实进入既有 Compose 并生成可读 MP4；Legacy/Mixed 专项仍待补 |
| QC Gate | PR-018/020 | PARTIAL | 独立 QCResult/manual gate 已完成；auto evaluator 尚未完成 |
| Story Gate | PR-021/025 | BLOCKED | Event/Snapshot/Contract/Director contract 都不存在 |
| P0 Final | PR-030 | PARTIAL | 12-shot Golden fixture 和验收规则已定义；仍缺可执行 runner、final MP4 和 production evidence |

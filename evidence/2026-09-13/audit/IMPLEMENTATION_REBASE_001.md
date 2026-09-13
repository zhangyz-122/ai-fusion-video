# MASTER-AUDIT-001｜Implementation Rebase

日期：2026-09-13（Asia/Shanghai）

本文件在初始只读审计之后生成，现已补入 VERTICAL-SLICE-001 的真实数据库、Redis、4090/ComfyUI 与 MP4 证据。

## FIRST_ACTIONABLE_PR

初始审计结论为：`PR-002`。该项及其后续 Production Slice 已完成真实验证；`PR-006～008` 已完成；当前下一行动为 `PR-018`。PR-009/WAN Runtime 仍未完成。

已完成：`VERTICAL-SLICE-001` Run #4 真实三候选闭环；PR-015/016 的前端状态展示与 Take Drawer 已完成会话验收。

## 当前矩阵

| PR | 当前状态 | 证据 / 说明 |
|---|---|---|
| PR-001 | PARTIAL | 初始仓库基线已采集；工作树仍保留用户既有改动 |
| PR-002 | IMPLEMENTED | `dev-docs/2-in-progress/20260913-production-run-take-selection/production-contract.md` |
| PR-003 | IMPLEMENTED | ProductionRun/Step/Take Entity、Mapper、Flyway migration；命名测试通过 |
| PR-004 | IMPLEMENTED | `ProductionRunService` 状态编排、reconcile、失败记录与定时恢复 |
| PR-005 | IMPLEMENTED | `StoryboardItem.selectedTakeId` nullable projection |
| PR-006 | VERIFIED | `StoryboardService.guardProductionOwnedFields`；Storyboard CRUD/Agent 不能覆盖 `selectedTakeId`，冲突写入显式拒绝 |
| PR-007 | VERIFIED | `WorkflowProfile` 元数据层已落库；引用既有 ComfyUI Workflow，不复制 graph；Flyway `1.1.1.3.0` 已应用 |
| PR-008 | VERIFIED | Profile eligibility、Production Step 的 profile/version snapshot、Consumer 固定版本校验；Flyway `1.1.1.4.0` 已应用 |
| PR-009 | PARTIAL | 三条 WAN API artifacts 已抽取；当前 Runtime preflight 仍 BLOCKED |
| PR-010 | VERIFIED | Run #4 真实调用既有 RedisTaskQueue/VideoGenerationConsumer；VideoTask id=26，count=3，status=2 |
| PR-011 | VERIFIED | Run #4 真实生成 3 个 VideoItem（id=28/29/30），均有本地视频 URL；reconcile 生成 Take 1/2/3 |
| PR-012 | VERIFIED | Take 1 真实 QC PASS、select；Run #4=SELECTED，StoryboardItem.selected_take_id=1 |
| PR-013 | VERIFIED | 既有 Compose 真实读取 selected Take 并生成 MP4；HTTP 200、HEVC/AAC、960x544、15.08s |
| PR-014 | VERIFIED | 真实登录会话调用启动、详情、QC、select、compose API |
| PR-015 | VERIFIED | Storyboard 页面真实显示生成中、待质检、已选定状态 |
| PR-016 | VERIFIED | 三候选 Take Drawer 真实完成刷新、QC、Select、Compose |
| PR-017 | NOT_STARTED | 尚未接入 Production Overview |
| PR-018 | PARTIAL | 已有 QC 状态与 gate；独立 QCResult 审计模型尚未建模 |
| PR-019 | NOT_STARTED | 尚无 Python QC sidecar |
| PR-020 | NOT_STARTED | 尚无自动 QC evaluator/fixture |
| PR-021 | NOT_STARTED | 尚无 StoryEvent store |
| PR-022 | NOT_STARTED | 尚无 state snapshot/replay |
| PR-023 | NOT_STARTED | 尚无 EpisodeContract/lint |
| PR-024 | NOT_STARTED | 尚无 stateRefs continuity baseline |
| PR-025 | PARTIAL | 既有 agents；Director contract 尚未冻结 |
| PR-026 | PARTIAL | 既有 strategy router；尚无 explainable Profile routing |
| PR-027 | PARTIAL | Storyboard frame 字段存在；尚无统一 task-creation gate |
| PR-028 | NOT_STARTED | 尚无 RepairRouter |
| PR-029 | NOT_STARTED | 尚无 usage/cost metrics |
| PR-030 | BLOCKED | 依赖真实 Vertical Slice、Golden harness 与 final artifact |

## 本轮验证

- `mvn -DskipTests compile`：PASS
- `ProductionRunServiceTests`：4 PASS
- `VideoComposeServiceTests`：8 PASS
- `StoryboardServiceTests`：13 PASS
- `StoryboardOwnershipGuardTests`：1 PASS
- `VideoGenerationConsumerTests`：4 PASS
- `ComfyUiVideoStrategyTests`：3 PASS（含单输出工作流 fan-out 为 3 个候选）
- `FlywayMigrationNamingTests`：2 PASS
- 本轮关键集合：29 tests，0 failures，0 errors；此前关键集合 30 tests 同样通过

- Docker Backend：重建成功并健康运行
- MySQL：`production run take selection` migration 成功应用，三张 Production 表及 `selected_take_id` 已存在
- Redis：现有实例 `PONG`
- ComfyUI：`127.0.0.1:8188` 可达，当前版本 `0.33.0`

完整 `mvn test` 结果：509 tests 中 1 个既有 AgentScope 依赖契约失败，9 个 Spring 容器测试因本机 MySQL 未启动而无法初始化；其余测试通过。该失败不归因于本轮 Production 代码。

## VERTICAL-SLICE-001 真实证据

- Run #4：`ProductionRun=SELECTED`，StoryboardItem id=5 的 `selected_take_id=1`
- VideoTask id=26：`count=3`、`success_count=3`、`status=2`
- VideoItem id=28/29/30：3 个独立 Comfy prompt、3 个本地视频 URL，单个约 5.1 MB、3 秒
- ProductionTake id=1/2/3：Take 1=`PASS`，Take 2/3=`REVIEW_REQUIRED`
- 合成结果：`/media/videos/2e29aa8aee0c4bd883fe7206aefed7dd.mp4`，HTTP 200，HEVC + AAC，960×544，15.08 秒
- 失败样本也已保留：Run #3 证明旧单输出绑定会安全失败；修复后 Run #4 完成
- 已知边界：当前发布的 H3 Workflow Version 10 没有 `duration` input binding；Run #4 的 VideoTask 元数据为 3 秒，但媒体实际为 15.08 秒，时长一致性不能宣称 VERIFIED

## 尚未完成

- Redis 重启后的队列恢复实测
- WAN_I2V_STANDARD / WAN_FLF_STANDARD / WAN_INFINITETALK 的真实 Runtime E3
- PR-007 及后续 WorkflowProfile、QC evaluator、StoryEvent、Golden harness
- 当前 H3 发布工作流的 `duration` binding 与媒体时长一致性

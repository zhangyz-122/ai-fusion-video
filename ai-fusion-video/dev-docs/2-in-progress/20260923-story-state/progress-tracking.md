# 进度跟踪

## 已完成

- [x] `story-state-contract.md`：三层状态分离、事件形态、折叠语义、5 条提交不变量、non-goals
- [x] 迁移 `V1.1.1.10.0__story_state_event.sql`：`afv_story_event` + `afv_story_state_snapshot`，
      沿用仓库 DDL 约定（backtick、`longtext` 存 JSON、`deleted` 入唯一键、全量中文 COMMENT）
- [x] `StoryEvent` / `StoryStateSnapshot` 实体与 Mapper，继承 `BaseEntity`
- [x] `StoryStateService`：`commitShot`（幂等 + 契约门禁）、`state`（按 id 升序折叠，`@Cacheable`）、
      `snapshot`（快照复用与漂移拒绝）
- [x] `StoryStateController`：提交 / 查询状态 / 固化快照，全部走 `ProjectAccessGuard` + `requireCurrentUserId()`
- [x] 契约测试 8 项：未选定拒绝、QC 非 PASS 拒绝、同键幂等、派生 project/episode/幂等键、
      列表型与覆盖型主体互斥、后写覆盖前写、快照漂移拒绝覆盖、快照哈希一致时复用

## 关键设计落点

- 提交门禁强制契约第 1、3 条：镜头必须有 `selectedTakeId`，且该 Take 的 `QcResult.status = PASS`。
- 幂等键 `storyboardItemId:subjectType:subjectKey:changeKind` 由服务端派生，
  因此**同镜重选另一候选不会产生第二条剧情事件**（State Delta 属于镜头，不属于候选）。
- 取消选定不撤回剧情；更正只能追加更晚事件，`predecessor_event_id` 记更正链。
- 快照不是第二事实源：同一 `(project, episode, after_event)` 折叠出不同哈希时直接报错，
  不静默覆盖，避免重放逻辑漂移被吞掉。
- `episode_id` 用 `0` 表示项目级，避免 MySQL 唯一键落在 NULL 上。

## 验证记录

- 后端全量 `./mvnw test`：**957 项，0 失败，1 错误**；唯一错误仍是
  `ProjectWorkspaceCacheTests`（需 46379 Redis），与本需求无关
- Flyway 在真实 MySQL 8.0.43 上**增量**应用 `1.1.1.10.0` 成功，库版本推进至 `v1.1.1.10.0`
- 两张新表 0 个列缺中文 COMMENT，表注释已落库
- `StoryStateServiceTests` 8/8；`FlywayMigrationNamingTests` 2/2

## 未开始

- [ ] PR-023 分集契约与 lint（输入/输出状态、知识边界）
- [ ] PR-024 把 state refs 注入 Production Context 与生成提示词
- [ ] 选定流程是否自动触发剧情提交的决策（当前仅显式 API，不改 `ProductionRunService` 状态机）
- [ ] `ADD` 型主体的撤销与去重语义（当前 `RESOLVED` 只是追加一条，折叠端未做配对）
- [ ] 前端剧情状态展示

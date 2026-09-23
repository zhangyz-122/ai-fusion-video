# 进度跟踪

## 已完成

- [x] `story-state-contract.md`：三层状态分离、事件形态、折叠语义、5 条提交不变量、non-goals
- [x] 迁移 `V1.1.1.10.0__story_state_event.sql`：`afv_story_event` + `afv_story_state_snapshot`，
      沿用仓库 DDL 约定（backtick、`longtext` 存 JSON、`deleted` 入唯一键、全量中文 COMMENT）
- [x] `StoryEvent` / `StoryStateSnapshot` 实体与 Mapper，继承 `BaseEntity`
- [x] `StoryStateService`：`commitShot`（幂等 + 契约门禁）、`state`（按 id 升序折叠，`@Cacheable`）、
      `snapshot`（快照复用与漂移拒绝）
- [x] `StoryStateController`：提交 / 查询状态 / 固化快照，全部走 `ProjectAccessGuard` + `requireCurrentUserId()`
- [x] PR-023 迁移 `V1.1.1.11.0__episode_story_contract.sql`：`afv_episode_contract`
      （`(project_id, episode_id)` 唯一 + `revision` 递增）
- [x] `EpisodeContractService.define/current/lint`：5 类 lint 结果，全部只由已提交事件与契约声明比对得出
- [x] 契约接口：`PUT /contract`、`GET /contract`、`GET /contract/lint`
- [x] `EpisodeContractServiceTests` 7 项：无契约、输出未达成、引用未提交主体、契约达成放行、
      节拍匹配事件取值、悬念未闭合与未登记、修订号递增、非法 JSON 拒绝
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

- 后端全量 `./mvnw test`：**964 项，0 失败，1 错误**；唯一错误仍是
  `ProjectWorkspaceCacheTests`（需 46379 Redis），与本需求无关
- Flyway 在真实 MySQL 8.0.43 上连续增量应用 `1.1.1.10.0` 与 `1.1.1.11.0`，
  库版本推进至 `v1.1.1.11.0`
- 三张新表的表注释与列注释均已落库（缺中文注释列数为 0），
  `uk_episode_contract_scope` 唯一索引存在
- `StoryStateServiceTests` 8/8；`EpisodeContractServiceTests` 7/7；`FlywayMigrationNamingTests` 2/2

## 未开始

- [ ] PR-024 把 state refs 注入 Production Context 与生成提示词
- [ ] `input_state_json` 的 lint：需要先确定镜头顺序的真相源
- [ ] 选定流程是否自动触发剧情提交的决策（当前仅显式 API，不改 `ProductionRunService` 状态机）
- [ ] `ADD` 型主体的撤销与去重语义（当前 `RESOLVED` 只是追加一条，折叠端未做配对）
- [ ] 前端剧情状态与 lint 结果展示

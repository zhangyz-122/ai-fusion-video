# Story State Contract｜STORY-STATE-001

本契约面向 `PR-021 / PR-022`，只定义"剧情状态事实源"。
它采纳 `dev-docs/2-in-progress/20260913-universal-director/director-runtime-and-research.md`
里已经写明的镜头状态流转，不重新发明：

```text
PRE_STATE → 当前镜头声明的 State Delta → 生成 → QC → PASS 才 COMMIT；FAIL 则 ROLLBACK → POST_STATE 供下一镜继承
```

## 三层状态分离

```text
Story State      = 已经被剧情采纳的世界事实
Production State = 作品做到哪一步（afv_production_run / step）
Execution State  = 计算任务运行到哪一步（afv_video_task）
```

剧情状态只记录"衣服是否湿、剑是否出鞘、门是否开着、谁知道了哪个秘密"。
`CHARACTER_ID / COSTUME_REVISION / PROP_ID` 属于资产标识，进入的是素材与角色档案，
不得写进剧情状态，否则资产换版会被误判为剧情变化。

## Ownership

- `afv_story_event` 是剧情状态的唯一事实源，append-only，不修改、不物理删除。
- `afv_story_state_snapshot` 是事件折叠结果的可重建缓存，不是第二事实源。
- 事件的镜头归属通过 `storyboard_item_id` 表达；允许 `NULL`，用于人工登记的项目级事实。
- 项目隔离以 `project_id` 为准，`episode_id` 只做收窄查询，不作为权限边界。

## Event shape

一条事件只描述一个 subject 的一次变更：

| 字段 | 语义 |
| --- | --- |
| `subject_type` | `CHARACTER` / `PROP` / `LOCATION` / `FACT` / `OPEN_LOOP` |
| `subject_key` | 该项目内稳定的 subject 名，折叠时的键 |
| `change_kind` | `SET`（覆盖该 subject）或 `ADD`（`FACT`、`OPEN_LOOP` 这类列表型） |
| `value` | 变更后的值；`OPEN_LOOP` 关闭时用 `RESOLVED` |
| `predecessor_event_id` | 更正链，可空 |

折叠语义按 id 升序：`SET` 后写覆盖先写，`ADD` 追加。因此更正一条已提交事件的方式是
**追加一条更晚的事件**，而不是修改或撤回。

## Commit invariants

1. 只有该镜头已存在 `StoryboardItem.selectedTakeId`，且该 Take 的
   `QcResult.status = PASS` 时，才允许提交本镜声明的 State Delta。
2. `selectedTakeId` 变更但 State Delta 不变时，提交必须幂等：
   `idempotencyKey = storyboardItemId:subjectType:subjectKey:changeKind`，
   同键已存在即返回既有事件，不产生第二条。
3. 技术质检 FAIL 的 Take 不可能处于选定态（由 PR-012 保证），因此剧情层不再重复判定，
   但必须校验选定 Take 的 QC 结论确实为 PASS。
4. 取消选定不撤回已提交剧情；剧情一经提交，只能由更晚的事件更正。
5. `PRE_STATE` 必须是 `POST_STATE` 的前缀折叠结果：同一 `(project, episode)` 下，
   `replay()` 与 `snapshot` 折叠必须产出完全一致的状态。

## Episode contract (PR-023)

`afv_episode_contract` 按 `(project_id, episode_id)` 唯一，覆盖写入时 `revision` 递增。

状态声明的键统一写作 `TYPE:KEY`（例如 `CHARACTER:林川`），值为期望状态；
`required_beats` 与 `must_resolve` 是字符串数组。

lint 只做四类可由已提交事件判定的检查：

| 编码 | 判定 |
| --- | --- |
| `EPISODE_CONTRACT_MISSING` | 该分集没有契约，无法判定 |
| `STORY_UNKNOWN_SUBJECT` | 契约引用的主体从未产生过已提交事件 |
| `STORY_OUTPUT_STATE_MISMATCH` | 主体存在但当前值与契约期望不一致 |
| `STORY_REQUIRED_BEAT_MISSING` | 节拍关键词未出现在该集任何已提交事件的取值中 |
| `STORY_OPEN_LOOP_UNRESOLVED` | 要求闭合的悬念从未登记，或最后一条不是 `RESOLVED` |

`input_state_json` 本版未参与 lint，但**不是**因为缺排序依据：镜头顺序可由
`storyboard_id → storyboard_episode_id → storyboard_scene_id → sort_order → id` 确定
（与 P0 Golden fixture 的取镜规则一致）。缺口是"按顺序取第 N 镜之前的状态"这条规则
尚未在 lint 里实现，属于可以立刻补的下一项，而不是被阻塞项。

## Explicit non-goals

- 不做 knowledge boundary 的机器判定，字段先不建。
- 不做 PR-024：不把 state refs 注入 Production Context 或生成提示词。
- 不改动 `ProductionRunService` 的状态机与写路径；提交入口是独立 API，
  待 `PR-024` 决定是否由选定流程调用。
- 不做剧情时间线 UI，不引入第二套内容存储。

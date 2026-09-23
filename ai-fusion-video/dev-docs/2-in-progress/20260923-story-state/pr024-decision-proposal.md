# PR-024 决定提案（交给 Production 线负责人拍）

这份文件只为收敛决策，不含实现。三个问题按依赖顺序排列，第一个不定，后面都动不了。

## 先更正一条我记错的债

`technical-debt.md` 里我写过"`input_state_json` 缺镜头顺序真相源所以不 lint"。这条不成立：
`afv_storyboard_item` 已有 `storyboard_id / storyboard_episode_id / storyboard_scene_id /
sort_order / id`，且 `evidence/2026-09-13/golden/P0_GOLDEN_FIXTURE_001.json:35` 已经把
"按 storyboard, episode, scene, sort_order, id 取前 12 镜"写成规则。所以开拍基线校验可以做。

## 决定 1：镜头的 State Delta 声明存在哪里

| 方案 | 代价 | 判断 |
| --- | --- | --- |
| **A. 新表 `afv_story_shot_delta`**（镜头 1:N 声明，键与事件同构） | 多一张表和一个 Mapper | **推荐**。与事件表共用 `TYPE:KEY` 词汇，lint/幂等键直接复用 |
| B. `afv_storyboard_item` 加 JSON 列 | 改跨模块核心表 | 不推荐。分镜的普通更新与 Agent 工具都要开始守这个字段，PR-006 已为 `selectedTakeId` 做过一次 guard，会再加一次 |
| C. 复用 `custom_data` | 零迁移 | 不接受。无契约的字符串口袋，幂等键要从文本里刨，等于把剧情契约建在沙上 |

## 决定 2：谁触发剧情提交

| 方案 | 影响 | 判断 |
| --- | --- | --- |
| **A. `selectTake` 成功后同事务自动提交** | 触碰已 VERIFIED 的写路径 | **推荐**。镜头没有声明 delta 时是 no-op，对 Run #4/#5 那条闭环零影响；否则"绕过提交"的口子会一直开着 |
| B. 只保留显式 API（现状） | 零风险 | 可作为过渡，但不能作为终态：直接写 `selectedTakeId` 或走 Legacy 更新就能跳过剧情 |
| C. PR-024 只做读侧（注入 PRE_STATE），写侧推后 | 中 | 半套系统：状态能读但不会自然产生，样本期一长就得返工 |

选 A 的改造范围就三处：`commitShot` 增加"按镜头声明取 delta"的重载、`selectTake` 事务内调用、以及一条集成测试。

## 决定 3：PRE_STATE 以什么形式进入生成

`prompt` 前缀（可读、影响面小）还是结构化 context（要动 ComfyUI 参数绑定）。
我倾向前者起步：先只做人类可读的"上一镜继承状态"摘要，不进 binding，等 PR-026 有候选评分再谈结构化。

## 顺带请他们确认的三处矩阵与代码不一致

1. `PR-028` 标 VERIFIED，但 `SWITCH_WORKFLOW` 此前等价于同工作流重跑（已在 `06ac5f8` 改为显式阻断）。
2. `PR-017` 标 NOT_STARTED「无 Production overview route」，但 `app/(dashboard)/production/page.tsx` 存在。
3. `PR-026`「无 profile eligibility」与 `PR-007/008`「eligibility 已 VERIFIED」互相矛盾。

## Golden 留证补一条

`Run Capsule` 里存 `contract_hash`（以及事件折叠到的 `after_event_id`）。契约只有修订号、
不留历史内容，否则事后无法回答"当时按哪一版契约判定通过"。

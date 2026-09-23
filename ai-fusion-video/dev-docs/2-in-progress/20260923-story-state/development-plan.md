# Story State（PR-021 / PR-022 / PR-023）

## 目标

给剧情状态建立唯一事实源，并让它能被校验，解掉 `evidence/2026-09-13/audit/PR_GAP_MATRIX.md` 中
"Story Gate BLOCKED：Event/Snapshot/Contract 都不存在" 的前三项。

```text
镜头声明 State Delta
→ 该镜头已选定候选且 QcResult PASS
→ append afv_story_event
→ 按 id 升序折叠 = 当前剧情状态
→ 需要时固化 afv_story_state_snapshot（可重建、可校验哈希）
→ 用 afv_episode_contract 做 lint，判定本集是否达到剧情要求
```

设计与不变量见同目录 `story-state-contract.md`，本文件只记范围。

## 范围

- 三张新表 + 实体/Mapper + `StoryStateService` + `EpisodeContractService` + 接口。
- 提交入口独立，不改 `ProductionRunService` 状态机与写路径。

## 明确不做

- PR-024 把 state refs 注入 Production Context。
- `input_state_json` 的 lint（缺镜头顺序真相源，见契约文档）。
- 剧情时间线 UI、角色身份与资产版本（属于素材层）。
- 事件的修改与物理删除。

# ADR-002: Production Domain Contract

## 状态

Accepted

## 背景

我们需要在融光现有 Storyboard→Generate→Compose 主链上叠加 Production Layer（可编排、可多 Take、可 QC/Repair、可恢复、可追溯成本），同时防止架构膨胀。

## 决策

### SSOT 声明

| 概念 | SSOT | 禁止 |
|---|---|---|
| 分镜内容 | StoryboardItem | 另建 ShotSpec 主表 |
| 视频生成 | VideoTask + VideoGenerationConsumer | 第二套生成队列 |
| 图片生成 | ImageGenerationConsumer | 第二套图片队列 |
| ComfyUI 执行 | ComfyUiWorkflowService + Version | Python ComfyUI Client |
| 视频合成 | VideoComposeService | 重写 Timeline/Compose |
| Take 选择 | StoryboardItem.selectedTakeId | Take 表存 selected 布尔 |
| 故事状态 | Story Event Store (append-only) | LLM 记忆 |

### Production 实体边界

```text
afv_production_run     一次批量生产操作
afv_production_step    单镜头单步骤执行（引用 execution_ref）
afv_production_take    一次生成的产出（引用 VideoItem/ImageItem）
afv_qc_result          QC 结果（可阻断 select/compose）
afv_workflow_profile   生产语义配置（引用 ComfyUiWorkflow Version）
story_event            故事状态事件（append-only）
story_state_snapshot   状态快照（Event 重放）
episode_contract       集契约（状态边界声明）
afv_generation_usage   生成成本记录
```

### 实体关系

```text
ProductionRun 1:N ProductionStep
ProductionRun 1:N ProductionTake
StoryboardItem 1:N ProductionStep (via storyboard_item_id)
StoryboardItem 1:N ProductionTake (via storyboard_item_id)
StoryboardItem.selectedTakeId → ProductionTake.id (unique)
ProductionStep.execution_ref_id → VideoTask.id / ImageTask.id / QC job
VideoTask 1:N VideoItem 1:N ProductionTake
WorkflowProfile → ComfyUiWorkflowVersion (pin, 不保存 graph)
```

### 状态机

Production Step:
```text
PENDING → READY → QUEUED → RUNNING → SUCCEEDED | FAILED | RETRYING
                                    → BLOCKED | REVIEW_REQUIRED → APPROVED
                                    → CANCELLED
```

非法迁移在 Service 层拒绝（抛异常），数据库层只做最终兜底。

### 幂等

- ProductionStep 幂等键：`run_id + storyboard_item_id + step_type + attempt`
- VideoTask 已有幂等（同 task 不重复提交）
- ProductionTake ingest 幂等：`video_item_id` 唯一约束
- 付费操作（云模型调用）必须有 idempotency_key

### 禁止事项

1. 禁止 Python 重写 ComfyUI Runtime
2. 禁止第二套 Shot/生成队列
3. 禁止修改历史 Flyway migration
4. 禁止 ProductionStep 直接操作 ComfyUI API（必须通过 VideoTask）
5. 禁止 LLM 作为故事状态 SSOT
6. 禁止 ProductionTake 保存 selected=true
7. 禁止绕开 Run/Step/Take 直接调用 ComfyUI

## 后果

- 后续所有 PR 必须引用本契约（production-contract.md）
- 违反上述禁止事项的 PR 会被拒绝合并
- 新增概念需要新 ADR

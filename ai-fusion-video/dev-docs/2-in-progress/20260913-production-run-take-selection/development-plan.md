# Production Run / Take / Select

## 目标

在不替换现有视频任务链的前提下，建立第一条可追踪的分镜生产闭环：

```text
StoryboardItem
→ ProductionRun
→ ProductionStep
→ VideoTask(count=3)
→ RedisTaskQueue / VideoGenerationConsumer
→ VideoItem
→ ProductionTake
→ QC
→ selectedTakeId
→ VideoComposeService
```

## 实施范围

1. 增加 ProductionRun、ProductionStep、ProductionTake 持久化模型。
2. 使用唯一幂等键保护生产启动。
3. 固定第一版生成数量为 3，复用现有 VideoTask 和 RedisTaskQueue。
4. 将 VideoItem 幂等同步为 ProductionTake。
5. 支持人工 QC、唯一 Select、Legacy/Mixed Compose。
6. 为后续 Runtime、Workflow、Director Compiler 保留 provenance 字段。

## 后续阶段

- 将 `workflowProfileId`、`runtimeRevision` 和模型快照接入 ProductionStep。
- 增加真实 ComfyUI Run Capsule 关联。
- 完成 VERTICAL-SLICE-001 的数据库、队列、Runtime 和最终 MP4 验收。

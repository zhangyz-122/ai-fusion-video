# Production Usage 观测（PR-029）

## 目标

在不改动 Production 状态机、不新增写入路径的前提下，提供生产用量的只读观测口径，
为 PR-030 的 12 镜头 Golden harness 提供可直接断言的分子与分母。

```text
afv_production_run / step / take
+ afv_qc_result
+ afv_production_repair_attempt
+ afv_video_task
        ↓ 只读派生
ProductionRunUsage / ProductionShotUsage
        ↓
GET /api/production/runs/{runId}/usage
GET /api/production/shots/{storyboardItemId}/usage
```

## 设计约束

- 复用既有表，不建 `afv_generation_usage`：候选数、尝试数、修复次数、质检分组、
  结构化指标数与生成耗时全部可从现有行派生，新增写表会制造第二份真相。
- 不在 `ProductionRunService` 的 start/reconcile/repair/select 中埋点：该状态机
  已由 Run #4/#5 真实闭环 VERIFIED 并有测试覆盖，写路径插桩的风险大于收益。
- 状态一律按实际取值分组返回（`technicalStatusCounts`、`qcStatusCounts`），
  不在观测层固化通过率/成本等指标口径。
- `provider cost` 无数据源（视频生成侧仅有 no-billing 结算适配器），因此不预留空列。
- 生成耗时只报告"最早步骤提交 → 最晚候选入库"的实测间隔，任一端时间缺失即为 null。

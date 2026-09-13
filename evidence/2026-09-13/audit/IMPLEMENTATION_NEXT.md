# Implementation Next｜FIRST_ACTIONABLE_PR

```text
FIRST_ACTIONABLE_PR = PR-030
```

理由：PR-028 的真实故障恢复闭环已在 Run #5 验证；PR-009 的三条 WAN 能力、Profile/模型登记和 Runtime E3 均已完成，当前进入 P0 Golden harness 收口。

## 下一序列

```text
PR-030  P0 Golden harness（12-shot production evidence）
```

Vertical Slice 001 已完成真实 Run #4：

```text
StoryboardItem → ProductionRun/Step → Existing VideoTask(count=3)
```

```text
Run #4
→ VideoTask id=26 / count=3 / success_count=3
→ VideoItem id=28/29/30
→ ProductionTake id=1/2/3
→ Take 1 QC PASS / selected_take_id=1
→ 独立 QcResult：Take 1 PASS，Take 2/3 REVIEW_REQUIRED
→ Existing Compose → HTTP 200 MP4
```

仍待补：Redis 重启恢复实测、WAN I2V/FLF 正式素材 E3、Legacy/Mixed 专项真实回归；InfiniteTalk 已通过短片 E3 smoke；当前 H3 发布版本还需补 `duration` binding，避免任务元数据与媒体实际时长漂移。

## 当前可复用资产

- `StoryboardItem`、`VideoTask`、`VideoItem`
- `RedisTaskQueue`、`VideoGenerationConsumer`
- `ComfyUiWorkflowService`、`ComfyUiWorkflowVersion`
- `ComfyUiGenerationExecutor`、ComfyUI Image/Video Strategy
- `VideoComposeService` 的 Legacy Compose 和 FFmpeg 安全处理

## 本轮验证结果

| 验证 | 结果 |
|---|---|
| Git baseline/status | PASS（记录完成；工作树保持原样） |
| RT01 | PASS |
| RT02 | PASS（按预期结构化拒绝） |
| RT03 | PASS |
| RT04 | PASS（按预期结构化拒绝） |
| WAN I2V workflow | PASS（Run-WAN-I2V-STANDARD-001：真实输入→H.264 MP4） |
| WAN FLF workflow | PASS（Run-WAN-FLF-STANDARD-001：首尾帧真实输入→H.264 MP4） |
| WAN canonical registry | PASS（三个 Workflow、已发布 Version、Profile、视频模型均已绑定） |
| WAN InfiniteTalk local smoke | PASS（本地 wav2vec2、Wan stack、MP4 输出） |
| Java test | PASS（JDK 21；本轮 Repair/QC/Production 聚焦测试 18 项） |
| QCResult migration | PASS（`V1.1.1.6.0`，现有 3 条候选完成回填） |
| Technical QC migration | PASS（`V1.1.1.7.0`，技术质检字段已启用） |
| Technical QC focused tests | PASS（4 项，包含有效 H3/SeedVR2 输出结构、无音频和尺寸边界 fixture） |
| RepairRouter focused tests | PASS（14 项，覆盖路由、预算、阻断和幂等谱系） |
| RepairExecutor focused tests | PASS（3 项，覆盖复制提交、提交失败留痕、重复计划阻断） |
| Repair execution migration | PASS（`V1.1.1.9.0`，后端启动时成功应用并保持 healthy） |
| Frontend typecheck | PASS（使用现有 TypeScript 二进制；pnpm 依赖脚本未重新安装） |
| Vertical Slice 001 | PASS（Run #4 真实三候选、QC、Select、Compose） |
| Repair failure recovery E2E | PASS（Run #5：ComfyUI 故障→RepairExecutor→3/3→QC→Select→Compose） |

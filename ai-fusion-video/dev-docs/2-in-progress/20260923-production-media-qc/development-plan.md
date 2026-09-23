# Production 媒体质检增强（PR-020 补充）

## 目标

在既有自动技术质检上补两项客观帧信号：**画面是否完全静止**、**画面是否全黑/全白**。
这两项是 AI 视频生成的典型硬失败，不属于审美判断，因此归技术质检而不是人工复核。

```text
ProductionTechnicalQcService.inspect
  → ffprobe 流属性校验 validateProbe          （既有）
  → ProductionMediaAnalysisService.measure     （新增：ffmpeg 抽 5 帧灰度图）
  → ProductionMediaAnalysisService.judge       （新增：纯函数判定）
  → QcResult.technicalStatus / technicalFailureCode / technicalMetricsJson
```

## 设计约束

- **不新增表、不新增列、不加迁移**：结论写进 `QcResult` 已有的 `technical_*` 字段，
  帧信号写进 `technicalMetricsJson`（`mediaSampledFrames` / `mediaMotionScore` /
  `mediaBrightness` / `mediaCheck`）。
- **测量与判定分离**：`measure` 依赖 ffmpeg 进程，`judge` 是纯函数；
  与仓库里把 `validateProbe` 做成包级可见以便 fixture 测试的做法一致。
- **测不出来就保持原判定**：ffmpeg 缺失、解码失败、可解码帧不足 2 帧时返回
  `mediaCheck=UNAVAILABLE` 且不改 status，不把未测量当作通过或失败。
- **阈值刻意保守**：只有逐像素平均差恰为 0（完全静止）才判静止；
  平均灰度 ≤ 2 或 ≥ 253 才判无内容。低运动、轻微欠曝一律交给人工复核。
- 原 `ai-drama-qc` Python sidecar 的 opencv 算法改为 Java + `ImageIO` 实现，不引入新依赖。
  这也避开了一条仍然有效的仓库约束——
  `dev-docs/1-todo/2026-09-13-页面整改与短剧制造平台IA.md` 写着
  "Production Worker：现有 Consumer + Reaper，禁止再起 Python Worker"。

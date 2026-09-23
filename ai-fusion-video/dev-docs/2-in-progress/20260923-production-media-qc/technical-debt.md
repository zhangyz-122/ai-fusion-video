# 技术债与已知限制

- **阈值未标定**：`meanMotion == 0`（完全静止）与平均灰度 `≤ 2 / ≥ 253`（全黑全白）
  是刻意保守的起点值，没有在真实 4090 / WAN 输出集上标定。标定后应移入配置项，
  目前复用 `video.compose.ffmpeg-path` 与 `video.technical-qc.timeout-seconds`。
- **启发式而非语义质检**：只能发现"不动""没有可见内容"，发现不了换脸、口型、
  连续性漂移等；契约原文仍然成立——技术 PASS 不等于可选定，人工审美复核不可省。
- **每次技术质检多一个 ffmpeg 进程**：抽 5 帧、宽 160 的灰度图，开销小但非零；
  `ProductionRunReconcileScheduler` 批量回收时未做并发上限，候选量大需观察耗时。
- **跨帧比较按较短像素数组对齐**：正常输出分辨率固定不会触发；若将来某个工作流
  输出可变分辨率，运动信号会退化为部分像素比较而不会报错。
- **真实帧测试依赖本机 ffmpeg**：缺失时以 `Assumptions` 跳过而非放行；
  CI 环境若无 ffmpeg，这两条路径不会被覆盖。
- **新失败码未验证前端展示**：`VIDEO_MOTION_STATIC`、`VIDEO_NO_VISIBLE_CONTENT`
  会进入 `technicalFailureCode`，但 Production 前端是否原样展示未知。

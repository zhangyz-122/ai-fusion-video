# 进度跟踪

## 已完成

- [x] `ProductionMediaAnalysisService`：ffmpeg 抽 5 帧灰度图 → 逐像素求平均灰度与相邻帧平均差
- [x] `measure`（进程相关）与 `judge`（纯函数）分离，`metrics` 只输出已测得的量
- [x] 判定接入 `ProductionTechnicalQcService.inspect`：流属性 PASS 之后才看帧信号，
      静止/无内容降级为 `technicalStatus=FAIL` + 新失败码
- [x] `withMediaSignals` 开放为包级可见，与既有 `validateProbe` 的测试方式一致
- [x] `ProductionTechnicalQcServiceTests` 两处构造点补齐新依赖，4 项原断言全部保持
- [x] 新增 `ProductionMediaAnalysisServiceTests` 9 项：
      静止判定、全黑/全白判定、正常信号放行、未测到不改判、指标只记已知量、
      真实 testsrc 片段测出运动>0 且曝光正常、静止灰度片段判为静止、
      静态片段把流 PASS 降级为媒体 FAIL、文件不可读时保留流 PASS

## 验证记录

- 后端全量 `./mvnw test`：**949 项，0 失败，1 错误**；唯一错误是
  `ProjectWorkspaceCacheTests`（依赖 46379 Redis，本机未启动），改动前即存在
- production 包聚焦：48 项通过（含既有 Run/Scheduler/Repair/QC/Readiness/Usage 全部）
- 真实媒体路径在本机用 ffmpeg 9.0 生成的 `testsrc` 与 `color=gray` 片段实测通过

## 未开始

- [ ] 阈值标定：需要一批真实 WAN 候选输出做静止/欠曝样本回归
- [ ] 新失败码在前端 QC 面板的展示与文案
- [ ] 更多帧级信号（清晰度骤降、黑帧占比、音画长度一致性）

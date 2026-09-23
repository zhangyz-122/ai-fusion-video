# 进度跟踪

## 已完成

- [x] `ProductionRunUsage` / `ProductionShotUsage` 观测视图（`@Data @Builder`，与既有 VO 一致）
- [x] `ProductionUsageService` 只读聚合：候选数、步骤数与最大尝试、按取值分组的质检结论、
      结构化技术指标记录数、修复尝试数、选定候选的 QC/技术结论、生成耗时
- [x] 运行归属校验复用 `requireOwnedRun` 语义（非本人 Run 返回 404，不泄露存在性）
- [x] 镜头维度无 Run 时直接返回零值，不再查询 take/qc/repair 子表
- [x] `ProductionController` 两个读接口，沿用 `ProjectAccessGuard` + `requireCurrentUserId()`
- [x] `ProductionUsageServiceTests` 6 项单测通过（聚合、耗时缺失、耗时计算、越权、空镜头、跨 Run 汇总）
- [x] 后端全量回归：940 项 0 失败，唯一错误为 `ProjectWorkspaceCacheTests`（依赖 46379 Redis，本机未启动）
- [x] 空库 Flyway 实测：应用本分支 11 条迁移至 `v1.1.1.9.0` 成功，`contextLoads` 通过

## 未开始

- [ ] 指标口径定义：FPR / CPAS / GPAS / APSH 在 `PR_GAP_MATRIX` 中仅有名称、无计算定义
- [ ] provider / GPU 成本采集：需要成本数据源与结算入口
- [ ] 前端用量面板：PR-029 只要求记录与观测，UI 未列入本需求
- [ ] PR-030 Golden runner 对两个 usage 接口的实际消费

## 验证记录

- JDK 21（Temurin 21.0.12）；`./mvnw clean test` 全量 940 项，0 失败 / 1 环境性错误
- `ProductionUsageServiceTests` 单独执行 6/6 通过

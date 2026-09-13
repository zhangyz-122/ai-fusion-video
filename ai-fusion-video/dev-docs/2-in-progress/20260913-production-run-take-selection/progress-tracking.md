# 进度跟踪

## 已完成

- [x] ProductionRun / ProductionStep / ProductionTake 实体和 Mapper
- [x] Flyway 迁移：运行、步骤、候选视频、`selected_take_id`
- [x] 幂等启动接口，固定 `VideoTask.count = 3`
- [x] 复用现有 `VideoGenerationConsumer.submitTask`
- [x] VideoTask 结果同步为 ProductionTake
- [x] QC PASS / FAIL / REVIEW_REQUIRED
- [x] 唯一 `StoryboardItem.selectedTakeId`
- [x] Existing Compose 优先读取 selected Take，Legacy 无选择时保持原行为
- [x] 自动同步等待中的 ProductionRun
- [x] Production Contract 冻结 ownership、state、idempotency 和 Legacy/Mixed 语义
- [x] JDK 21 环境恢复，Maven 编译通过
- [x] ProductionRun、VideoCompose、Storyboard、VideoConsumer、ComfyUI Strategy、Flyway 命名关键测试通过（此前 30 + 本轮 15 tests）
- [x] Docker Backend 重建并启动，MySQL 实际应用 Production migration，Backend health 正常
- [x] Storyboard 前端接入三候选 Production Drawer、状态刷新、QC、Select、Compose
- [x] Frontend production build 通过并完成 Docker 容器重建
- [x] VERTICAL-SLICE-001 Run #4：真实 Redis/4090/ComfyUI 生成 3 个候选
- [x] Run #4：3 VideoItems → 3 ProductionTakes，Take 1 QC PASS 并完成 selectedTakeId
- [x] Run #4：Existing Compose 生成可读取的 HEVC/AAC MP4
- [x] PR-006 ownership guard：普通 Storyboard CRUD 保留 selectedTakeId，冲突写入拒绝；Agent 更新工具显式拒绝
- [x] PR-007 WorkflowProfile 元数据：引用既有 Workflow，不复制 graph；迁移已应用到本地 MySQL
- [x] PR-008 Profile eligibility 与 pinned WorkflowVersion：ProductionStep 快照、Consumer mismatch guard；迁移已应用

## 待完成

- [x] 在现有 MySQL 上完成 Flyway migration 应用并检查三张 Production 表与 `selected_take_id`
- [ ] 启动项目 MySQL 后重跑 Spring 容器测试；另行处理既有 AgentScope 过时符号契约失败
- [x] 将当前 Wan Workflow Profile 接入启动请求和 Step 快照
- [ ] 独立 QCResult 审计模型（下一项：PR-018）
- [x] 在真实登录会话中选择一个 storyboardItem，执行 VERTICAL-SLICE-001 三候选闭环
- [x] 增加真实 VideoTask → 3 VideoItems → 3 ProductionTakes 的集成证据
- [ ] 实测 Redis 重启后的队列恢复

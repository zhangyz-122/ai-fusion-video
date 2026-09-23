# 技术债与已知限制

- **指标口径未定义**：`FPR / CPAS / GPAS / APSH` 只在 `evidence/2026-09-13/audit/PR_GAP_MATRIX.md`
  的 PR-029 行出现名称，全仓没有计算定义。本需求只返回按取值分组的原始计数，
  比率由调用方在口径冻结后自行计算，避免在观测层写死一套未确认的算法。
- **无成本数据源**：视频生成链路没有 provider/GPU 成本字段，仅存在
  `AuditLedgerModelUsageSettlementAdapter` 这个 no-billing 适配器。因此本需求
  没有为 `providerCost`/`gpuMillis` 预留空列；补成本需要先定接入点。
- **生成耗时是近似值**：`generationMillis` 取"最早步骤创建 → 最晚候选创建"，
  其中包含队列等待，无法拆分排队与实际渲染；要精确需由 Consumer 或 ComfyUI 侧上报分段耗时。
- **观测接口未加缓存**：Run 进行中时每次轮询结果都在变化，缓存会返回过期进度；
  `ProductionRunService` 本身也未使用 `@Cacheable`，此处保持一致。
- **只有单元测试**：本需求为只读派生，`ProductionUsageServiceTests` 使用 Mockito 桩；
  跨表 SQL 的真实行为未做数据库级集成验证。
- **切分支必须 clean（实测踩到）**：`target/classes/db/migration` 会残留另一条线的迁移脚本。
  本地从 `feat/ai-drama-os-pr001-baseline` 切到本分支后未 `clean` 直接 `test`，
  Flyway 加载到残留的 `V1.1.2.0.1__storyboard_item_production_fields.sql`，
  因 `Duplicate column name 'selected_take_id'` 导致 8 个上下文测试失败；
  `./mvnw clean` 后全绿。两条 AI Drama OS 实现线的迁移脚本互不兼容，切换时务必清理构建产物。

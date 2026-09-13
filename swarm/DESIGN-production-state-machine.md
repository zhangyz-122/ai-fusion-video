# DESIGN:Production 状态机 enum 化(SW-T16 设计稿,本文只设计不实现)

作者:Architect(Agent-1),Round 2,2026-09-14。对应 BACKLOG SW-T16(Dev-A 执行)。
契约基线:ARCHITECTURE.md §4.2/§5(「状态字符串 ProductionRunService 常量」为已知债务)。
硬约束:**既有行为与 API/DB 字符串值零变更**;本冲刺禁止数据库迁移,enum name 必须逐一对齐存量列值。

---

## 1. 现状盘点(2026-09-13 基点 sprint/universal-director-integration 实测)

四套独立状态族,全部是散落的 `String` 常量 + 字面量,无合法迁移表,无编译期检查。

### 1.1 Run 状态(`afv_production_run.status`,实体 `ProductionRun.status:String`)

| 值 | 常量定义处 | 写入处 |
|---|---|---|
| `CREATED` | ProductionRunService `RUN_CREATED`(public) | start 建单 |
| `WAITING_GENERATION` | `RUN_WAITING_GENERATION` | submit 成功;修复重提(resubmit) |
| `QC_PENDING` | `RUN_QC_PENDING` | reconcile 成功(未 SELECTED 时);人工 QC 更新后 |
| `SELECTED` | `RUN_SELECTED` | 选片成功(终态,reconcile 不降级) |
| `FAILED` | `RUN_FAILED` | submit 失败;reconcile 数量≠3(TAKE_COUNT_MISMATCH);修复提交失败 |

现状问题:常量宿主是 1100+ 行的 ProductionRunService,RepairRouter/Executor、TechnicalQcService、ReconcileScheduler、GenerationTaskReaper 各自 import 或复写;迁移合法性只靠 if 散文(如 `!RUN_SELECTED.equals(run.getStatus())`),无集中校验。

### 1.2 Step 状态(`afv_production_step.status`,实体 `ProductionStep.status:String`;stepType 固定 `GENERATE_VIDEO`)

`CREATED / SUBMITTED / SUCCEEDED / FAILED` — 常量全部 private 在 ProductionRunService(`STEP_*`),导致 **ProductionRepairExecutor:73 出现裸字符串 `step.setStatus("SUBMITTED")`**(同类不同源,重命名必漏)。

### 1.3 QC 状态(`afv_qc_result.status` + `afv_production_take.qc_status` 双写兼容)

`PASS / FAIL / REVIEW_REQUIRED` — 三处定义:QcResult(public `PASS/FAIL/REVIEW_REQUIRED`)、ProductionRunService(private `QC_*`)、`ProductionTake.qcStatus` 字段初始化器裸字符串 `"REVIEW_REQUIRED"`。`normalizeQcStatus` 在 service 内做输入归一/校验;`technicalStatus`/`technicalFailureCode` 为随行字段(失败码如 `TAKE_COUNT_MISMATCH`、`VIDEO_QC_EXCEPTION`,不在本设计范围)。

### 1.4 修复 Attempt(`afv_production_repair_attempt.status`,实体 `ProductionRepairAttempt.status:String`)

- 状态:`PLANNED / BLOCKED`(Router 创建)、`SUBMITTED / FAILED`(Executor 执行)。
- 动作路由(非状态,同产):`RETRY_SAME_WORKFLOW / SWITCH_WORKFLOW / MANUAL_REVIEW / TERMINATE`(ProductionRepairRouter public 常量)。

### 1.5 前端镜像(改动必须同步,否则 UI 状态渲染空白)

- `ai-fusion-video-web/lib/api/production.ts`:`ProductionRunStatus`、`ProductionQcStatus` union type;`ProductionStep.status/stepType` 为裸 `string`(弱类型,无 union)。
- `ai-fusion-video-web/app/(dashboard)/production/_components/production-status.ts`:label/配色两张 map 按 字符串键 重复枚举。

### 1.6 相邻发现(独立处理,不并入本任务)

`VideoComposeService` 合成状态是 **int 魔法值** `STATUS_IDLE=0/RUNNING=1/DONE=2/FAILED=3`,写入 `StoryboardEpisode` 状态字段——属另一套状态机,建议以本设计同套路独立小任务处理(见 BACKLOG SW-T19)。

---

## 2. 目标设计

### 2.1 类型落点

新包 `com.stonewu.fusion.service.production.state`,四个 enum + 一个迁移策略:

```
ProductionRunStatus     { CREATED, WAITING_GENERATION, QC_PENDING, SELECTED, FAILED }
ProductionStepStatus    { CREATED, SUBMITTED, SUCCEEDED, FAILED }
ProductionQcStatus      { PASS, FAIL, REVIEW_REQUIRED }
ProductionRepairStatus  { PLANNED, BLOCKED, SUBMITTED, FAILED }
ProductionRepairRoute   { RETRY_SAME_WORKFLOW, SWITCH_WORKFLOW, MANUAL_REVIEW, TERMINATE }
```

**enum name == 存量 DB 字符串值**,逐条与 §1 清单核对;`getValue()` 即 `name()`,`fromValue(String)` 静态解析。
`ProductionRunStatus` 另携带语义标记:`terminal()`(SELECTED)、`revivable()`(FAILED,可被修复复活)、`initial()`(CREATED)。

### 2.2 持久化策略(关键取舍)

**实体字段保持 `String`,enum 不直接进实体。** 理由:
1. MyBatis-Plus 换 enum 字段需 `@EnumValue`/TypeHandler 并波及全部 LambdaQueryWrapper 构造点,改动面与「行为不变」红线冲突;
2. 本冲刺禁迁移,DB 列仍为 varchar,enum 存取零序列化风险;
3. 服务层内部全部以 enum 表达,跨层边界(实体/VO)读写统一走 `fromValue()/getValue()`。

**API 出入参(VO)保持 String**,JSON 值不变;controller 层入参归一继续由 service 的 normalize 承担,实现改为 `ProductionQcStatus.fromValue(...)` 抛 `IllegalArgumentException`(全局异常处理器已将参数类错误映射 400,与现状 `normalizeQcStatus` 行为对齐,需在实现时以现有用例回归确认)。

### 2.3 合法迁移表(由现状代码行为反推,实现时逐边对照源码)

`ProductionRunStatus.canTransitionTo(target)`(self 迁移仅 QC_PENDING 允许——人工 QC 重复更新):

| from \ to | CREATED | WAITING_GENERATION | QC_PENDING | SELECTED | FAILED |
|---|---|---|---|---|---|
| CREATED | – | ✓(submit) | | | ✓(submit 失败) |
| WAITING_GENERATION | | – | ✓(reconcile 成功) | | ✓(reconcile≠3 / 失败) |
| QC_PENDING | | ✓(修复重提) | ✓(QC 更新) | ✓(选片) | ✓(修复提交失败) |
| SELECTED | | | | – | |
| FAILED | | ✓(修复复活) | | | – |

`ProductionStepStatus`:CREATED→SUBMITTED;SUBMITTED→{SUCCEEDED, FAILED, SUBMITTED(修复重提)};FAILED→SUBMITTED(修复);SUCCEEDED 终态。
`ProductionQcStatus`:**无迁移限制**(人工改判 PASS↔FAIL↔REVIEW_REQUIRED 是业务允许的),仅作值枚举。
`ProductionRepairStatus`:PLANNED→{SUBMITTED, FAILED};SUBMITTED→FAILED(提交异常);BLOCKED、FAILED 终态(FAILED 由 reconcile 重新规划新 attempt,不复用旧行)。

### 2.4 写路径接入方式

实体不迁 enum 的替代锚点:**在 ProductionRunService 内聚一个私有守卫方法**

```java
private void transitionRun(ProductionRun run, ProductionRunStatus target) {
    ProductionRunStatus current = ProductionRunStatus.fromValue(run.getStatus());
    if (!current.canTransitionTo(target)) {
        throw new IllegalStateException("非法生产运行状态迁移 runId=%d %s→%s".formatted(run.getId(), current, target));
    }
    run.setStatus(target.getValue());
}
```

所有 `run.setStatus(RUN_*)` 调用点替换为 `transitionRun(run, ...)`;非法迁移抛错并打 WARN 日志(含 runId/from/to),终态语义由此集中可见。SELECTED 的「reconcile 不降级」特判(现状 `if (!RUN_SELECTED.equals(...))`)保留在调用处,由迁移表兜底(SELECTED 无出边,双保险)。

---

## 3. 迁移步骤(每步独立可合并、全测试绿后进下一步)

1. **Step 1(纯新增)**:五个 enum + `fromValue/getValue/canTransitionTo` + 迁移表全量边单测(正向、反向、非法、self);新增对账单测:枚举 name 集合 ≡ §1 清单值集合(防漏防拼错)。
2. **Step 2(替常量)**:ProductionRunService 的 `RUN_*/STEP_*/QC_*` 常量改为 enum 引用(private 常量直接删除;public 常量保留并标注 `@Deprecated`,委托 `ProductionRunStatus.X.getValue()`,GenerationTaskReaper 等外部引用零改动)。同批修复 ProductionRepairExecutor:73 裸 `"SUBMITTED"` 与 ProductionTake 字段初始化器裸 `"REVIEW_REQUIRED"`。
3. **Step 3(上守卫)**:写路径接入 `transitionRun/transitionStep`;补齐迁移表覆盖的现有行为单测(start/submit 失败/reconcile/qc/select/repair 各路径断言状态不变)。
4. **Step 4(收编 Repair/QC)**:RepairRouter/Executor、TechnicalQcService、ReconcileScheduler 换 enum;`Decision` record 的 route/status 字段类型化(String 出参保持)。
5. **Step 5(前端镜像)**:`ProductionStep.status/stepType` 收紧为 union type;`production-status.ts` 两张 map 改为 `Record<ProductionRunStatus, ...>` 穷尽性写法;后端非法迁移 500/400 的用户文案核对。

## 4. 风险与对策

| 风险 | 对策 |
|---|---|
| 存量 DB 脏值(历史手工数据)导致 `fromValue` 抛错、列表打不开 | **读路径宽松、写路径严格**:读侧 `fromValueOrNull`(未知值原样透传 + WARN,仅此一处留档技术债,禁止扩散成 fallback);写侧严格抛错 |
| 迁移表与现状行为推导有出入,上线即红 | Step 3 前先以「log-only 观察模式」跑全部单测+集成测试;IT(AgentWaitingStateIT 同套)覆盖 repair 复活、SELECTED 后 reconcile 两条最易错边 |
| 外部引用 public 常量遗漏(GenerationTaskReaper 等) | `@Deprecated` + IDE 告警清扫;架构守护测试可后续加规则:production 包内 `setStatus` 禁止字符串字面量实参 |
| 前端镜像漂移 | Step 5 用 `Record<Union,...>` 穷尽性检查;两处 map 不再手写字符串键 |
| API 字符串变化破坏 OpenAPI/前端 | VO 全程 String;enum 只在 service 内部——验收项之一为 `/v3/api-docs` diff 为空 |

## 5. 验收标准

1. 全测试绿(单测 + IT + e2e 六路径,含生产中心页冒烟);`/v3/api-docs` 与实现前 diff 为空。
2. 仓库内 `RUN_*/STEP_*/QC_*` 字符串常量清零(RepairRoute 常量收编为 enum)。
3. 非法迁移在写路径抛 `IllegalStateException` 且日志含 runId/from/to;迁移表单测覆盖 §2.3 全部 ✓ 边 + 全部非法边抽样。
4. ARCHITECTURE.md §5「状态字符串」契约行更新为 enum 落点;本文档标记实现完成。

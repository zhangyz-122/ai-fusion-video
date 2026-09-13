# REVIEW R1(SW-T01~T11 架构审查记录)

审查人:Architect(Agent-1)。日期:2026-09-14。依据:swarm/ARCHITECTURE.md(R1 扩写版)、swarm/TECH_DEBT_PLAN.md、代码实扫。
修正直接落入 swarm/BACKLOG.md 各条目缩进行;本文件记录"发现了什么、改了什么、为什么"。

## 逐条结论

| ID | 结论 | 补充/修正 |
|---|---|---|
| SW-T01 | 通过,补 RISK | DataURI 体积膨胀(~33%)的请求体/内存上限;v22→v23 含音轨 QC 显式化。FILES 正确(uploadBoundMedia 位于 ComfyUiGenerationExecutor/ComfyUiInputResourceService)。 |
| SW-T02 | 通过,补时序约束 | aria/按钮结构改动破坏 e2e 定位器 → 必须先于/同批于 SW-T03 合入;并标注与 generation-workbench 拆分(TECH_DEBT #7)的先后。 |
| SW-T03 | 通过,补 DEPENDENCIES | **原条目缺失对 SW-T02 的依赖**,已补;另澄清"全绿"仅指 Playwright 套件,与 SW-T10 的 Java 存量失败是两个口径,避免验收歧义。 |
| SW-T04 | 通过,补 RISK | 幂等跳过必须可观测(日志/metric),防止静默吞掉真实 journal 乱序。 |
| SW-T05 | 通过,补 RISK+NOTE | 软删过滤需覆盖全部资产列表查询(漏一处即数据泄漏);物理删除与媒体文件一致性;缓存 @CacheEvict 义务;与 asset-detail-sheet(1465 行)同域,先功能后拆分。 |
| SW-T06 | 通过,补 RISK | SRT 空对白/超长的期望格式需先定规格,避免测试固化任意行为。 |
| SW-T07 | 本轮完成 | 标题行数校准:dashboard 页实为 498(已拆),workbench 实为 994;产出=ARCHITECTURE.md 扩写+TECH_DEBT_PLAN.md+本轮增补+本记录。 |
| SW-T08 | 通过,加回填要求 | 170 集树性能结论须回填 TECH_DEBT_PLAN 第二梯队,作为 storyboard 页面族拆分优先级输入。 |
| SW-T09 | 通过,发现联动缺口 | 与 SW-T10 同涉"僵尸/滞留"判定——**原两条未互相引用,存在两套标准并存风险**;已互相标注 DEPENDENCIES(建议 SW-T10 先行定阈值,SW-T09 复用)。 |
| SW-T10 | 通过,补 RISK | 存量测试修复可能暴露产品真实缺陷,允许小范围产品修正但须留档;参数化默认值保持 2h 不变。 |
| SW-T11 | 通过 | 明确评估输入为本轮 R1 产出,评估结论以 SW-T17+ 或条目修订形式落地。 |

## 发现的重复/矛盾与处理

1. **SW-T02 ↔ SW-T03 时序矛盾(最实质)**:两者各自独立,但 SW-T02 的按钮/aria 修复必然改动 SW-T03 要适配的定位器;若并行,QA 适配两次。处理:SW-T03 增补 DEPENDENCIES=SW-T02。
2. **SW-T09 ↔ SW-T10 标准分裂风险**:僵尸会话标记 failed 与滞留任务回收(2h)是同一"任务失联"问题的两个出口。处理:互设 DEPENDENCIES/NOTE,统一以 GenerationTaskReaper 阈值为唯一口径。
3. **SW-T07 描述与现状漂移**:"990 行 dashboard 页"已不成立(page 498,存量 _components);处理:条目内 NOTE 校准,标题保留历史不改(已派发过的措辞)。
4. **SW-T05 ↔ TECH_DEBT #3 文件域重叠**:recycle-bin 改造与 asset-detail-sheet 拆分都动资产详情域。处理:双侧互注"先功能后拆分",避免合并冲突。
5. 未发现功能需求层面的重复条目;SW-T06(测试)与 SW-T08(扫描)互补不重叠。

## 横向发现(供 Supervisor 派发参考)

- 所有 P1 条目(SW-T01/02/03)构成一个合入波次;SW-T03 必须收尾该波次。
- P2 条目中 SW-T04/05/10 互不依赖,可并行;SW-T13/T14/T15/T16(R1 新增)详见 BACKLOG。
- 架构规则回归风险:本轮未改任何产品代码;拆分批次 A 建议在 SW-T14 守护门禁落地后启动(先红后拆)。

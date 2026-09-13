# TECH DEBT PLAN(巨型文件拆分计划)

生成:2026-09-14,Architect Round 1(SW-T07)。扫描范围:`ai-fusion-video-web`(app/components/lib)与 `ai-fusion-video/src/main/java`(main,不含 test)。
红线:前端单文件 ≤1000 行(AGENTS.md),页面建议 ≤500 行、业务组件建议 ≤400 行;Java 无硬红线,>500 行列入观察。

## 拆分总原则

1. **行为不变**:纯移动 + 提取,不改交互/样式/契约;每步拆分后 e2e + 单测全绿再提交。
2. **拆分方向**:多组件同居 → 一文件一组件;页面内嵌表单/列表 → `_components/`;store 巨石 → 按职责分模块再聚合导出;Java 协议/机制类 → 按 HTTP 构造、响应归一化、媒体装载、尺寸映射分协作类。
3. **顺序**:先拆"正在被多任务触碰的文件"(改动冲突风险高),后拆稳定文件。
4. 每完成一项在本文打勾并记录实际行数;新增超限文件必须同步登记。

## Top 15(按行数降序,前后端合并)

| # | 路径(相对仓库根) | 行数 | 职责 | 拆分建议 |
|---|---|---|---|---|
| 1 | ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/_components/storyboard-ref-panel.tsx | 1637 | 7 个组件同居:BatchFrameGenDialog(132-461)、BatchFrameGenerateControl(461-532)、SceneAssetPanel(532-845)、AssetItemGroup(845-930)、ItemDetail(930-1335)、LinkedAssetGroup(1335-1454)、StoryboardOverview(1454-1530)+ StoryboardRefPanel 出口 | 每组件一文件移入同目录 `ref-panel/` 子目录;`parseIds`/`compareStoryboardItemsAsc` 移共享 util;index barrel 保持对外导入路径不变。目标单文件 <400 行 |
| 2 | ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/page.tsx | 1541 | 分镜 Tab 页:数据加载、视图切换(表格/卡片)、Sheet、批量操作编排全部内嵌 | 提取 `use-storyboard-data.ts`(加载/失效)、`storyboard-toolbar.tsx`(视图切换+主操作)、`storyboard-batch-actions.tsx`;page 只留路由参数与组合,目标 <300 行 |
| 3 | ai-fusion-video-web/components/dashboard/asset-detail-sheet.tsx | 1465 | 资产详情浮层:ImageLightbox(114)、CoverSelectorDialog(139)、AssetItemEditPanel(288)、AssetItemCreatePanel(670)、AssetDetailPanel(858)+ parseProps | `asset-detail/` 子目录五文件;props 解析移 `asset-detail-props.ts`;与 SW-T05 回收站改造同批规划避免二次冲突 |
| 4 | ai-fusion-video-web/lib/store/pipeline-store.ts | 1140 | zustand 巨石:任务列表 + SSE 连接编排 + 失效广播 + 通知面板 UI 态 | 按既有伴生模块模式继续抽:`pipeline-connection.ts`(连接/恢复/续传)、`pipeline-invalidation.ts`(失效计数);store 保留聚合门面,导出签名不变 |
| 5 | ai-fusion-video/src/main/java/.../generation/image/strategy/support/OpenAiCompatibleImageProtocolSupport.java | 1132 | OpenAI 兼容图像协议机制:HTTP 请求构造、响应归一化、媒体下载/落盘、模型尺寸映射 | 拆 ImageRequestBuilder、ImageResponseNormalizer、ImageMediaLoader、ImageSizeMapper 四个协作类,原类保留门面方法委托;类注释已声明"无编排/持久化",拆分不改边界 |
| 6 | ai-fusion-video-web/components/dashboard/notification-panel/detail.tsx | 1028 | 通知面板:PipelineDetailPanel(158)、HistoryDetailPanel(313)、TaskListItem(456)、PipelineTaskCard(486)、ExpandedPanel(579)+ 恢复工具函数 | `notification-panel/detail/` 子目录五文件;createPipelineRecovery/merge 等纯函数移 `detail/recovery.ts` 配单测 |
| 7 | ai-fusion-video-web/components/dashboard/generation-workbench.tsx | 994 | 生成工作台主组件(图/视频双模式):模型选择、参数、结果、动效编排 | 已有 generation/ 子组件(advanced-panel、header、asset-dialog);继续抽 `use-generation-forms.ts`(表单态)与 `generation-results.tsx`(结果区);注意 BACKLOG SW-T02 会触碰本文件,拆分排其后 |
| 8 | ai-fusion-video-web/app/(dashboard)/settings/ai-models/_components/model-config-support.tsx | 976 | 模型配置可视化表单 + config JSON 解析/字段定义 | 按"字段 schema 定义 / 表单渲染 / JSON 双向转换"拆三文件;schema 部分可单测 |
| 9 | ai-fusion-video-web/app/(dashboard)/settings/ai-models/page.tsx | 974 | 模型管理页:列表、筛选、对话框编排 | 列表与筛选抽 `_components/model-list.tsx`、`model-filters.tsx`;page 组合化,目标 <300 行 |
| 10 | ai-fusion-video/src/main/java/.../ai/run/DurableAgentWaitingStateService.java | 963 | MySQL 持久 WAITING 状态机(确认候选/外部执行两类,含 owner 围栏与恢复) | 按两类 WAITING 拆 WaitingConfirmationStore / WaitingExternalStore 共享围栏基类;ResumeXxx 命令处理各自成类,接口 AgentWaitingStatePort 不变 |
| 11 | ai-fusion-video-web/app/(dashboard)/settings/general/page.tsx | 934 | 通用设置页:系统配置多个分区表单内嵌 | 每配置分区(站点/存储/媒体等)`_components/general/` 独立卡片组件;URL 校验工具移 lib |
| 12 | ai-fusion-video-web/lib/store/assistant-store.ts | 888 | 助手 zustand 巨石:会话运行时、草稿、工具确认、停靠模式 | 运行时更新器已在 assistant-runtime;继续抽 `assistant-actions.ts`(工具确认/批量应答)与 `assistant-ui-mode.ts`(停靠/抽屉态) |
| 13 | ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/_components/storyboard-table-view.tsx | 844 | 分镜表格视图:列定义、拖拽列宽持久化、单元格渲染 | `table/columns.tsx`、`table/use-col-widths.ts`(localStorage 逻辑可单测)、行操作组件独立 |
| 14 | ai-fusion-video/src/main/java/.../ai/run/DefaultRunExecutionSupervisor.java | 839 | Agent 执行监督:启动/恢复/等待检查点/终态协调/事件管道接线 | 按"启动路径 / 恢复路径 / 终态处理"提取协作类;先补 RunExecutionSupervisorTests 覆盖再动(现有 655 行测试是安全网) |
| 15 | ai-fusion-video-web/app/(dashboard)/projects/[id]/scripts/page.tsx | 793 | 剧本 Tab 页:加载、侧栏折叠、编辑/解析编排内嵌 | 参照 storyboard 页同套方案:`use-script-data.ts` + `_components/script-toolbar.tsx`;侧栏折叠持久化移 hook |

## 第二梯队(>500 行,拆分随邻近任务顺带处理)

- 前端:settings/storage/page.tsx 791、ai-models/_components/ai-model-dialog.tsx 790、lib/store/pipeline-event-handler.ts 769(纯函数,先补测试再拆)、notification-panel/timeline.tsx 769、agent-pipeline/state.ts 744(已单测,低风险)、projects/[id]/settings/page.tsx 672、storyboard-sidebar.tsx 654、ai-models/_components/api-config-dialog.tsx 596、lib/store/assistant-connection-coordinator.ts 594、storyboard-card-view.tsx 543、assistant/dock-slot.tsx 533、create-project-dialog.tsx 514。
- 后端 main:OpenAiCompatibleVideoProtocolSupport 717(与 #5 同套拆法)、VideoComposeService 709(合成状态机可抽 ComposeStateMachine)、AgentSkillImportService 703、AiAgentRegistry 691(按 Agent 族拆 @Configuration 分册)、AgentScopePipelineRunService 658(prepare/launch/continuation 三段)、OpenAiResponsesAgentScopeModel 636、HarnessLeaseCache 632、AgentMessageProjectionService 629、DashScopeVideoStrategy 628、GenerationModelCapabilityService 604、NewApiVideoStrategy 570、ProductionRunService 579、GoogleFlowReverseApiSupport 556、AiStreamRedisService 535、AgentRunQueryService 513、AgentScopeV2Properties 504。

## 建议执行批次(依赖 SW-T02/T05 时序)

1. **批次 A(先行,冲突低)**:#5、#10、#14(后端)+ #6、#8、#11(前端)。
2. **批次 B(待 SW-T02 合并后)**:#7 generation-workbench、#9、#12、#13、#15。
3. **批次 C(待 SW-T05 合并后)**:#3 asset-detail-sheet、#1、#2(同属 storyboards 页面族,一次冲刺内顺序完成)、#4。
4. 每批次完成更新本文清单行数;目标:前端 >1000 行文件数归零,Java main >700 行文件数减半。

## 架构守护(配套,见 BACKLOG SW-T14)

- CI 增加`行数预算`检查:前端 >1000 行 fail,500–1000 行新文件 warn;Java main >700 行 warn。
- ArchUnit(或等价)固化 ARCHITECTURE.md 依赖规则:controller 不触 mapper、run↔agentscope 单向、策略互不依赖。

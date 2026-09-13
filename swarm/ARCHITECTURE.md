# ARCHITECTURE(融光 ai-fusion-video 全量架构文档)

更新:2026-09-14。维护者:Architect(Agent-1)。基点分支:sprint/universal-director-integration。
本文为权威架构摘要:模块依赖(文字版)、核心数据流、跨模块契约。改产品代码前先读本文;发现漂移请更新。

---

## 1. 系统总览

- 后端 `ai-fusion-video/`:Spring Boot 3 + MyBatis-Plus(Flyway 迁移)+ Redis(任务队列 + 流式通道)+ AgentScope V2 内核(Harness 管道)+ ComfyUI 执行器(工作流绑定渲染)+ 策略制多供应商图像/视频生成。
- 前端 `ai-fusion-video-web/`:Next.js 16 App Router + Shadcn(base-ui)+ zustand(pipeline-store / assistant-store)+ 原生 SSE(authenticatedFetch 手工解析,非 EventSource)。
- 部署:docker-compose(单机/分离式两套),MySQL + Redis 外置;媒体文件走 MediaStorageService 抽象(本地 ./data/media 或对象存储策略)。

---

## 2. 后端模块依赖图(文字版)

包根:`com.stonewu.fusion`。分层:`controller → service → mapper/entity`;横向基础设施:`infrastructure/queue`、`config`、`common`。

```
controller/(REST 入口,按域分包:ai、production、script、storyboard、generation、
  project、asset、dashboard、storage、system、task、team)
  ├── controller/*/vo/          请求/响应 VO(按域)
  ↓ 仅依赖对应 service
service/
  ├── script/         ScriptService(剧本CRUD+结构解析回退) ← ScriptAutoSplitService(自动分块)
  │                   SubtitleExportService(SRT 导出)
  ├── storyboard/     StoryboardService(分镜/集/场/条目 CRUD+资产守卫)
  │     └── VideoComposeService(选片后合成成片,状态机 IDLE/RUNNING/DONE/FAILED)
  ├── production/     ProductionRunService(三候选生产编排,唯一编排入口)
  │     ├── ProductionTechnicalQcService(技术质检)
  │     ├── ProductionRepairRouter / ProductionRepairExecutor(修复计划/执行)
  │     ├── ShotReadinessService(开拍就绪评估)
  │     └── ProductionRunReconcileScheduler(60s 定时重同步)
  ├── generation/     GenerationModelCapabilityService(模型能力解析:首尾帧/参考图上限)
  │     ├── GenerationTaskReaper(滞留任务回收,2h)
  │     ├── ReferenceImageTransportService
  │     ├── image/  VideoGenerationService + ImageGenerationService(任务持久化)
  │     ├── image/strategy、video/strategy  策略包(comfyui/dashscope/openai/newapi/
  │     │   agnes/googleflow/volcengine),strategy/support 放共享协议机制
  │     └── image|video/consumer  消费者:submitTask 入队、consume() 出队执行
  ├── ai/
  │     ├── AiModelService / ApiConfigService / ModelPresetService(模型与凭据配置)
  │     ├── AiAgentService + config/ai/AiAgentRegistry(内置 Agent 定义注册表,17 个 builtin)
  │     ├── CapabilityCatalogService(能力目录)、DashboardService、AiToolConfigService
  │     ├── AiStreamRedisService(双通道流:Redis Stream 实时 token + Redis List Replay 重连回放,1h 过期)
  │     ├── provider/  ChatModelFactory、AbstractAiProvider、OpenAiCompatibleAiProvider、
  │     │              OpenAiResponsesAgentScopeModel(AgentScope 模型适配)
  │     ├── comfyui/   ComfyUiGenerationExecutor(图像/视频策略共享执行编排:context 解析→
  │     │              prepare 渲染→submit→waitForJob→storeOutputs)
  │     │              ComfyUiWorkflowService/DocumentService/Renderer/ValidationService、
  │     │              WorkflowProfileService(执行档案解析)、ComfyUiNativeClient(HTTP)
  │     ├── agentscope/ AgentScopePipelineRunService(根 Harness 运行唯一生产入口)
  │     │     ├── kernel/  AgentKernelSpecFactory、HarnessLeaseCache(租约)、AgentPromptVariables
  │     │     ├── runtime/ AgentRuntimeSchedulers、state/(StateStoreSlot)
  │     │     ├── skill/   AgentScopeSkillRegistry、AgentSkillImportService、AgentUserSkillService
  │     │     ├── mcp/     AgentScopeMcpRegistry
  │     │     ├── tool|permission|message|workspace|context  工具适配/执行模式/消息映射/工作区
  │     │     └── AgentScopeSubAgentToolAdapter(子 Agent 以工具形式挂载)
  │     └── run/  执行监督层(与内核解耦的可持久化执行框架)
  │           ├── RunExecutionSupervisor ← DefaultRunExecutionSupervisor(执行生命周期/等待/恢复)
  │           ├── AgentRunCoordinator、AgentExecutionFactory、OwnedExecutionRegistry
  │           ├── AgentEventJournal(MySqlAgentEventJournal:agent_event 事件日志/seq 单调)
  │           ├── AgentEventOutboxPublisher(outbox→Redis 唤醒提示)、BoundedAgentEventIngress
  │           ├── AgentEventChunkCoalescer(合并 chunk)、AgentScopeEventMapper(内核事件→信封)
  │           ├── AgentMessageProjectionService + AgentMessageAllocator(事件→agent_message 投影)
  │           ├── DurableAgentWaitingStateService(MySQL 持久 WAITING:确认/外部执行两类)
  │           ├── AgentConfirmationService/Coordinator、AgentRunReplayService(重放)、
  │           │   AgentRunReconciliationService、AgentRunMaintenanceScheduler、CancellationCoordinator
  │           └── Direct*GenerationService(助手直接触发生图/分镜帧/生视频的直连通道)
  ├── asset/ dashboard/ project/ storage/(strategy)/ system/ team/  支撑域
  ↓
mapper/(MyBatis-Plus)、entity/(production/generation/script/storyboard/ai/asset/…)
infrastructure/queue/RedisTaskQueue(多实例安全:LPOP/RPUSH + INCR/DECR 并发闸,
  key 前缀 fv:taskqueue:,注册表 registered_queues,默认并发 1)
```

依赖规则(评审基线):
1. controller 不直接访问 mapper;跨域只能走 service。
2. `service/ai/run` 不 import AgentScope 内核类型以外的 `io.agentscope` 运行时(通过 Port/Adapter 反转);`agentscope/` 依赖 `run/`,反向禁止。
3. 生成策略之间互不依赖;共享逻辑下沉 `strategy/support`,协议机制下沉 `provider/`。
4. production 只编排既有 VideoTask/RedisTaskQueue,禁止建第二套视频执行链(类注释明确)。

---

## 3. 前端模块结构

```
app/
  (auth)/            login/register/forgot-password/setup
  (dashboard)/       共享 dashboard layout(Header/Sidebar 固定,main=覆盖式 ScrollArea)
    dashboard/       首页仪表盘(_components: activity-section、recent-projects)+ analytics
    generate/        image|images|video|videos|audios|universal(生成工作台,复用 generation-workbench)
    projects/[id]/   scripts、storyboards(页+_components: ref-panel/table/card/sidebar)、settings
    production/      生产中心:page(运行列表)+ _components(run-detail-drawer/sections)
    assets/ settings/(ai-models、general、storage、profile)
components/
  ui/                Shadcn(base-ui)基线组件,禁止改源码
  dashboard/         业务组件:generation-workbench、asset-detail-sheet、notification-panel/
                     (detail、timeline)、agent-pipeline/(state 纯函数+use-agent-pipeline)、
                     assistant/(dock-slot)、main-content-frame、overlay-scroll-area、shared/
lib/
  api/               每域一个 client(ai-pipeline、task-stream、production、storyboard、script、
                     asset、generation、ai-model、comfyui-workflow、capability、system…)
                     client.ts=http 封装;ai-assistant.ts 提供 authenticatedFetch+SSE 解析
  store/             zustand:pipeline-store(任务/时间线/失效广播)、assistant-store(会话运行时)、
                     auth-store;伴生纯模块:pipeline-event-handler(SSE→时间线)、pipeline-timeline、
                     pipeline-status-sync、assistant-runtime、assistant-connection-coordinator
  generation-capabilities.ts  与后端 GenerationModelCapabilityService 对应的前端镜像
e2e/                 Playwright 六条创作路径用例 + helpers
```

规则:页面只做参数/数据加载/编排;列表、筛选、编辑器在 `_components/`;纯逻辑抽 `state.ts`/`lib/store/*` 便于单测(agent-pipeline/state.test.ts 是范本)。

---

## 4. 核心数据流

### 4.1 Agent 管道流(助手/通用导演)

```
前端 pipeline-store.start()
  → POST /api/ai/pipeline/run (SSE, authenticatedFetch)
  → AiPipelineController → AgentScopePipelineRunService.prepare(模型/Agent定义/技能≤8个/
      系统提示词/内核快照 AgentKernelSnapshot)
  → AgentRunCoordinator 持久化 AgentRun(root/sub)→ RunExecutionSupervisor.start
  → AgentExecutionFactory 创建执行 → OwnedExecutionRegistry 登记(实例身份 instanceIdentity)
  → AgentScope 内核(Harness 租约 HarnessLeaseCache)逐 token 产出
  → AgentScopeEventMapper → BoundedAgentEventIngress → AgentEventChunkCoalescer
  → AgentEventJournal 提交 agent_event(sequence 单调,幂等去重)
  → 三路分发:
     a) AgentEventOutboxPublisher → Redis 唤醒提示 → TaskStreamService →
        AiStreamRedisService(Redis Stream 实时 + Replay List 回放)
     b) SSE 直接 flush 给首连请求(ServerSentEvent<AiChatStreamRespVO>)
     c) AgentMessageProjectionService/AgentMessageAllocator → agent_message 投影
  → 工具调用:ToolExecutorRegistry → tool/(script/storyboard/project 域写操作)或
     SubAgentToolAdapter(子运行);需人工确认 → DurableAgentWaitingStateService.enterWaitingConfirmation
  → 前端断线:GET /api/ai/pipeline/reconnect?runId&lastSequence(Replay 回放+续传)
     或 GET /api/task-stream/reconnect?taskId(任务维度)
  → 终态:MySqlRunTerminalCoordinator 写终态;前端 TOOL_INVALIDATION_MAP 触发 assets/
     scripts/storyboards 计数失效 → 页面 refetch
```

持久化恢复:实例崩溃后 `AgentRunReconciliationService`+`DeterministicPipelineRecoveryService` 依据 journal+快照恢复;WAITING(确认/外部)由 `DurableAgentWaitingStateService` 以 MySQL 行 + owner 围栏保证,确认过期由 `AgentConfirmationExpiryCoordinator` 清扫。

### 4.2 生产链(三候选+QC+选片+合成)

```
StoryboardItem(就绪校验 ShotReadinessService)
  → POST /api/production/runs(idempotencyKey 幂等,重复键直接返回既有 run)
  → ProductionRunService.start:建 ProductionRun(CREATED)+ProductionStep(GENERATE_VIDEO)
      + VideoTask(count=3, category=production, 固化 workflowVersionId)入 RedisTaskQueue
  → step=SUBMITTED,run=WAITING_GENERATION
  → VideoGenerationConsumer.consume() 出队 → 策略(comfyui 经 ComfyUiGenerationExecutor/
      其他走 OpenAI 兼容或厂商 SDK)→ VideoItem×3 落库,task.status=2 成功/3 失败
  → ProductionRunReconcileScheduler(60s)或手动 POST /runs/{id}/reconcile:
      同步 VideoItem → ProductionTake(takeIndex 1..3)+ QcResult(SYSTEM 评估)
      + ProductionTechnicalQcService 技术质检;数量≠3 → FAILED(TAKE_COUNT_MISMATCH)
  → 技术失败 → ProductionRepairRouter 出 PLANNED 修复计划 → 手动 POST /runs/{id}/repair
      → ProductionRepairExecutor 重提交 → run 回 WAITING_GENERATION(调用方显式触发,无后台无限重试)
  → 人工 QC:PUT /runs/{id}/takes/{takeId}/qc(PASS/FAIL/REVIEW_REQUIRED;技术 FAIL 禁止人工 PASS)
  → POST /runs/{id}/takes/{takeId}/select:仅 PASS 可选;写 run.selectedTakeId +
      storyboard_item.selected_take_id(双向一致,缓存 @CacheEvict storyboardItem)
  → POST /runs/{id}/compose → VideoComposeService.submitCompose(episodeId)按集合成片
```

Run 状态机:`CREATED → WAITING_GENERATION → QC_PENDING → SELECTED;任意步失败 → FAILED(带 failureCode,可 repair 复活)`.
Step 状态机:`CREATED → SUBMITTED → SUCCEEDED | FAILED`.

### 4.3 图像生成链(非生产直用)

`generate/images 页 → generationApi → POST /api/generation/image → ImageGenerationConsumer.submitTask → RedisTaskQueue → strategy(同视频策略族)→ ImageItem`。模型能力(首帧/尾帧/参考图)由 `GenerationModelCapabilityService` 与前端 `lib/generation-capabilities.ts` 双端镜像,改动必须两处同步。

### 4.4 ComfyUI 执行契约

模型(`AiModel.comfyuiWorkflowId`)→ 工作流 → **已发布版本**(published,任务固化 versionId,杜绝版本漂移)→ `ComfyUiExecutionContext(model, apiConfig, workflow, version)`;输入绑定经 `ComfyUiInputResourceService.uploadBoundMedia`(引用转 DataURI 或上传,见 SW-T01);`waitForJob` 轮询 `ComfyUiNativeClient`;产物 `ComfyUiOutputResolver → storeOutputs → MediaStorageService`。

---

## 5. 关键契约(跨端/跨模块)

| 契约 | 位置 | 要点 |
|---|---|---|
| SSE 事件信封 | `AiChatStreamRespVO` + `AgentEventEnvelope` | sequence 单调、可重放;前端 `parseSseEventBlock` 按 `data:` 行解析,断线凭 lastSequence reconnect |
| Pipeline REST | `/api/ai/pipeline/*` | run(SSE)/continue/cancel/confirm/confirm/expire/reconnect(SSE)/status/running |
| TaskStream REST | `/api/task-stream/*` | reconnect(SSE)/status/running;任务维度与 run 维度并存 |
| Production REST | `/api/production/*` | runs、shots/{id}/readiness、runs/{id}(reconcile|repair|compose)、takes/{id}(qc|select);全部 userId 围栏 |
| 幂等 | `ProductionStartReqVO.idempotencyKey` | (userId, storyboardItemId, key) 唯一定位既有 run |
| 状态字符串 | ProductionRunService 常量 | RUN_*/STEP_*/QC_* 目前是 String 常量而非 enum(已知债务,见 TECH_DEBT_PLAN) |
| 模型能力 | GenerationModelCapabilityService ↔ lib/generation-capabilities.ts | supportsFirstFrame/LastFrame/ReferenceImages、maxReferenceImages;双端镜像需同步 |
| 缓存 | `@Cacheable/@CacheEvict`(如 storyboardItem) | create/update/delete 必须清缓存(AGENTS.md 全局规则) |
| 当前用户 | `SecurityUtils.requireCurrentUserId()` | 禁止硬编码 userId;所有列表查询带 userId 围栏 |
| Redis 队列 | `fv:taskqueue:*` | 原子 LPOP+INCR 许可;默认每队列并发 1;Reaper 回收滞留 2h |
| 流式 TTL | AiStreamRedisService | 状态/Stream/Replay 均 1h 过期;过期后只能走 status/running 兜底 |
| 迁移 | `db/migration/V1.x.y.z.n__desc.sql` | 命名与基线规则见迁移目录 README;本冲刺禁新增迁移 |
| 前端布局 | main-content-frame + overlay-scroll-area | 滚动条覆盖式;ScrollArea 以 pathname 为 key;详见 AGENTS.md |

---

## 6. 已知架构债务(详见 TECH_DEBT_PLAN)

1. 前端 6 个文件超 1000 行红线(storyboard-ref-panel 1637、storyboards/page 1541、asset-detail-sheet 1465、pipeline-store 1140、notification detail 1028)。
2. 后端 OpenAiCompatibleImageProtocolSupport 1132 行,协议机制类过大;Agent run 层 DurableAgentWaitingStateService/DefaultRunExecutionSupervisor 接近 900 行。
3. Production 状态机用散落 String 常量,无 enum/合法迁移表。
4. `AiAgentRegistry` 691 行集中注册 17 个 builtin Agent 定义,新增 Agent 单点拥挤。
5. tool/ 子域直接写各域 service,权限模式(ToolExecutionMode)与审计散落多处。

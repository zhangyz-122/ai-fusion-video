# BACKLOG(统一任务池)
格式:ID|P|OWNER|TITLE|FILES|ACCEPTANCE
条目下方缩进行为 Architect 审查补充(DEPENDENCIES/RISK/NOTE),不改变原管道格式。
R1 审查:2026-09-14,Architect。详细审查记录见 swarm/REVIEW-R1.md。

SW-T01|P1|Dev-A|INFINITETALK 按请求音频:uploadBoundMedia 支持 uploaded_audio(转DataURI),出v23按请求驱动|service/ai/comfyui/ComfyUiGenerationExecutor.java、ComfyUiInputResourceService.java+tests|请求传音频试运行出片含音轨
  RISK: 音频转 DataURI 体积膨胀约 33%,受 HTTP 请求体/内存上限约束,需设上限与超限报错;v22(内置音频)与 v23 行为差异须在 QC 中显式校验含音轨。
  DEPENDENCIES: 无阻塞;涉及 uploadBoundMedia 私有链路,拆分 TECH_DEBT #5 批次时避开该文件。

SW-T02|P1|Dev-B|T10 六条UI缺陷:生图死链/SeedVR2入口/分镜按钮aria与hover误触/生产抽屉文案/登录页品牌|generate/image、videos、storyboards行内按钮、login页|六项逐一可核
  RISK: aria/按钮结构改动会破坏 Playwright 定位器——必须在同一提交内同步 e2e,或先合 SW-T02 再做 SW-T03。
  DEPENDENCIES: 无;但 SW-T03 依赖本条先行(见下)。触碰 generation-workbench/storyboard 行内按钮,TECH_DEBT 批次 B 拆分排其后。

SW-T03|P1|QA|E2E 定位器适配集成版UI(5失败用例:路径1/3/4/5/6)|e2e/**|playwright 全绿
  DEPENDENCIES: SW-T02(其按钮/aria 改动会二次破坏定位器,必须在其合入后适配,否则重复返工)。
  NOTE: 全绿基线含 SW-T10 之前的存量 Java 失败——本条只覆盖 Playwright 套件,勿混淆两套测试口径。

SW-T04|P2|Dev-A|AgentMessageAllocator 完全幂等化(重复投影跳过而非抛错)|service/ai/run/AgentMessageAllocator.java、AgentMessageProjectionService.java+tests|维护调度不再报错
  RISK: 跳过语义必须可观测(重复计数日志/metric),不得静默吞——否则掩盖真实的 journal 乱序问题。
  DEPENDENCIES: 无;与 AgentRunMaintenanceScheduler 维护路径联测。

SW-T05|P2|Dev-B|资产回收站后端三接口(已删列表/恢复/物理删除)+前端接真接口替换localStorage方案|asset controller/service+recycle-bin-view.tsx+lib/api/asset.ts|删→回收站可见→恢复→物理删全链路
  RISK: 软删过滤必须覆盖所有资产列表查询(漏一处即泄漏已删资产);物理删除需与 MediaStorageService 文件清理一致;与 asset-detail-sheet(1465行,TECH_DEBT #3)同域,先功能后拆分。
  DEPENDENCIES: 无;@Cacheable 资产缓存需按 AGENTS.md 规则同步 @CacheEvict。

SW-T06|P2|QA|新功能边界测试:chunkChars边界/SRT极端用例(空对白超长文本)/系统状态降级|src/test/**|全绿
  RISK: SRT 空对白/超长文本的期望输出格式(空块 vs 占位/截断)需先定规格再写断言,避免测试固化任意行为。
  DEPENDENCIES: 无。

SW-T07|P2|Architect|状态与架构体系初建+巨型文件拆分计划(990行dashboard页、971行workbench)|swarm/*.md、docs|计划文档可执行
  NOTE: R1 已完成(本轮)。校准实际行数:generation-workbench 994;dashboard/page 已拆至 498(存量 _components 生效);Top15 清单与执行批次见 swarm/TECH_DEBT_PLAN.md。

SW-T08|P3|Red Team|安全与性能扫描报告(SSRF绕过/N+1/170集树性能/内存)|swarm/报告|可执行发现清单
  NOTE: 170 集树性能结论请回填 TECH_DEBT_PLAN.md 第二梯队(storyboard 页面族)作为拆分优先级依据。
  DEPENDENCIES: 无。

SW-T09|P3|Dev-B|通知面板僵尸会话后端标记failed(DB running但Redis NONE)|task service/controller|僵尸不再显示
  RISK: 误杀慢任务;僵尸判定阈值/口径必须与 GenerationTaskReaper(SW-T10 参数化对象)统一,禁止出现两套"僵尸"标准。
  DEPENDENCIES: 与 SW-T10 协调同批或明确先后(建议 SW-T10 先定阈值常量,本条复用)。

SW-T10|P3|Dev-A|ProjectServiceTests 存量失败修复+滞留阈值常量参数化|ProjectServiceTests、GenerationTaskReaper|存量失败清零
  RISK: 修复存量测试可能暴露产品代码真实缺陷——允许小范围产品修正但须留档;参数化默认值保持 2h 行为不变。
  DEPENDENCIES: 无;建议先于 SW-T09(为本条提供统一阈值)。

SW-T11|P4|Red Team|产品进化提案:批量镜头操作/诊断面板/字幕导入/模板库|swarm/提案|Architect 评估
  NOTE: 评估输入=本轮 ARCHITECTURE.md/TECH_DEBT_PLAN.md;评估结论将追加为 SW-T17+ 或并入既有条目。
  DEPENDENCIES: SW-T07(本评估基于其产出)。

SW-T12|P2|Dev-B|storyboard 页面族拆分(TECH_DEBT #1/#2):ref-panel 1637行七组件分文件+page 1541行抽数据hook与工具栏|storyboards/_components/ref-panel/*、storyboards/page.tsx、新增 use-storyboard-data.ts|单文件<500行;六路径 e2e 全绿;对外导入路径经 barrel 不变
  VALUE: 消除最大两处红线违规;storyboard 是多任务热区,拆分降低后续所有冲突成本。
  COST: M-L(2-3 天,含 e2e 回归)。
  RISK: 大量 move 引起 git 噪声与定位器漂移——随拆同步 e2e;保持行为零变更。
  DEPENDENCIES: SW-T02、SW-T03(先合避免定位器二次返工);批次 C 详见 TECH_DEBT_PLAN.md。

SW-T13|P2|Dev-A|OpenAI 兼容协议支持类拆分(TECH_DEBT #5):图像1132行/视频717行按请求构造、响应归一化、媒体装载、尺寸映射四分|generation/image/strategy/support/*、generation/video/strategy/support/*+相关单测|拆分后全测试绿;策略类(comfyui/dashscope/...)零改动;单类<500行
  VALUE: 两类为图像/视频新供应商接入必经之路,拆分后接入成本显著下降;是后端最大债务。
  COST: M(1.5-2 天,机制类已有类注释边界声明,拆分风险低)。
  RISK: 低-中;纯提取,注意静态常量引用路径。
  DEPENDENCIES: 与 SW-T01 同文件域(comfyui 不在 support 包,实际无冲突);批次 A 先行。

SW-T14|P2|QA|架构守护 CI(TECH_DEBT_PLAN 配套):前端行数预算(>1000 fail、500-1000 新文件 warn)+ ArchUnit 依赖规则(controller不触mapper、run↔agentscope 单向、策略互不依赖)|tools/ 或 ci 脚本、src/test/arch/*|违规构建即红;存量豁免清单留档并有清零期限
  VALUE: 防止拆分成果回潮,把 AGENTS.md 行数红线从人审变成机器门禁。
  COST: S-M(0.5-1 天)。
  RISK: 豁免清单过宽沦为摆设——清单须有 owner 与期限。
  DEPENDENCIES: SW-T07(本计划);建议在批次 A 拆分开始前落地,先红后拆。
  NOTE(R2): Architect 已实现守护测试本体(后端 BackendArchitectureGuardTests 5 用例 + 前端 line-budget.test.mjs,豁免清单与运行方式见 TECH_DEBT_PLAN「架构守护」)。QA 剩余工作=把两条命令接进 CI 流水线,并在拆分任务合入后核对豁免收缩。

SW-T15|P3|Dev-B|pipeline-store 拆分(TECH_DEBT #4):连接编排/失效广播抽独立模块,store 保留聚合门面|lib/store/pipeline-store.ts、新增 pipeline-connection.ts、pipeline-invalidation.ts|store 消费方零改动(导出签名不变);现有单测+e2e 全绿
  VALUE: SSE 重连/恢复是最脆链路,分模块后可对连接编排单独测试。
  COST: M(1-1.5 天,牵涉 assistant-connection-coordinator 同构逻辑可一并归拢)。
  RISK: zustand set/get 时序回归——以行为快照测试护航。
  DEPENDENCIES: SW-T03(e2e 基线全绿是安全网前提);批次 B。

SW-T16|P3|Dev-A|Production 状态机 enum 化:RUN/STEP/QC 的 String 常量集中为 enum 并提供合法迁移表(canTransitionTo)|ProductionRunService、ProductionRepairRouter/Executor、QcResult+tests|非法状态迁移在编译期暴露;既有行为与 API 字符串值不变
  VALUE: 生产链是资金/时序敏感主链路,状态机显式化消除魔法字符串散落(ARCHITECTURE.md §5 已列契约)。
  COST: S-M(0.5-1 天;注意 API 出入参仍序列化为原字符串)。
  RISK: 低;enum name 与存量 DB 值逐一核对。
  DEPENDENCIES: 无;与 SW-T08 性能扫描结论互不阻塞。
  NOTE(R2): 设计稿已落盘 swarm/DESIGN-production-state-machine.md(现状盘点/迁移表/五步迁移/风险),实现按稿执行。

SW-T17|P2|Dev-A|run↔agentscope 双向耦合治理:run 侧引用的契约类型(context/kernel 快照/调度闸门/state slot)下沉中立包,恢复 agentscope→run 单向|service/ai/run/**、service/ai/agentscope/{context,kernel,state,runtime}/**、BackendArchitectureGuardTests R2 豁免清单收缩|R2 豁免 61 边/26 文件清零(或留档"只读 DTO 子集"边数较基线减半)
  VALUE: 全仓最大结构性耦合;守护测试已钉棘轮,每治理一条边白名单缩一条。
  COST: L(2-3 天;26 文件触点)。
  RISK: 共享类型双侧引用,须先定义接口契约再移实现;禁止"为移包而移包"——只下沉 run 实际引用的类型。
  DEPENDENCIES: SW-T14(守护已落地,本条即其 R2 豁免的治理出口);与 TECH_DEBT 批次 A(run 层两巨文件拆分)同文件域,排同批或先后,避免二次冲突。

SW-T18|P2|Dev-B|TeamController 直连 mapper 消除:memberCount 统计下沉 TeamService.enrich|controller/team/TeamController.java、service/team/TeamService.java+tests|BackendArchitectureGuardTests R1 豁免清零(测试仍绿)
  VALUE: 半小时内清零守护规则 R1 唯一违例(enrichTeamVO 绕过 service 直查 TeamMemberMapper)。
  COST: S(0.5h)。
  RISK: 无(纯搬移,行为不变;注意 TeamService 补 @Cacheable 语义评估)。
  DEPENDENCIES: 无;建议在守护测试进 CI 硬门禁前合入。

SW-T19|P3|Dev-B|合成状态机 enum 化:VideoComposeService STATUS_IDLE/RUNNING/DONE/FAILED int 魔法值收编 enum+守卫(套路同 SW-T16 设计稿)|service/storyboard/VideoComposeService.java、entity/storyboard/StoryboardEpisode.java+tests|合成状态读写全走 enum;DB int 值与 API 行为不变;前端合成状态渲染回归绿
  VALUE: DESIGN-production-state-machine.md §1.6 相邻发现;合成是六路径收口,显式状态机防非法状态写入。
  COST: S(0.5 天)。
  RISK: 低;enum 序数与存量 int 值逐一核对。
  DEPENDENCIES: 设计参照 swarm/DESIGN-production-state-machine.md;与 SW-T16 不同状态机,互不阻塞。

SW-T20|P3|Dev-A|AgentScopeToolAdapter↔AgentToolPermissionPolicy 解环:权限策略反转出接口或抽取共享上下文|service/ai/agentscope/AgentScopeToolAdapter.java、service/ai/agentscope/permission/*+tests|BackendArchitectureGuardTests R4 豁免清零
  VALUE: 清除 service 层唯一类级循环依赖(import 图 Tarjan 实测 1 组)。
  COST: S-M(0.5 天)。
  RISK: 中低;注意工具注册时序对 Policy 构造的影响。
  DEPENDENCIES: 无。

SW-T21|P3|Dev-B|service 层 controller.vo 渗透治理第一批:system/dashboard 小域改自有 DTO|service/system/**、service/dashboard/**、controller/system/vo、controller/dashboard/vo|第一批 VersionInfoRespVO/VideoQueueStatusRespVO 等 4 文件渗透清零;全量渗透清单(29 文件)登记 TECH_DEBT_PLAN
  VALUE: 29 个 service 文件直接 import web 层 VO(RemoteModelVO×10、AiChatStreamRespVO×4…),服务层与 HTTP 契约耦合;小域先行立范式。
  COST: M(第一批 1 天;全域另计)。
  RISK: SSE 信封 AiChatStreamRespVO 是跨层流式契约,第一批禁碰、单独评估;转换层不得引入行为差异。
  DEPENDENCIES: 无;扩至 ai 域前需 Architect 评审。

SW-T22|P3|Dev-A|StoryboardService 跨域直连 Script mapper 收拢:改经 ScriptService 读接口|service/storyboard/StoryboardService.java、service/script/ScriptService.java|script 三 mapper 直连 4 处(96/161/184/453 行)清零;跨域 mapper 规则纳入守护并留豁免清单
  VALUE: StoryboardService 注入 7 个 mapper 跨 script 域直查,绕过缓存与校验;与其 730 行拆分峰值(TECH_DEBT_PLAN 第二梯队)同文件域,顺批处理。
  COST: M(1 天;ScriptService 补只读方法+缓存语义)。
  RISK: ScriptService 新增读方法需评估 @Cacheable/@CacheEvict(AGENTS.md 规则);VideoComposeService 对 productionTake/videoItem 的跨域读属 ARCHITECTURE 规则4 已认可编排,不在本条范围。
  DEPENDENCIES: 建议与 storyboard 页面族后端收尾同批(SW-T12 前端部分之后)。

---


## 红队 R1 升级条目(2026-09-14)
SW-T17|P0|Dev-Sec|API 密钥泄露:/get、/list 补 @PreAuthorize admin;RespVO 脱敏(不回传 apiKey/appSecret/proxyPassword);实体 @ToString.Exclude|ApiConfigController、ApiConfigRespVO、ApiConfig|uitest 调 /get 拿不到密钥
SW-T18|P0|Dev-Sec|SSRF 旁路封堵:ComfyUiInputResourceService.downloadHttp/downloadVideoHttp 与 LocalStorageStrategy.store 接入 PublicHttpUrlValidator;OkHttp 关闭自动重定向或重定向后重校验;统一 VideoCompose 漂移的第二套校验|上述两服务+VideoComposeService|内网 URL 全链路拒绝
SW-T19|P0|Dev-Sec|上传链路:subDir 路径穿越 sanitize;扩展名与 Content-Type 绑定白名单;魔数校验;大小流式处理(不 getBytes 入堆)|LocalStorageStrategy、上传端点|穿越/XSS/内存三向用例全拒

---


## Dev-A 备注(分支 swarm/a2-dev-backend)
- SW-T01 后端已完成(2026-09-14):`uploadBoundMedia` 不再拒绝 `uploaded_audio`,改为走 `ComfyUiInputResourceService.uploadAudios`(URL/DataURI→下载/解码→`ComfyUiNativeClient.uploadAudio`→`/upload/image` 端点 + `ai-fusion-video` 子目录),渲染端 `uploaded_*` 单元素自动解包,无需改 renderer。音频上限 100MB,支持 mp3/wav/m4a/aac/ogg/flac/webm。验收"请求传音频试运行出片含音轨"需要 Supervisor 在 v23 INFINITETALK 工作流上为 LoadAudio 配置 `uploaded_audio` 绑定(referenceAudios 字段)后实测,后端已具备能力。
- SW-T04 已完成(2026-09-14):`AgentMessageAllocator.append` 撞唯一键后重锁会话重读计数再试一次,仍冲突则记 WARN(conversationId/attemptedOrder/runId/projectionKey/role)并按"已落库"返回最后尝试的顺序,不再上抛 DataIntegrityViolation,也不更新会话计数。效果验证:AgentRunMaintenanceScheduler 每5秒 recoverTerminalBatch 原会因该异常反复 log.error,单测 `appendIdempotentSkipKeepsProjectionRecoveryLoopAliveForMaintenanceScheduler` 证明重复投影幂等返回、异常不再外泄,Mono 链正常完成;真机验证看日志不再出现 "Agent run maintenance failed"。决策点(留档):持续冲突按已存在跳过,理论上可能丢弃一条真正的新消息,日志字段足以人工对账;如需强一致可在投影层先按 projection_key 比对后删除重投,归 Supervisor 决策。
- 存量失败记录(非本分支引入):ProjectServiceTests(SW-T10 已立项,集成合入后已转绿)、AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol 标记 ApplicationTimeZoneInitializerTests.java(历史遗留)。
- Round 2 已完成(2026-09-14):ProductionRunReconcileScheduler 每60秒"滞留运行扫描失败: error="根因=`loadTaskStatuses` 的 `Collectors.toMap` 遇 `afv_video_task.status IS NULL` 行抛无消息 NPE(status 列 DDL 仅 DEFAULT 0 非 NOT NULL;JDK21 隐式解引用 NPE 均带详细消息,唯 toMap→HashMap.merge 显式抛无消息 NPE,与空 error 吻合,已本地实证)。整批扫描崩溃→run 14(task 36 已 failed)永远进不了 reconcile。修复:①两处 catch 追加异常对象输出完整堆栈;②loadTaskStatuses 过滤 null id/status 行,未知状态按非终态跳过,不阻塞同批其他运行。单测覆盖:null status 批次容忍(含 run14/task36 场景)、扫描异常不外泄、日志必带堆栈(ListAppender 断言)。遗留:生产库该 NULL status 行的来源未查(需 Supervisor 查 `SELECT id,task_id FROM afv_video_task WHERE status IS NULL`);后续可评估 status 列补 NOT NULL 迁移(本冲刺禁迁移)。

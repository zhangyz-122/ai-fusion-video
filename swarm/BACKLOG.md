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

## 红队 R1 升级条目(2026-09-14)
SW-T17|P0|Dev-Sec|API 密钥泄露:/get、/list 补 @PreAuthorize admin;RespVO 脱敏(不回传 apiKey/appSecret/proxyPassword);实体 @ToString.Exclude|ApiConfigController、ApiConfigRespVO、ApiConfig|uitest 调 /get 拿不到密钥
SW-T18|P0|Dev-Sec|SSRF 旁路封堵:ComfyUiInputResourceService.downloadHttp/downloadVideoHttp 与 LocalStorageStrategy.store 接入 PublicHttpUrlValidator;OkHttp 关闭自动重定向或重定向后重校验;统一 VideoCompose 漂移的第二套校验|上述两服务+VideoComposeService|内网 URL 全链路拒绝
SW-T19|P0|Dev-Sec|上传链路:subDir 路径穿越 sanitize;扩展名与 Content-Type 绑定白名单;魔数校验;大小流式处理(不 getBytes 入堆)|LocalStorageStrategy、上传端点|穿越/XSS/内存三向用例全拒

# BACKLOG(统一任务池)
格式:ID|P|OWNER|TITLE|FILES|ACCEPTANCE

SW-T01|P1|Dev-A|INFINITETALK 按请求音频:uploadBoundMedia 支持 uploaded_audio(转DataURI),出v23按请求驱动|service/ai/comfyui/ComfyUiGenerationExecutor.java、ComfyUiInputResourceService.java+tests|请求传音频试运行出片含音轨
SW-T02|P1|Dev-B|T10 六条UI缺陷:生图死链/SeedVR2入口/分镜按钮aria与hover误触/生产抽屉文案/登录页品牌|generate/image、videos、storyboards行内按钮、login页|六项逐一可核
SW-T03|P1|QA|E2E 定位器适配集成版UI(5失败用例:路径1/3/4/5/6)|e2e/**|playwright 全绿
SW-T04|P2|Dev-A|AgentMessageAllocator 完全幂等化(重复投影跳过而非抛错)|service/ai/run/AgentMessageAllocator.java、AgentMessageProjectionService.java+tests|维护调度不再报错
SW-T05|P2|Dev-B|资产回收站后端三接口(已删列表/恢复/物理删除)+前端接真接口替换localStorage方案|asset controller/service+recycle-bin-view.tsx+lib/api/asset.ts|删→回收站可见→恢复→物理删全链路
SW-T06|P2|QA|新功能边界测试:chunkChars边界/SRT极端用例(空对白超长文本)/系统状态降级|src/test/**|全绿
SW-T07|P2|Architect|状态与架构体系初建+巨型文件拆分计划(990行dashboard页、971行workbench)|swarm/*.md、docs|计划文档可执行
SW-T08|P3|Red Team|安全与性能扫描报告(SSRF绕过/N+1/170集树性能/内存)|swarm/报告|可执行发现清单
SW-T09|P3|Dev-B|通知面板僵尸会话后端标记failed(DB running但Redis NONE)|task service/controller|僵尸不再显示
SW-T10|P3|Dev-A|ProjectServiceTests 存量失败修复+滞留阈值常量参数化|ProjectServiceTests、GenerationTaskReaper|存量失败清零
SW-T11|P4|Red Team|产品进化提案:批量镜头操作/诊断面板/字幕导入/模板库|swarm/提案|Architect 评估

## Dev-A 备注(分支 swarm/a2-dev-backend)
- SW-T01 后端已完成(2026-09-14):`uploadBoundMedia` 不再拒绝 `uploaded_audio`,改为走 `ComfyUiInputResourceService.uploadAudios`(URL/DataURI→下载/解码→`ComfyUiNativeClient.uploadAudio`→`/upload/image` 端点 + `ai-fusion-video` 子目录),渲染端 `uploaded_*` 单元素自动解包,无需改 renderer。音频上限 100MB,支持 mp3/wav/m4a/aac/ogg/flac/webm。验收"请求传音频试运行出片含音轨"需要 Supervisor 在 v23 INFINITETALK 工作流上为 LoadAudio 配置 `uploaded_audio` 绑定(referenceAudios 字段)后实测,后端已具备能力。
- SW-T04 已完成(2026-09-14):`AgentMessageAllocator.append` 撞唯一键后重锁会话重读计数再试一次,仍冲突则记 WARN(conversationId/attemptedOrder/runId/projectionKey/role)并按"已落库"返回最后尝试的顺序,不再上抛 DataIntegrityViolation,也不更新会话计数。效果验证:AgentRunMaintenanceScheduler 每5秒 recoverTerminalBatch 原会因该异常反复 log.error,单测 `appendIdempotentSkipKeepsProjectionRecoveryLoopAliveForMaintenanceScheduler` 证明重复投影幂等返回、异常不再外泄,Mono 链正常完成;真机验证看日志不再出现 "Agent run maintenance failed"。决策点(留档):持续冲突按已存在跳过,理论上可能丢弃一条真正的新消息,日志字段足以人工对账;如需强一致可在投影层先按 projection_key 比对后删除重投,归 Supervisor 决策。
- 存量失败记录(非本分支引入):ProjectServiceTests(SW-T10 已立项)、AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol 标记 ApplicationTimeZoneInitializerTests.java(历史遗留)。

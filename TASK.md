<<<<<<< HEAD
# 冲刺任务 T1:后端缺陷修复:消息投影竞态 + 场次移动分集静默忽略

---

# 冲刺任务 T6:仪表盘 B04 深化:真实快捷入口与明细跳转
=======
# 冲刺任务 T9:安全加固 M03:上传与执行面收口
>>>>>>> sprint/T9-security-hardening

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
<<<<<<< HEAD
修复两个已诊断的后端缺陷。

### 缺陷1:AgentMessageAllocator 投影竞态
- 现象:AgentRunMaintenanceScheduler 每 5 秒 DataIntegrityViolation,
  根因是 projections.recoverTerminalBatch 投影消息时
  (conversation_id, message_order) 唯一键冲突(见技术债文档)。
- 修复方向:insert 冲突时重试读取 nextMessageOrder 再插入;
  或改用 UPDATE 计数器原子递归(SELECT ... FOR UPDATE 事务)。
### 缺陷2:PUT /api/storyboard/scene 对 episodeId 变更静默忽略
- 复现:scene 37 episode_id=6,PUT episodeId=8 返回 success 但库值不变。
- 修复方向:排查 SceneUpdateReqVO→StoryboardScene 的 MapStruct 映射
  与 updateById 更新策略;修复后显式校验场景与分集归属一致性。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/run/AgentMessageAllocator.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/AgentMessageService.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/storyboard/StoryboardService.java(updateScene 部分)
- ai-fusion-video/src/test/java/com/stonewu/fusion/service/**(新增测试)
## 禁区
- 不改前端;不加数据库迁移;不改其他服务文件。
## 验收
- 新增单测覆盖竞态重试与 episodeId 变更;./mvnw compile 通过;
  相关 StoryboardServiceTests 全绿。

---

1. 快捷动作四卡片改为真实能力入口(生图→/generate/images、
   视频→/generate/videos、声音→/generate/audios、生产→/production)。
2. "进行中与待办"条目点击深链细化:SCRIPT_PARSE→/projects/{id}(剧本页)、
   PRODUCTION_RUN→/production。
3. 最近项目卡片显示项目真实分集/资产计数(复用现有接口)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/dashboard/**(本目录内自由)
## 禁区
- 不改 lib/api 下既有文件(可新增);
  不改 app-header/sidebar-nav(导航已预置)。
## 验收
- tsc/eslint/build 通过;全部入口真实可达(手动冒烟)。
=======
1. 上传/导入校验收口:检查所有接收文件或 URL 的端点
   (图片上传、参考图 URL 拉取、工作流导入)的类型/大小/内网地址限制;
   参考图 URL 禁止内网地址(127.0.0.1/10.x/172.16-31/192.168/hostMetadata)。
2. 工作流导入(JSON)增加深度与节点数上限,防嵌套炸弹。
3. 全局异常日志不含密钥(抽查 apiKey 脱敏)。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/security/**
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/generation/ReferenceImageTransportService.java(校验部分)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/comfyui/ComfyUiWorkflowDocumentService.java(上限部分)
- ai-fusion-video/src/test/java/**(新增测试)
## 禁区
- 不改生成策略与渲染逻辑;不改前端;不加数据库迁移。
## 验收
- 新增单测:内网 URL 拒绝、超限 JSON 拒绝;既有测试全绿。
>>>>>>> sprint/T9-security-hardening


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

<<<<<<< HEAD
## 决策点与执行记录(T1 子代理追加,2026-09-13)

### 决策1:缺陷2 修复需要例外触碰 StoryboardSceneUpdateReqVO 与 StoryboardConvert(需任务群确认)
- 原因:PUT /api/storyboard/scene 静默忽略 episodeId 的根因有三层——
  1) `StoryboardSceneUpdateReqVO` 缺少 `episodeId` 字段,请求 JSON 在反序列化时被丢弃;
  2) `StoryboardConvert.convert(StoryboardSceneUpdateReqVO)` 显式 `@Mapping(target="episodeId", ignore=true)`;
  3) `StoryboardService.updateScene` 直接 `updateById` 且无归属校验。
- 仅改 StoryboardService 无法让 episodeId 进入服务层,任务书修复方向亦明确指向该 MapStruct 映射。
- 处理:按最小改动新增 VO 字段、移除 ignore 注解(两者为 controller 层文件,不在"不改其他服务文件"禁区字面范围内,但超出允许文件清单,故留档声明)。
- 同时说明:updateById 沿用 MyBatis-Plus NOT_NULL 更新策略,episodeId 缺省=不移动,保持局部更新语义。

### 缺陷1 修复方案说明
- `AgentMessageAllocator.append` 在持有会话行锁(SELECT ... FOR UPDATE)的基础上:
  1) 插入前将会话计数器与库内实际最大 message_order 对账(`resolveInsertOrder`),治愈计数器落后于已落库消息导致的每 5 秒确定性 DataIntegrityViolation;
  2) insert 撞 `uk_agent_message_conv_order` / `uk_agent_message_projection_key` 唯一键时,重锁会话行重读计数后有界重试(最多 3 次),耗尽后原样抛出。
- 遗留:投影并发下 `projection_key` 唯一键冲突(两个事务同时通过 selectByProjectionKey 检查)仍会最终抛错而不是幂等跳过;幂等化需改 AgentMessageProjectionService.persistProjection(不在本任务允许文件内)。

### 测试结果
- `./mvnw compile` 通过。
- StoryboardServiceTests 16/16 绿(含新增 3 个 updateScene 用例);新增 AgentMessageAllocatorTests 3/3 绿;AgentMessageServiceTests 2/2 绿。
- 全量 service 包测试:498 个用例,仅 2 个与本次改动无关的失败——ProjectServiceTests(在未含本改动的基线上同样失败,系存量缺陷)与 ProjectWorkspaceCacheTests(依赖 MySQL 的上下文测试,任务书规定的跳过清单)。

---

---

## T6 完成记录(2026-09-13)

### 变更文件
- `ai-fusion-video-web/app/(dashboard)/dashboard/page.tsx`:快捷操作四卡片改为
  生图(/generate/images)、视频(/generate/videos)、声音(/generate/audios)、
  生产(/production);最近项目区块改用 `<RecentProjects>`;移除 assistant/旧入口相关代码。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/recent-projects.tsx`(新增):
  最近项目列表,逐项目并发请求 `GET /api/project/{id}/workspace-overview`(分集数)与
  `GET /api/asset/all?projectId={id}&size=1`(资产数 total),行内展示"N 集 · N 资产"。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/section-header.tsx`(新增):
  从 page.tsx 抽出的共享区块标题。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/activity-section.tsx`:
  深链细化——SCRIPT_PARSE→`/projects/{projectId}/scripts`(无项目回退 /projects)、
  PRODUCTION_RUN→`/production`、IMAGE_TASK→`/generate/image`、VIDEO_TASK→`/generate/video`;
  kindLabels 补充"剧本解析"。

### 需要决策/集成者处理的问题(按任务书要求追加,未自行改动)
1. 【基线损坏,阻塞 tsc 与剧本页】`sprint/base` 上
   `app/(dashboard)/projects/[id]/scripts/page.tsx:23` 仍 import
   `./_components/story-to-script-button`,但该文件已被 7a8c008 删除。
   后果:全仓 `tsc --noEmit` 报唯一一处 TS2307;运行时访问 `/projects/{id}/scripts` 500
   (本任务 SCRIPT_PARSE 深链的目标页)。属于 projects/** 禁区,本任务未修复,
   需基线/剧本任务负责人删掉该 import(或恢复组件)后集成。
2. 【导航旧地址】`components/dashboard/sidebar-nav.tsx` 的"生图/生视频"仍指向旧单数路由
   `/generate/image`、`/generate/video`(属禁区未改);建议统一为复数工坊入口或保留双入口,
   由导航任务决策。
3. 【产品确认】原快捷卡片中的"新建项目/管理素材/融光助手"按任务书被四个能力入口替换;
   仪表盘上不再有助手入口卡片(助手仍从 Header/其他入口可达)。如需保留请告知补回。
4. 【深链粒度受禁区限制】PRODUCTION_RUN 只能到 /production 列表(生产页不支持按 run 高亮);
   IMAGE_TASK/VIDEO_TASK 到 /generate/image|video 工作台,不能按 taskId 定位
   (工作台不读 URL 参数)。如需精确定位需要生产页/工作台配合加参数(超出本任务禁区)。
5. 【计数加载策略】项目计数为挂载后二次请求(最多 6 个项目×2 个现有接口),加载完成前不显示;
   单个项目计数请求失败时仅隐藏该行计数,不影响列表。

### 验证结果
- `corepack pnpm exec tsc --noEmit`:仅剩上述第 1 条基线既有错误(TS2307,scripts/page.tsx),
  本次改动的 4 个文件 0 错误。
- `corepack pnpm exec eslint`(4 个改动文件):0 问题。
- 本地 dev(代理 docker 平台 API)冒烟:
  - zhangyz:/dashboard 200;四入口目标页 /generate/images|videos|audios、/production 均 200;
    /api/dashboard/activity 返回 SCRIPT_PARSE(projectId=5)与 PRODUCTION_RUN×3,深链分别指向
    /projects/5/scripts 与 /production;/api/asset/all?projectId=1 → total=10、
    workspace-overview(projectId=3) → episodes=4,计数接口真实有数。
  - uitest:/dashboard、/production 均 200;activity.running=[](空态路径),项目计数 0/0 正常返回。
  - `/projects/5/scripts` 返回 500:即上述第 1 条基线既有问题,与本任务改动无关。
=======
## 决策与遗留记录(T9 执行后追加,2026-09-13)

### 决策
1. SSRF 校验收口在 `security/http/PublicHttpUrlValidator`(新文件,位于允许的 security/** 内),
   `ReferenceImageTransportService` 在"选择传递方式"与"服务端拉取(转 Data URI)"两处都强制校验。
2. 行为变更:参考图 http(s) URL 指向本机/回环/内网/链路本地时一律拒绝,
   即使模型启用 Data URI 也不再由平台代为拉取内网资源。原用例
   `convertsLoopbackHttpUrlToDataUriEvenWhenUrlIsAllowed` 编码的正是该漏洞行为,已改为拒绝用例。
3. 防 DNS rebinding 采用基础版:先 `getAllByName` 解析、再对全部解析结果逐一校验(fail-closed)。
   连接阶段二次解析的 TOCTOU 需连接级 IP 固定,留作技术债。
4. 主机名后缀 `.internal` 一律拒绝(覆盖 metadata.google.internal 等),`.local`/`.localhost` 沿用并保留。
   公网域名校验依赖服务器 DNS;DNS 故障时公网 URL 也会被拒(生成链路本就依赖外网,可接受)。
5. 工作流导入:`ComfyUiWorkflowDocumentService` 新增 JSON 嵌套深度上限 64(解析后校验,防嵌套炸弹);
   500 节点上限从 normalize 收口到 `parseApiWorkflow`,使 `parseInputBindings/parseOutputBindings` 路径同样受限。
6. 日志脱敏越界修复(按"抽查日志/异常路径不含 apiKey……发现即修"执行,特此声明例外):
   - `TokenService.deserializeSession` 不再输出会话原文(在允许的 security/** 内);
   - `AbstractOpenAiCompatibleVideoStrategy.parseConfig` 失败日志改为仅输出长度(越界);
   - `ApiConfig`(apiKey/appSecret/proxyPassword)、`StorageConfig`(secretKey)、`User`(password)
     增加 `@ToString.Exclude`(越界,防未来任意日志/异常串出 toString)。
   其余全局抽查未发现直接打印 apiKey 的调用点(Gemini key 走 header、AbstractAiProvider 仅打 url/响应体)。
7. 单测确定性:涉及公网 URL 的用例一律使用 IP 字面量或注入 Fake DNS 解析器,
   不依赖真实网络;`0x7f.0.0.1`、`127.1` 等非规范 IPv4 写法按 Java 归一化结果校验。

### 既有失败(与本任务无关,已在未改动基线上复现)
- `AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol`
  (源码树扫出 `ApplicationTimeZoneInitializerTests.java`,基线即失败);
- `ProjectServiceTests.listAccessibleByUserUsesCurrentTeamScope`
  (Mockito TooManyActualInvocations,基线即失败);
- 环境类不适用本机:`FusionVideoApplicationTests`、`AiAgentToolRegistrationTests`、
  `ProjectWorkspaceCacheTests`(需 MySQL/Redis);`*IT` 集成测试需 docker/Redis,默认不在 surefire 范围。

### 事故记录(供集成者与其他任务知悉)
- 本 worktree 执行 `git stash push/pop` 期间,因 stash refs 跨 worktree 共享,与并行任务发生竞态,
  误 pop 了 T2-backend-resilience 的 stash 条目。T2 的内容已完整备份至 `E:/RG-sprint/_stash-handoff-T9/`,
  并用 `refs/holds/t2-backend-resilience-stash-b59a8344` 固定(防 gc)。
  T2 可在其 worktree 执行 `git stash apply b59a8344046d807097cffb0a0f11174549bf628a` 恢复,
  或直接从备份目录取回文件。本 worktree 状态已通过自身的 dangling stash 提交(b905fc05/3f0e1f72)完整还原。
>>>>>>> sprint/T9-security-hardening

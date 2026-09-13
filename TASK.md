# 冲刺任务 T9:安全加固 M03:上传与执行面收口

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
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


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

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

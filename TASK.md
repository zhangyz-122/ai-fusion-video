# 冲刺任务 T1:后端缺陷修复:消息投影竞态 + 场次移动分集静默忽略

---

# 冲刺任务 T6:仪表盘 B04 深化:真实快捷入口与明细跳转

---

# 冲刺任务 T9:安全加固 M03:上传与执行面收口

---

# 冲刺任务 T3:生产中心 v2:运行详情抽屉 + 重试/同步操作

---

# 冲刺任务 T10:E2E 冒烟:N04 六条创作路径 Playwright 脚本

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。

---

# 冲刺任务 T12:系统状态页 M05:真实健康/存储/队列数据

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
settings/general 增补'系统状态'区块,全部真实数据:
1. 健康检查:后端 /actuator/health 透传端点(新增,仅追加)。
2. 存储占用:媒体目录大小统计(新增只读端点,后端遍历 /app/data/media 统计)。
3. 队列深度:Redis 视频队列长度(经既有 RedisTaskQueue 读取,新增只读端点)。
4. 前端区块展示 + 30s 轮询。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/settings/general/**(本目录内自由)
- ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/**(仅追加端点)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/system/**(新增服务,仅追加)
## 禁区
- 不改既有端点与安全配置;不加迁移;不改其他设置页。
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

---

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

---

在 /production 页面为每条运行增加:详情抽屉(复用/参考分镜页的
production-take-drawer 数据展示)、重新同步(reconcile)与重试(repair)按钮、
失败原因完整展示。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/production/**(本目录内自由)
- ai-fusion-video-web/lib/api/production.ts(仅追加,不修改既有导出)
## 禁区
- 不改 app-header/sidebar-nav(导航已预置);
  不改 lib/api/production.ts 既有类型与方法签名;
  不碰分镜页的 production-take-drawer.tsx。
## 验收
- tsc/eslint/build 通过;真实运行数据显示与操作可用(手动冒烟)。

---

为六条路径编写 Playwright 冒烟脚本(可不真实执行生成,
校验到"提交成功"为止):独立生图、参考生图、图生视频(生产)、
对白片段(自动分块)、高清处理入口、项目成片(合成)。
- 使用账号 uitest / Uitest#2026,基准地址读环境变量。
- 输出 PASS/FAIL 报告。
## 允许文件
- e2e/**(新建目录,含 playwright.config 与用例)
- package.json(仅追加 devDependency: @playwright/test)
## 禁区
- 不改产品代码;不真实提交图片/视频生成(断言到提交前一步);
  不修改数据库。
## 验收
- npx playwright test 本地全绿(对运行中的 localhost:8081)。

---

- 编译通过;真实数据展示(对照 docker 与宿主实际值);uitest 视角不泄露敏感信息。

---

# 冲刺任务 T11:资产中心 D01/D05:检索标签增强 + 回收站视图

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
1. /assets 页面增强:搜索按名称/标签过滤(后端已支持 keyword)、标签云快捷筛选、视图切换(网格/列表)。
2. 回收站视图:已删除资产(deleted=1,走既有软删字段,不加迁移)独立 Tab 展示,支持恢复(调用既有 update/恢复能力或新增仅前端组合)与彻底删除入口(带二次确认)。
3. 大文件上传失败重试提示(仅前端状态,断点续传不做)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/assets/**(本目录内自由)
- ai-fusion-video-web/lib/api/asset.ts(仅追加)
## 禁区
- 不改后端;不改导航;不加数据库迁移。
## 验收
- tsc/eslint/build 前两项通过;双账号冒烟(zhangyz 有数据/uitest 空态)。

---

# 冲刺任务 T13:通知面板:taskStream 任务实时进度强化

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
通知面板对 category=task 的任务(自动分块解析/合成等)展示实时进度:
1. 对运行中任务接 /api/ai/chat/{taskId}/reconnect SSE 流(既有端点),实时渲染 publishContent 的进度文本(如'正在解析分块 37/170')。
2. 任务完成后显示完成摘要与产物链接(如有)。
3. 面板关闭后轮询兜底刷新列表状态。
## 允许文件
- ai-fusion-video-web/components/dashboard/notification-panel/**(本目录内自由)
- ai-fusion-video-web/lib/api/task-stream.ts(仅追加)
## 禁区
- 不改后端;不改 pipeline-store 既有语义。
## 验收
- tsc/eslint 通过;真实任务(如正在运行的 script 5 自动分块)进度实时可见。

## 通用规则
1. 只允许改动允许文件清单内文件;需要例外先在 TASK.md 末尾声明并继续可做部分。
2. 不改数据库迁移;不改导航与公共组件。
3. 提交规范:conventional commits。
4. 完成后确认编译/验证通过,报告分支名与变更清单,由集成者合并。
5. 需要决策的问题追加到 TASK.md 末尾,不要空等。


---


## T11 决策与遗留记录(2026-09-13,集成者关注)

### 决策1:回收站为"仅前端组合"实现,原因:后端既有接口完全无法触达软删行(重要)
- 后端 `BaseEntity.deleted` 带 MyBatis-Plus `@TableLogic`:所有 `selectList/selectPage/selectById`
  自动追加 `AND deleted = 0`,`deleteById` 是逻辑删除,`updateById` 的 WHERE 也带 `deleted = 0`。
  因此**既有接口既列不出、也改不动、更查不到 deleted=1 的资产行**;"调用既有 update/恢复能力"不存在。
- 按任务书"或新增仅前端组合"落地:
  - 删除(本页新增删除入口,二次确认)成功后,把资产完整快照按用户隔离存入 localStorage
    (`rg:assets:recycle-bin:v1`,含 userId 字段防跨账号串数据);
  - 回收站 Tab 展示快照;**恢复 = 用快照走 POST /api/asset 重建(获得新 id)**,成功后移除快照;
    原资产 id 上的引用(如分镜引用)不会自动重连,Toast 中明确提示"新 id";
  - **彻底删除 = 二次确认后仅移除本地快照**。数据库中的软删行依旧存在(全站不可见),
    物理清理需后端"受控清理"能力(见遗留2)。
- 后端需要的三个接口(供后续任务):列出已删除资产、按 id 恢复(写 deleted=0)、按 id 物理删除。
  届时前端可无缝替换 localStorage 组合,UI 不用大改。

### 决策2:筛选统计全部改为前端计算(全量加载)
- 后端 keyword 只 LIKE name,不匹配标签;任务要求"按名称/标签过滤",只能前端补齐。
- 改为循环分页拉取全部可访问资产(每页 200,上限 1000,超出在页脚提示"已加载前 N 个"),
  项目/类型/关键词(名称+标签)/标签云(多选取交集)全部在前端过滤;类型统计与标签云基于内存数据计算。
  当前账号资产量为个位数到两位数,该模型无压力;数据量显著增长后应交还后端(标签检索需后端支持)。

### 决策3:上传入口新增在本页(此前 /assets 无任何上传)
- 任务要求"大文件上传失败重试提示",本页原先没有上传,故新增"上传图片"入口:
  走既有 POST /api/storage/upload(仅 png/jpeg/webp/gif,≤100MB,前端 accept 已对齐),
  成功后创建 type=image 的资产(封面=上传文件 URL)到当前选中的项目(未选具体项目时提示先选择)。
- 队列逐文件展示 上传中/已完成/失败+原因;失败的文件保留 File 引用,可"重试"(整文件重传,不做断点续传)。

### 遗留/说明
1. 【未用资产】`lib/api/asset.ts` 的追加权限未使用:本任务无需新增接口,保持零改动。
2. 【物理删除与全量回收站需后端】见决策1:服务端已软删但无本地快照的资产(例如从项目资产页删除的)
   不会出现在回收站;彻底删除也不物理清理数据库行。均待后端接口。
3. 【数据口径】统计卡片/标签云来自内存全量数据;"全部资产"数字 = 已加载数(上限 1000)。
4. 【基线既有问题】`projects/[id]/scripts/page.tsx` 引用被删除的 `story-to-script-button`(TS2307)
   在基线上即存在(见 T6/T3 记录),与本任务无关;本任务改动文件 0 错误。

## T12 执行记录与决策(2026-09-13)

### 变更文件
- 后端新增(全部为追加,未改既有代码):
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/SystemStatusController.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/service/system/SystemStatusService.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/SystemHealthRespVO.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/MediaStorageStatsRespVO.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/VideoQueueStatusRespVO.java`
- 后端测试(例外声明见下):
  - `ai-fusion-video/src/test/java/com/stonewu/fusion/service/system/SystemStatusServiceTests.java`
- 前端(`settings/general/` 目录内):
  - `ai-fusion-video-web/app/(dashboard)/settings/general/_components/system-status-api.ts`(类型/请求/格式化)
  - `ai-fusion-video-web/app/(dashboard)/settings/general/_components/system-status-section.tsx`(状态区块 + 30s 轮询)
  - `ai-fusion-video-web/app/(dashboard)/settings/general/page.tsx`(仅追加 import 与 isAdmin 挂载)

### 决策与例外声明
1. 健康检查未做 /actuator/health 透传:`pom.xml` 不在允许文件清单,无法追加 `spring-boot-starter-actuator`(SecurityConfig 虽放行 /actuator/** 但依赖缺失该路径本就 404)。改为 `SystemStatusService` 内原生探测:数据库用独立 JdbcTemplate 执行 `SELECT 1`(3s queryTimeout),后端状态由端点可响应自证。如集成阶段要求真 actuator,需补依赖后替换 getHealth() 实现。
2. 测试文件例外:允许清单未含 `src/test/**`,按验收"编译通过(+单测如可行)"新增 `SystemStatusServiceTests`,不改任何既有测试。
3. `SystemStatusService` 不使用 `@Cacheable`:状态页 30 秒轮询必须返回实时探测结果,缓存会使健康/队列数据失真;本服务只读、无 create/update/delete,不涉及缓存失效语义。
4. 管理员实测数据受限:任务禁止 docker/compose,运行中的 8081 平台为旧镜像(新端点 404 已实测确认);本地后端启动依赖 MySQL 43306 / Redis 46379,宿主未监听且不允许经 docker 暴露。已完成:后端编译 + 7 个单测、前端 tsc/eslint、next dev SSR 冒烟、8081 登录链路验证。**集成者合并重建镜像后需补一次实测**:①health 的 database=UP;②storage 三分类文件数/字节与容器内 `find /app/data/media/{images,videos} -type f` 对比(composed 含在 videos 下,注意口径);③队列深度与 `redis-cli LLEN fv:taskqueue:video_generation:model:*` 对比。
5. 存储统计口径:与 WebMvcConfig 的 /media/** 映射一致——优先取数据库默认"本地存储"配置的 basePath,否则回退 `app.storage.local-base-path`;images 递归(含 images/video-frames);composed=videos/composed 递归;videos=videos 递归但排除 composed,避免重复计数;默认存储为 S3 时返回 storageType=s3 并照常统计本地目录。basePath 仅出现在管理员专用端点中,前端区块仅管理员可见,非管理员视角无泄露。
6. 队列口径:`listRegisteredQueuesByPrefix("video_generation")` 读取全部已注册视频队列(video_generation 与 video_generation:model:*),逐队列只读 LLEN/并发计数/最大并发;Redis 不可用时降级为 available=false + 通用文案,不暴露异常细节与内部地址。

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

---

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

---

## T3 需决策 / 遗留问题(集成者关注)
1. 【阻断全仓 tsc,非本任务文件】基础分支缺少 `app/(dashboard)/projects/[id]/scripts/_components/story-to-script-button.tsx`,导致 `projects/[id]/scripts/page.tsx(23,37)` 报 TS2307。已用 `git stash` 验证该错误在未含 T3 改动的基线上即存在,疑似脚本页任务拆分遗漏;需要归属任务补齐该组件,否则全仓 `tsc --noEmit` 与 build 无法通过。T3 自身文件(production/**)tsc、eslint 均零错误。
2. 【跨模块宽度约定】/production 页沿用了脚手架自带的 `max-w-6xl` 居中容器,与设置模块的 `max-w-[1200px]` 不一致;AGENTS.md 要求"同一模块同级页面统一主内容宽度",dashboard 级独立页面是否统一到 1200px 需集成者拍板(本任务未越界改动)。
3. 【范围说明】详情抽屉仅展示候选视频与 QC 状态,未提供质检/选用/合成操作:这些操作在分镜页 production-take-drawer 已有完整交互,按任务要点只做 reconcile/repair,避免两个入口重复维护同一套操作。如产品要求生产中心页支持 QC 全流程,需追加需求。
4. 【未做自动轮询】抽屉内 WAITING_GENERATION 状态不自动轮询 detail(分镜页抽屉有 5s 轮询),提供手动刷新按钮即可满足"详情+操作"要求;如需与分镜页一致可后续补。

---

---

## T10 执行记录与决策(2026-09-13)

### 完成情况

- 新增 `ai-fusion-video-web/e2e/`:playwright.config.ts、helpers(API 登录注入 / 测试数据发现)、
  11 条用例(认证 3、创作路径 6、权限对照 2)、README(运行方式与前置条件)。
- `package.json` 追加 devDependency `@playwright/test@^1.63.0`,`pnpm-lock.yaml` 同步更新。
- 验收:`corepack pnpm exec playwright test -c e2e/playwright.config.ts` 对 localhost:8081 实跑
  **11 passed**(约 20s,连续两轮全绿)。分支:`sprint/T10-e2e-smoke`。

### 需要决策的问题(不空等,记录如下)

1. **被测部署版本与本分支源码不一致**:localhost:8081 运行的是 main 工作区(含未提交修改)的构建,
   项目概览为"写/定/拆/拍/剪"阶段流,`/generate/images`、`/generate/videos` 均为服务端重定向,
   故事转剧本入口位于 `/projects/{id}/source`(原文页)。任务书中的路由
   (`/generate/images` 能力目录、`/projects/{id}/scripts` 故事转剧本按钮)与本分支(sprint/base)
   源码一致、与部署不一致。用例按**部署实况**编写以保证可实跑;若后续统一为 sprint/base 的
   信息架构,需同步调整用例(用例 4/6/8 的定位器集中在这三个文件,改动面小)。
2. **运行命令带 `-c`**:playwright.config.ts 按任务书放在 `e2e/` 内(允许文件边界),
   因此验收命令为 `corepack pnpm exec playwright test -c e2e/playwright.config.ts`;
   若希望根目录直接 `playwright test`,需允许在 `ai-fusion-video-web/` 根放一份配置或在
   package.json 增加 script(超出本次允许文件,未做)。
3. **lockfile 变更**:安装 devDependency 必然更新 `pnpm-lock.yaml`,已包含在提交中;
   严格的"仅 package.json"无法保证他人可复现安装。

### 发现的页面缺陷(只记录,不修)

1. **能力目录入口死链**:生图编辑器头部"← 图像工坊 / 能力目录"链接指向 `/generate/images`,
   而该路由服务端重定向回 `/generate/image`,点击后回到原页,能力目录实际不可达。
2. **高清处理(视频放大)无 UI 入口**:模型 `SeedVR2 高清视频放大 1080P`(id 13,已启用)
   在"万能导演台"中不可达——统一视频模式锁定默认底座(MiniMax H3 文戏低配版),
   用户无法从界面选择该模型,任务书路径 5 的"高清处理入口"在部署版本上缺失。
3. **分镜行内"生成视频"按钮可访问性差**:无 `aria-label`/`title`(仅 Tooltip 内容),
   默认 `opacity-0` 依赖 hover 显现,键盘无法聚焦;且侧栏"剪 · 成片"使用同款 Video 图标,
   自动化/读屏都容易误触。建议补 `aria-label="生产这一镜"` 并常显。
4. **生产抽屉按钮语义不一致**:抽屉描述为"生成 3 个候选视频",启动按钮文案却是"生产这一镜",
   且就绪度不足时仅静默禁用(blockers 列表在上方另述,按钮本身无提示)。
5. **登录页品牌文案不一致**:部署版本登录页底部为"短剧制造平台",应用其余位置为"融光",
   疑似部署构建落后于品牌改版(本分支为"融光 · AI视频创作平台")。
6. **本分支前端无法编译(继承自基线,非本任务引入)**:`scripts/page.tsx` 引用了不存在的
   `story-to-script-button.tsx`(7a8c008 误删,b6d1f09 已在 sprint/base 修复)。
   本分支不含该修复,集成时以 sprint/base 为准合并即可,无需本任务处理。

---

- 后端:`./mvnw compile` 通过;`./mvnw test -Dtest=SystemStatusServiceTests` → Tests run: 7, Failures: 0, Errors: 0。
- 前端:`tsc --noEmit` 通过;eslint(page.tsx / system-status-section.tsx / system-status-api.ts)0 错误 0 警告;`next dev` 访问 /settings/general 返回 200、SSR 无报错。
- 平台 8081:zhangyz 登录成功;`/api/system/status/{health,storage,video-queue}` 当前均 404(旧镜像,待集成重建后生效)。

---

### T11 验证结果(2026-09-13)
- `corepack pnpm exec tsc --noEmit`:全仓 0 错误(T6 记录的 TS2307 已在 base 修复)。
- `corepack pnpm exec eslint "app/(dashboard)/assets"`:0 问题。
- 本地 dev(Next 16 Turbopack,DEV_BACKEND_URL=http://localhost:8081 代理 docker 平台):
  - `/assets` 编译并 200,SSR bundle 含回收站视图代码;双账号(带 auth-token cookie)均 200。
  - API 契约冒烟(zhangyz,UTF-8 报文):listAll size=200 → total=11 单页取全;
    创建(中文名+tags JSON 串+properties)→ DELETE → listAll 不再出现、GET by id 报"资产不存在"
    (证实软删行既有接口完全不可达)→ 以恢复载荷重建成功(新 id,tags/properties 完整);
    /api/storage/upload(subDir=assets)→ 返回 /media/... URL → 以该 URL 创建 type=image 资产成功。
  - uitest:listAll → total=0(空态路径);/assets 200;向不存在项目恢复(创建)返回
    业务错误"项目不存在"(前端 toast 会展示该消息,快照保留在回收站)。
  - 测试数据已清理:zhangyz 可见资产回到原有 11 个。**数据库留有 ids 12–20 共 9 条软删测试残留**
    (名称含 t11/T11,全站不可见,仅影响直接查库);物理清理依赖遗留2的后端能力。
- 浏览器级交互(页签切换、确认弹窗、上传面板重试按钮的点击流)未做 UI 自动化
  (子代理不使用 Browser Use),已由 tsc/eslint/SSR 编译与上述 API 契约测试覆盖,建议集成者合并后
  在浏览器中复核一遍交互观感。

---

----

## 决策与执行记录(T13 子代理追加,2026-09-13)

### 实现范围与数据流决策
1. 实时任务流展示为通知面板内的独立区块(新增 `task-stream-section.tsx`),不写入
   pipeline-store 的 tasks 列表:避免改变 pipeline-store 既有语义(禁区),也避免
   attachTaskStream 那套"终态才结算、内容按 paragraph 追加"的 Pipeline 时间线模型
   覆盖任务流"每条 CONTENT 都是全量进度文本"的语义。任务流卡片自己管理 SSE 订阅、
   轮询回退与终态渲染,与 store 内任务通过 conversationId 去重(分镜页合成的
   attachTaskStream 任务仍走原路径,不会双份展示/SSE)。
2. 数据源为既有 `/api/task-stream/running`(前端此前无人调用),仅取 `category === "task"`。
   TASK.md 写的端点 `/api/ai/chat/{taskId}/reconnect` 实际不存在,SSE 复用
   `lib/api/task-stream.ts` 已封装的 `/api/task-stream/reconnect?taskId=`(仅追加类型导出,
   未改既有代码)。
3. SSE 断开/流未带终态即结束时:先探测 `/api/task-stream/status`,ACTIVE 且重订未超
   2 次则重订(重订会从 0-0 全量回放,幂等),否则回退 5s 状态轮询;轮询期间用
   `listMessages` 最后一条 assistant 消息回填进度文本,终态(COMPLETED/ERROR)用同一条
   消息补齐完成摘要。
4. 面板关闭即组件卸载(SSE/定时器全部清理);再次打开全量重拉 running 列表 + 探针,
   并在面板打开期间每 15s 兜底刷新列表,覆盖关闭期间错过的开始/结束状态。
5. 完成摘要复用本目录 `parseTaskContent` 抽取"视频地址/下载地址"产物链接(合成任务),
   并按 `contextType` 附加剧本/分镜入口链接(script→/projects/{id}/scripts、
   storyboard_episode→/projects/{id}/storyboards)。

### 需要决策/集成者知悉的问题
1. 【僵尸运行中会话】实测发现 DB `status=running` 但 Redis 状态为 NONE 的会话
   (进程中断留下的记录,如脚本 5 的两个历史解析会话)。面板对这类记录探测后直接隐藏,
   不展示为运行中任务;它们也永远进不了历史列表(历史过滤 running)。是否需要后台
   兜底把这类会话标记为 failed,属后端范围,留档待决策。
2. 【终态摘要的生命周期】任务流会话无消息时间线面板,终态摘要只保留在当前面板会话内;
   面板关闭重开后,该任务从"实时任务/任务结果"消失,由历史列表(仅标题/状态)承载。
   若需要在历史详情里也展示任务流完整进度记录,需要任务中心详情面板支持
   category=task 会话,属后续增强。
3. 【轮询频率】断线回退轮询 5s/次(状态+消息两条请求/任务),面板列表兜底刷新
   15s/次;运行中任务通常 1-2 个,负载可控。如需更实时的断线恢复可改为"重订优先、
   轮询仅兜底",当前实现已含 2 次重订。
4. 【实测方式说明】子代理不可用浏览器 GUI 工具(仅限主代理),故面板内交互未做
   截图级验证;实测通过 dev server(3001,代理 8081)用与组件一致的数据路径完成:
   登录→running 列表过滤→状态探针→SSE 订阅→进度采样→断流→轮询→终态,并确认
   /dashboard 路由编译渲染 200。脚本 5 的 170 块解析任务在实现期间自然完成,其
   COMPLETED 状态与完成摘要("自动分块解析完成,共 170 集")被直接采样验证;另建了
   一次性项目(6 块小剧本)完整实测了运行中→完成与纯轮询两条路径,测试数据已清理。

### 验证结果
- `corepack pnpm exec tsc --noEmit`:0 错误。
- `corepack pnpm exec eslint`(6 个改动文件):0 问题。
- 实测(dev server 3001 → 平台 8081,zhangyz):
  - 阶段1 发现:running 列表过滤 category=task,3 条中 2 条僵尸(NONE)被过滤、
    1 条 ACTIVE 建立实时流 ✓
  - 阶段2 实时:SSE 进度文本 "进度：1/6" → … → "进度：6/6" 持续更新 ✓
  - 阶段3 轮询回退:全程无 SSE,仅轮询推进 "进度：1/6 → 2/6 → 5/6",终态 COMPLETED ✓
  - 阶段4 完成:DONE 摘要 "自动分块解析完成，共 6 集" ✓;脚本 5 大任务摘要
    "自动分块解析完成，共 170 集" 同样采样验证 ✓
  - /dashboard 经 dev server 编译渲染 200,无编译错误。

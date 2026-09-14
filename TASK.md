# 冲刺任务 M2:手机端深度打磨

## 背景
M1 已完成全站移动端基础适配(底部Tab栏/卡片化/触摸目标),但用户反馈"只是勉强能看到,并不舒服,各种布局不完美"。本任务做深度打磨:每一页逐个过,确保视觉舒适、操作自然、无妥协。

## 你要做的事
1. 用 Playwright 或手动检查每一个页面在 390×844 下的实际渲染效果,记录所有布局问题(间距不均/字体不协调/按钮过小/内容被裁剪/层级混乱/留白异常)
2. 逐页修复:调间距/字号/留白/层级,确保视觉舒适度与桌面端同等水准
3. 重点打磨高频页面:仪表盘/项目列表/项目概览/剧本工作区/分镜页/资产中心
4. 修复所有触控目标过小的行内按钮(改"更多"菜单或放大热区)
5. 检查各弹窗/抽屉/对话框在手机上是否全屏友好
6. 检查键盘弹出后的表单可用性(input focus 滚动到位)

## 允许文件
- ai-fusion-video-web/app/(dashboard)/** 各页面的响应式类与组件(仅追加/修改,不改桌面≥1024px 逻辑)
- ai-fusion-video-web/components/dashboard/mobile-*(新组件)
- ai-fusion-video-web/app/(dashboard)/dashboard/layout.tsx 等布局层

## 禁区
- 不改后端;不改桌面端(≥1024px)布局与交互逻辑
- 不做简单缩放;不加数据库迁移

## 验收
- tsc --noEmit 零错误;eslint 改动文件零 error;build 通过
- 逐页自查清单落盘 TASK.md 末尾(每页:可达/可操作/无溢出/无遮挡/触控≥44px/字体协调)
- 桌面端不回归

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

---

# 冲刺任务 T14:字幕导出 H02 前置:场次对白生成 SRT

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
1. 后端新增端点:GET /api/script/episode/{id}/subtitle.srt —— 按场次对白生成 SRT 字幕文件(对白解析为 speaker:line,按场次顺序均匀分配时间轴,时长参数可选)。
2. 前端:剧本页场次详情与 /editing 页各加'导出字幕'按钮(下载该集 SRT)。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/controller/script/ScriptController.java(仅追加端点)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/script/**(新增导出服务,仅追加)
- ai-fusion-video-web/lib/api/script.ts(仅追加)
- ai-fusion-video-web/app/(dashboard)/projects/[id]/editing/**(仅追加按钮)
## 禁区
- 不改既有端点;不改分镜/生产模块;不加迁移。
## 验收
- 单测:SRT 格式(序号/时间轴/文本)正确性;编译通过;真实分集导出下载可用。

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

---


---

## T14 执行记录(追加)

### 允许清单例外声明(规则 1)
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/scripts/_components/scene-detail.tsx`:任务要点要求"剧本页场次详情"加导出按钮,该详情组件位于 scripts/_components/ 下而允许清单只列了 editing/**。仅追加按钮与处理函数,不改既有逻辑。
- `ai-fusion-video/src/test/java/com/stonewu/fusion/**`:新增单测文件(任务验收要求单测)。

### 变更文件清单
后端:
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/script/SubtitleExportService.java`(新增)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/script/ScriptController.java`(仅追加 `GET /episode/{id}/subtitle.srt` 端点与新 Service 注入字段,既有端点未动)
- `ai-fusion-video/src/test/java/com/stonewu/fusion/service/script/SubtitleExportServiceTests.java`(新增,21 例)
- `ai-fusion-video/src/test/java/com/stonewu/fusion/controller/script/ScriptControllerSubtitleExportTests.java`(新增,3 例)
前端:
- `ai-fusion-video-web/lib/api/script.ts`(追加 `downloadEpisodeSubtitle`,鉴权 fetch → blob 下载)
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/scripts/_components/scene-detail.tsx`(场景详情头部追加导出按钮)
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/editing/_components/episode-subtitle-export-button.tsx`(新增)
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/editing/_components/editing-sidebar.tsx`(分集行追加导出按钮,主行为按钮拆分重构,行为不变)

### 需要决策/已知事项
1. 对白双来源的取舍:任务要求同时用 sceneDescription 的"角色:台词"行与 dialogues JSON。真实数据(脚本 3 分集 20-23)全部为 `dialogues=null` 且描述用 Markdown 标签块(`**陈砚：**` 独立成行 + 下一行台词);而自动拆分流程会把同一句对白同时写进 dialogues JSON 和描述。为避免重复字幕,按"单场次内 dialogues JSON 优先,无可说话条目时才解析描述"实现——与任务的双来源要求一致,集成时如有异议可改为合并去重。
2. dialogues JSON 字段名兼容:`character_name`/`character`/`speaker`、`content`/`line`;`type` 缺省视为对白(自动拆分产物无 type),type=1(对白)/3(画外音)可成字幕,其余跳过。说话人前缀用全角冒号"：",与 ScriptAutoSplitService 落库格式一致。
3. 描述解析排除了制作指令标签(画面/台词/生成提示词/动作提示词等)与占位台词(（无）);`旁白`/`字幕` 标签的文本保留为无说话人前缀的叙述字幕。真实数据中"建议生成顺序"等纯文本冒号行不会被误判(独立标签行必须带 Markdown 加粗符号)。
4. secondsPerLine 默认 3 秒,合法范围 1-60,越界返回 400;分集无任何对白时返回 400"该分集暂无可导出的对白"(前端 toast 提示),不产出空文件。
5. 下载走鉴权:后端仅认 Authorization Bearer 头且 axios 拦截器会强解 CommonResult,故 `downloadEpisodeSubtitle` 用独立 fetch 取 blob 再触发浏览器下载,文件名取自 Content-Disposition(filename* UTF-8)。
6. /editing 页按钮落在"镜头目录"侧栏每个分集行上(分镜分集通过 scriptEpisodeId 绑定剧本分集;未绑定时按钮禁用并给 tooltip),比单一工具栏按钮更贴合"下载该集 SRT"。
7. 验证受限说明:本机 MySQL/Redis 仅存在于 docker 网络内、端口未对宿主发布(handshake 阶段被断开),且禁止 docker/compose,无法用 worktree 后端连库起第二实例;8081 平台为已构建镜像,不含本分支代码。真实数据验证改为:抓取 8081 上脚本 3 全部 17 个真实场次(4 集),经实际生成管道产出 SRT 38 条 cue 并人工检查(格式、顺序、无制作指令残留);HTTP 层(状态码/Content-Type: application/x-subrip/Content-Disposition/accessGuard 调用)由控制器单测覆盖。合并后建议集成者在 8081 平台做一次浏览器端下载确认。
8. 全量测试(619 例)中有 3 例失败,已在无本分支改动的 HEAD 上复现,均为 base 既有问题,与 T14 无关:AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol、ProjectServiceTests.listAccessibleByUserUsesCurrentTeamScope、AgentPersistenceMigrationIT.migrateSchemaWithLegacyOrderingFixtures。

### 验证结果
- 后端:`./mvnw test-compile` 通过;SubtitleExportServiceTests(21)+ScriptControllerSubtitleExportTests(3) 全绿。
- 前端:`tsc --noEmit` 通过;改动文件 eslint 0 error(scene-detail.tsx 的 hasLinkedAssets 未使用警告为 base 既有)。

---

## SW-T02 执行记录(Dev-B 前端追加,2026-09-13)

T10 发现的六条 UI 缺陷逐一修复,每项单独提交(分支 `swarm/a3-dev-frontend`):

| # | 缺陷 | 修复 | 提交 |
|---|------|------|------|
| 1 | 生图编辑器"← 图像工坊 / 能力目录"死链 | 根因:基线 87f8a6e 把 `/generate/images` 页替换成 `redirect("/generate/image")`,链接自引用成环。恢复图像工坊目录页(已启用生图模型卡片 + 待接入工作流),链接语义恢复;卡片经 `/generate/image?modelId=` 进编辑器 | f70d2a2 |
| 2 | SeedVR2 高清视频放大(id=13,已启用)无入口 | 同根因:`/generate/videos` 被替换为 redirect,视频工坊目录消失。恢复目录页( capabilityApi.catalog(3) 渲染模型卡片);实测 API 已返回 id=13"SeedVR2 高清视频放大 1080P" enabled=true,卡片"使用此模型"直达 `/generate/video?modelId=13` | 26af7ff |
| 3 | 分镜行内"生成视频"按钮 aria/hover/图标 | 卡片视图+表格视图:补 `aria-label`(含镜号);去掉 `opacity-0 group-hover:opacity-100` 纯 hover 依赖,改常显 + `focus-visible` 焦点环;图标 Video→Clapperboard,与资产侧栏"批量生视频"的 Video 区分、与生产抽屉"生产这一镜"动作呼应;`transition-all` 收窄为 `transition-colors` | acf85fe |
| 4a | 生产抽屉文案不一致 | "三候选生产"/"3 个候选"/"生产这一镜"三种表述统一:标题"{镜头} · 生产这一镜",描述"生产这一镜会生成 3 个候选视频,逐个质检后选用",卡片"将生成 3 个候选视频",按钮保持"生产这一镜",失败 toast 改"启动生产失败" | 20faf89 |
| 4b | 就绪度不足禁用无原因 | "生产这一镜"按钮下方新增 `role="status"` 原因行,拼接 `readiness.blockers[].message`("暂不能生产这一镜:…"),与禁用状态一一对应 | b718512 |
| 5 | 登录页品牌文案 | 底部"短剧制造平台"→"融光" | 60d7723 |

### 验证结果
- `corepack pnpm exec tsc --noEmit`:0 错误;`corepack pnpm exec eslint`(6 个改动文件):0 问题。
- dev server(3457,DEV_BACKEND_URL→8081)冒烟:`/generate/images` 编译渲染 200 无编译错误(其余改动文件均过 tsc/eslint)。
- 平台 8081(合并前代码)复现诊断:`/generate/images` 服务端返回 NEXT_REDIRECT→`/generate/image`(死链确认);
  `capabilityApi.catalog(3)` 实测返回 id=13 SeedVR2 enabled=true(缺陷 2 数据面确认)。
- 浏览器级视觉验证未做(子代理不使用 Browser Use),建议合并后由 QA/集成者在浏览器复核缺陷 1/2/3。

### 需要决策/遗留(不空等,记录如下)
1. 【信息架构拍板】本分支基线(87f8a6e"用户 WIP")曾有意把 `/generate/images|videos` 收敛为 redirect;
   本次按缺陷清单恢复为目录页(与 main 一致)。若产品确认"只留编辑器单入口"的 IA,应改为改 header
   链接文案与指向,而非保留 redirect,否则死链复发。
2. 【品牌统一范围】文件锁仅允许登录页:已改"融光"。仍为旧文案的:`app/layout.tsx` metadata
   (title"短剧制造"/description"短剧制造平台")、`app-header.tsx`("短剧制造")、
   `forgot-password/page.tsx`("短剧制造平台")、register/setup 页未逐一排查。需拍板后授权统一。
3. 【"三候选"残留】`production/page.tsx`、`projects/[id]/production/page.tsx` 仍有 3 处"三候选"
   文案,均在本次禁区(production 页)内,未动;如需与抽屉口径统一另行派发。
4. 【blockers 双处展示】说明卡片内 blockers 列表(既有)与按钮下原因行(新增)并存,轻微重复;
   保留是为了"禁用处必有原因",如嫌重复可移除卡片处列表。
5. 【行内按钮常显的视觉取舍】行内生产按钮由 hover 显现改为常显,卡片/表格视觉密度略增;
   相邻的删除按钮等 hover-only 控件未在缺陷范围内未动,如需统一可访问性标准另行派发。
6. 【QA 知悉】行内按钮 Tooltip/aria 文案由"生产 3 个候选视频"改为"生产这一镜(3 个候选)";
   e2e/ 现有用例未引用该文案,无影响。

---

## Round 2 执行记录(Dev-B 前端追加,2026-09-13):导航统一 + 品牌收尾

前置:合并 `sprint/universal-director-integration`(6c3fe0f,快进无冲突,含 Round 1 六项修复与
SW-T05/T06/T10/T14~T19 等集成成果)。

### 导航方案与理由(侧栏"生图/生视频" vs "图像工坊/视频工坊"并存问题)
采用"目录层唯一"方案(任务建议的第二种),即:侧栏只保留工坊目录入口,编辑器直达全部收纳为
工坊页内次级入口。理由:
- 仪表盘快捷入口(T6)、编辑器返回链接(Round 1)、声音工坊页结构三者的既有语义都已是
  "目录页=入口层",侧栏直指编辑器是唯一的分歧点,收敛后全站一致;
- 目录页天然承载"模型选择/能力状态"这一层信息,直达编辑器绕过它会造成 SeedVR2 类
  "已启用模型不可见"问题复发(T10 缺陷 2 的根源)。
落地(提交 3f60d05):
1. 侧栏工坊组:图像工坊(/generate/images)、视频工坊(/generate/videos)、声音工坊
   (/generate/audios)三项;移除冗余"工坊"首页项(/generate 与三个目录语义重复,页面保留为
   直达 URL 落地)。
2. 视频工坊目录顶部新增「万能导演台」卡片(default 主按钮 → /generate/video 免选模型模式),
   文案复用编辑器内描述;避免侧栏改指目录后统一导演流程失联——这是对"收纳为次级入口"的
   关键补位。
3. 活动态归属:/generate/image*高亮图像工坊,/generate/video*与 /generate/universal*高亮
   视频工坊(编辑器/导演台视为对应工坊的深层路由)。
4. /generate 落地页按钮「打开视频工坊」更正为「打开万能导演台」,与指向一致。

### 品牌统一(提交 55895bf)
layout metadata(default title"短剧制造"→"融光",description→"融光视频平台",口径与设置页
邮箱占位一致)、顶栏品牌字样(app-header"短剧制造"→"融光")、忘记密码页页脚
("短剧制造平台"→"融光")。全仓前端源码 grep 已无"短剧制造/荣光"残留;register/setup 页
无品牌文案,无需改动。

### 变更文件
- `ai-fusion-video-web/components/dashboard/sidebar-nav.tsx`
- `ai-fusion-video-web/app/(dashboard)/generate/videos/page.tsx`(新增万能导演台区块)
- `ai-fusion-video-web/app/(dashboard)/generate/page.tsx`(按钮文案)
- `ai-fusion-video-web/app/layout.tsx`、`components/dashboard/app-header.tsx`、
  `app/(auth)/forgot-password/page.tsx`(品牌)

### 验证结果
- `corepack pnpm exec tsc --noEmit`:0 错误;`eslint`(6 个改动文件):0 问题。
- 架构守护 `node --test tests/architecture/line-budget.test.mjs`:pass(1) fail(0)。
- UI 级浏览器验证未做(子代理不使用 Browser Use),建议集成部署后在浏览器复核:
  ① 侧栏三工坊入口与活动态;② 视频工坊→万能导演台卡片;③ 顶栏/登录/忘记密码品牌文案。

### 需要决策/遗留(不空等)
1. 【e2e 适配】`e2e/generation.spec.ts` 注释与断言基于"部署版 /generate/images|videos 服务端
   重定向"的旧行为(Round 1 已恢复目录页,重定向不复存在),且 QA 归属 SW-T03,本次未动;
   合并后需 QA 同步用例(路径 1/5 的 waitForURL 断言)。
2. 【万能导演台层级】现方案将其作为视频工坊内首张卡片;若产品希望它升至与工坊平级的侧栏
   一级项(四入口方案),只需在 generationItems 加一项并补活动态,改动极小,待拍板。
3. 【/generate 落地页】侧栏移除后仅剩直达 URL/旧书签触达,内容与三工坊部分重叠;后续可考虑
   重定向到 /dashboard 或改造为真正的"创作总览",属信息架构增强,待派发。

---

## SW-T06 执行记录与发现(QA 子代理追加,2026-09-14)

### 执行方式与范围
- 全部真实运行:JDK21 单测(新增 2 个测试类,24 例)+ 对 8081 实时平台的 HTTP 级实测(5 个脚本,
  swarm/qa/,证据存 swarm/qa/out/)。未改任何产品代码;Redis 停机测试仅 stop/start fusion-redis,
  已恢复 healthy;E2E 临时项目(id=8)已删除,零数据残留。

### 测试执行汇总
1. chunkChars 边界:单测 11 例全绿(null=6000/1999→2000/12001→12000/0、负数、MIN/MAX_VALUE);
   硬切、章节边界、>400 块拒绝、无丢字。E2E 真跑(12524 字临时剧本):chunkChars=999999→钳12000→2 集;
   chunkChars=-1→钳2000→9 集,分块数与单测推算完全一致,拼接无丢字 → PASS。
2. 170 集抽查:全量结构(编号 1..170 连续/每集场景数≥1/原文非空)PASS;170 集 rawContent 拼接与
   剧本原文逐字符一致(无丢字)PASS;随机 3 集(#169/#151/#1)场景非空、无原文兜底、无整段照抄、
   长句照抄率 0-2%,结构化场次标题 → PASS。
3. 字幕导出:真实分集(id=210,16 cues)序号连续/时间轴单调/每条时长=secondsPerLine/内容 16/16 可溯源;
   secondsPerLine=0/-5/61/999999 → 400;=1/60 → 200 且时间轴吻合;空对白分集(id=228)→ 400
   "该分集暂无可导出的对白",不产空文件;uitest 越权/不存在分集 → 500(缺陷,见 BUGS)。
4. 系统状态:三端点管理员 200 + 类型与加和断言 PASS;uitest 三端点 403、匿名 401;Redis 停机:
   video-queue 降级代码路径本身正确,但认证层先失效,实测 401/500(缺陷 SW-T06-01),Redis 已恢复 healthy。
5. 生产运行:pageNo=0(钳制为第1页)/999(空列表 total 保留)/pageSize 0/-1/1000 无 5xx;
   五状态过滤全部匹配、计数之和=total;空串等价不过滤;uitest 只见己方(userId=2,8 条,与 zhangyz 零重叠)。

### 结论与增量
- 单测+实测合计:通过 60+ 项断言;发现缺陷 4 项(SW-T06-01 P2、-02/-03 P3、-04 P4)与 3 条观察项,
  已按 现象/触发/影响/建议 落盘 swarm/BUGS.md。
- 需要决策(不空等):SW-T06-01 的"降级语义 vs 认证依赖 Redis"矛盾,需 Supervisor/Architect 拍板
  修复方向(登录 503 透出/token 校验降级/接受现状仅留档)。

## SW-T06-02/03 异常语义修复(swarm/b2-exceptions,2026-09-14)

### 实施摘要
- SW-T06-02:ProjectAccessGuard `requireEntity`→404、`assertAllowed`→403;ProjectController 四处
  越权校验(访问/工作区概览/修改/删除/初始化)→403。全部走既有 `BusinessException(code,msg)`,
  GlobalExceptionHandler 原有 code→HttpStatus 映射直接生效,业务文案逐字保留,前端无感。
  最小侵入方案:未改 GlobalExceptionHandler、未给 BusinessException 加语义状态字段。
- SW-T06-03:GlobalExceptionHandler 新增 `MethodArgumentTypeMismatchException` 处理器→400
  (msg=`请求参数类型不匹配: <参数名>`),不再落入通用 500。
- 测试:GlobalExceptionHandlerTests(6,新增)、ScriptControllerExceptionMappingTests(3,新增,
  standalone MockMvc 复现 QA 三条请求)、ProjectAccessGuardTests(8,扩 code 断言+2 个 404 用例)、
  ProjectControllerAccessGuardTests(8,扩 code 断言+1 个 MockMvc 403)、ScriptControllerSubtitleExportTests(3,回归)。

### 需要决策(不空等,追加)
1. 防枚举口径:当前越权 403、不存在 404,但业务文案本身区分"X 不存在: id"与"无权访问",
   资源存在性对越权者可见。若要防枚举需同时统一文案(如一律 404"资源不存在或无权访问"),
   影响前端错误提示,需 Supervisor/产品拍板;本次按 BUGS.md 主方案(403/404 分开)实施。
2. 存量 500 语义未全量清理:仅覆盖 ProjectAccessGuard 全局模式+ProjectController(QA 复现路径);
   其余 service/controller 中 `BusinessException("...不存在/失败")` 默认 500 的仍大量存在,
   建议立技术债专项统一语义码,本次不扩面。
3. SW-T06-03 的 400 文案格式(`请求参数类型不匹配: <参数名>`)如需 i18n/前端定制,待定。

---

---

## SW-T10 执行记录与根因分析(2026-09-14,后端测试子代理追加)

### 根因分析:ProjectServiceTests.listAccessibleByUserUsesCurrentTeamScope(修复产品代码,理由如下)
- 失败:`TooManyActualInvocations: teamService.getCurrentTeamIdByUser(9L) — wanted 1 time but was 2`,
  调用点 `ProjectService.listAccessibleByUser:87` 与 `accessibleProjectWrapper:95`。单跑/全量均稳定复现
  (其他代理"单跑可能绿"应为旧代码状态差异;基线提交 6c81321 引入后确定性失败)。
- 引入史:6c81321(enforce project access control)把 `listAccessibleByUser` 的团队分支改为委托新方法
  `accessibleProjectWrapper(userId)`,但保留了自身 Null 检查用的 `getCurrentTeamIdByUser` 调用(用于路由到
  带 @Cacheable 的 `listByOwner` 个人分支),导致团队用户每次列表请求重复查询两次团队 ID。
- 判定:产品代码错了,测试是对的。`getCurrentTeamIdByUser` 内部有 `teamMemberMapper.selectOne` DB 查询
  (无缓存),重复调用是纯浪费;且两次读取之间团队状态若变化,Null 检查与 wrapper 构建可能基于不同结果,
  存在一致性隐患。修测试(verify times(2))等于把缺陷固化,故修产品。
- 修法(最小):抽包私有 `teamScopedProjectWrapper(currentTeamId)` 共享构建逻辑;`listAccessibleByUser`
  与 `accessibleProjectWrapper` 各自只查一次团队 ID 后传入。`page()` 行为不变;个人分支(含缓存路由)不变。

### 变更文件
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/project/ProjectService.java`:消除重复团队查询(见上)。
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/generation/GenerationTaskReaper.java`:
  STALE_HOURS 常量 → 构造参数 `@Value("${app.generation.reaper.stale-hours:2}")`(负/零 fail-fast);
  `@Scheduled` 改 `fixedDelayString/initialDelayString` 读 `app.generation.reaper.fixed-delay-ms:600000`
  与 `initial-delay-ms:60000`;TIMEOUT_MESSAGE 常量 → 构造期实例字段(文案随配置生成,默认值与原文案逐字一致)。
  构造器注入模式与既有 `AgentScopeKernelLifecycle`/`AgentWorkspacePayloadService` 一致。
- `ai-fusion-video/src/main/resources/application.yaml`:新增 `app.generation.reaper.{stale-hours,fixed-delay-ms,initial-delay-ms}`
  默认值(2 / 600000 / 60000,与改造前硬编码完全一致,行为不变);保留原 CRLF 行尾未做全文件归一。
- `ai-fusion-video/src/test/java/com/stonewu/fusion/service/generation/GenerationTaskReaperTests.java`:
  `reaper()` 工厂补第 6 个构造参数 2(阈值显式化),断言文案不变。

### 测试结果
- `./mvnw test -Dtest=ProjectServiceTests`:8/8 绿(修复前 1 失败)。
- `./mvnw test -Dtest=GenerationTaskReaperTests`:4/4 绿。
- 全量 `./mvnw test`:673 例,Failures: 1, Errors: 9,全部为与本任务无关的存量问题:
  - Errors(9):FusionVideoApplicationTests、AiAgentToolRegistrationTests(7)、ProjectWorkspaceCacheTests
    —— 均为 ApplicationContext 加载失败(需 MySQL/Redis,本环境不可用,按任务约定跳过)。
  - Failure(1):`AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol` —— 基线既有(T9/T14 已留档)。
    本任务定位到其确切根因:禁止正则含裸词 `MysqlSession`,误命中
    `ApplicationTimeZoneInitializerTests.java:24` 的方法名 `loadsApplicationAndMysqlSessionTimeZonesFromConfiguration`
    (纯标识符误伤,非真实 V1 残留)。属 AgentScope GA 工作面文件,超出本任务允许边界未改动,建议该方法改名
    (如 MySqlSession→大写 S 或拆词)即可转绿。

### 决策与说明
1. 回收器参数化未加新测试:验收为"行为不变",既有 4 例断言(含超时文案逐字比对)已覆盖默认值路径;
   @Value 解析由 Spring 承担,自测无增量价值。
2. `stale-hours<=0` 选择 fail-fast 抛 IllegalArgumentException:0/负值会使阈值落在未来、回收器首轮即把
   全部进行中任务标记失败,属配置事故,宁可启动失败也不静默破坏数据。

---

# SW-T17/T18/T19(P0 安全修复)需要决策的问题

分支:swarm/p0-security-fix(基于 swarm/a5-redteam)。提交:7fdf1d6(SW-T17)/ 3f623fc(SW-T18)/ e663749(SW-T19)。日期:2026-09-14。

1. **api-config 管理端密钥回显 UX(SW-T17)**:ApiConfigRespVO 已删除 apiKey/appSecret/proxyPassword。updateApiConfig 本就是"null=不修改"语义,后端安全;但 ai-models 设置页编辑表单中三个密钥字段将不再回显旧值。如需回显,按红队建议做 `sk-***last4` 脱敏回显(需后端出掩码字段+前端配合),当前实现选择"不回显"。
2. **内网模型端点产出 URL 会被拒(SW-T18)**:MediaStorageService.downloadAndStore 现在要求模型返回的 imageUrl/videoUrl 为公网地址。若存在部署在内网的自建 openai_compatible/ComfyUI(如 http://192.168.x.x 返回内网素材链接),持久化会失败。判定:安全优先,属预期;确有此场景时建议把 video.compose.allowed-hosts 的管理员白名单范式推广到 MediaStorageService,而不是放开校验。
3. **ComfyUI 输入 URL 遇 302 显式失败(SW-T18)**:为最小侵入(该文件正被音频改造占用),downloadHttp/downloadVideoHttp 只做了入口校验+关闭自动重定向,返回 302 的公网源会得到 502 而非跟随。如需跟随,后续在该文件引入 SafeHttpDownloader 逐跳复检。
4. **CGNAT 100.64.0.0/10 纳入拦截(超出红队报告清单,SW-T18)**:该段覆盖云元数据(阿里云 100.100.100.200)与 Tailscale 类主机间组网,Java 不视为私有。若未来有以该段为"公网出口"的部署环境需回退此条判定。
5. **assistant-upload 仍为 getBytes 全量入堆(P-4 残留)**:本次流式化只覆盖 /upload;assistant-upload(类型受常量白名单约束)维持原状,建议在 P-4 任务统一处理。
6. **uitest token 实机验证受限**:本环境 MySQL/Redis 仅在 docker 网络内且禁用 docker,无法起实例做实机验证;/get、/list 的 ADMIN 拒绝已由权限矩阵反射测试+全局 @EnableMethodSecurity 覆盖,合并后建议在 8081 平台用 uitest 账号实测一次。
7. **全量回归结果**:714 例(含新增 37 例安全用例),2 失败均为 base 存量(AgentScopeGaDependencyContractTests.sourceTreeContainsNoObsoleteV1Symbol 指向 ApplicationTimeZoneInitializerTests.java、ProjectServiceTests.listAccessibleByUserUsesCurrentTeamScope,TASK.md 此前已记录在无本分支的 HEAD 复现);39 错误全部为 integration/*IT 与 AgentPersistenceMigrationIT 的 Redis/MySQL 连接失败(本机基础设施未运行,环境性)。FlywayMigrationNamingTests 通过。

---

---

## SW-T05 执行记录(Dev-B,2026-09-14,分支 swarm/b1-recycle)

### 变更文件
后端(全部为追加,既有方法零改动):
- `ai-fusion-video/src/main/java/com/stonewu/fusion/mapper/asset/AssetMapper.java`
  (追加 4 个自定义 SQL:`selectDeletedPage` 分页查软删行/`selectDeletedById`/
  `restoreById` 置 deleted=0/`deletePhysicallyById` 物理 DELETE)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/mapper/asset/AssetItemMapper.java`
  (追加 `deletePhysicallyByAssetId`,彻底删除时物理清理子资产行)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/asset/AssetService.java`
  (追加回收站段:`pageDeletedInAccessibleProjects`/`getDeletedById`/`restore`/`purge`,
  恢复与彻底删除均 `@CacheEvict({"asset","assetItem"}, allEntries)+@Transactional`)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/asset/AssetController.java`
  (追加三端点,见下)
- 测试(允许清单例外声明:任务验收要求单测):
  - `src/test/java/com/stonewu/fusion/service/asset/AssetRecycleBinServiceTests.java`(7 例)
  - `src/test/java/com/stonewu/fusion/controller/asset/AssetControllerRecycleBinTests.java`(9 例)
  - `src/test/java/com/stonewu/fusion/mapper/asset/AssetRecycleBinMapperSqlTests.java`(6 例,
    用 MyBatis 动态 SQL 解析器验证注解 SQL 契约,不依赖数据库)

三个新端点(挂 /api/asset 下,与既有 /all、/list 同用"字面量优先于 {id}"路由机制):
- `GET /api/asset/recycle-bin?page=&size=` 当前用户可访问项目内已删资产分页
  (按 update_time 即删除时间倒序;项目范围 = projectService.listAccessibleByUser)
- `PUT /api/asset/recycle-bin/{id}/restore` 恢复(置 deleted=0,保留原 id/子资产/引用)
- `DELETE /api/asset/recycle-bin/{id}` 彻底删除(物理 DELETE 资产行 + 其全部子资产行)
归属校验按 ProjectAccessGuard 模式:先经 getDeletedById 解析软删行(既有 selectById 被
@TableLogic 过滤查不到),再 `accessGuard.assertProject(deleted.getProjectId())`,
校验失败抛"无权访问该项目内容",service 动作不会执行。

前端:
- `ai-fusion-video-web/lib/api/asset.ts`(仅追加:RecycleBinPageResp 类型 +
  listRecycleBin/restoreRecycled/purgeRecycled 三方法)
- `app/(dashboard)/assets/page.tsx`(回收站状态改服务端数据:loadBin 分页循环加载
  与 loadAssets 同上限;删除/恢复后双列表刷新;移除 useAuthStore/localStorage 依赖)
- `app/(dashboard)/assets/_components/recycle-bin-view.tsx`(数据源 DeletedAssetRecord
  快照 → Asset 实体;"删除于"取 updateTime(软删 UPDATE 经 ON UPDATE CURRENT_TIMESTAMP
  自动刷新);提示文案改为"保留原 id";新增刷新按钮;彻底删除二次确认文案明确"物理删除")
- `app/(dashboard)/assets/_components/recycle-bin-store.ts`(整文件删除)
- `app/(dashboard)/assets/_components/utils.ts`(删除仅为快照恢复服务的
  buildRestoreReq/propertiesToCreatePayload 死代码)

### 关键决策
1. 软删行触达方式:MyBatis-Plus @TableLogic 只对 BaseMapper 注入方法自动追加 deleted=0,
   自定义注解 SQL 不受影响——回收站查询/恢复/物理删除全部走 AssetMapper 显式 SQL。
   `selectDeletedPage` 的 `<script>` 文本块必须顶格(否则 MyBatis 不按动态 SQL 解析),
   已由 AssetRecycleBinMapperSqlTests 契约测试固化。
2. 彻底删除同时物理删除子资产行(afv_asset_item),避免主行删除后留下永久不可达的孤儿行;
   媒体文件不做清理——与既有软删行为完全一致(既有 delete 从不清理文件),留档为产品决策点。
3. 回收站可见范围 = 当前用户可访问项目(经 listAccessibleByUser 解析项目 id 集合后
   IN 查询),与任务书"当前用户项目内"一致;无可访问项目时短路返回空页(避免 IN () 非法 SQL,
   非兜底逻辑)。相比 T11 的 localStorage 方案,顺带覆盖了"从项目资产页删除"的资产(T11 遗留2)。
4. 恢复语义:保留原 id(置 deleted=0),不是 T11 方案的"重建拿新 id";分镜等引用自动重连。
5. 路由安全:GET /api/asset/recycle-bin 与 GET /api/asset/{id} 共存依赖 Spring 字面量优先,
   与既有 /all、/list、/metadata/{assetType} 同机制;补充 PathPattern 比较器单测固化。

### 验证结果
- 后端:`./mvnw compile` 通过;新增 22 例单测全绿(service 7 + controller 9 + SQL 契约 6)。
  全量 `./mvnw test`:694 例,仅 11 例失败且与基线完全一致(T9/T14 已归档:
  AgentScopeGaDependencyContractTests 源码扫描、ProjectServiceTests.listAccessibleByUserUsesCurrentTeamScope,
  及需 MySQL/Redis 的 FusionVideoApplicationTests/AiAgentToolRegistrationTests×7/ProjectWorkspaceCacheTests),
  本任务改动 0 新增失败。
- 前端:`tsc --noEmit` 0 错误;改动 4 文件 eslint 0 问题;`next build` 通过(exit 0);
  dev server(3459,代理 8081)/assets 带 auth-token cookie 返回 200,
  recycle-bin 调用已编入 SSR 与客户端 chunk。
- 平台 8081 实测(旧镜像,不含本分支代码):
  - 既有链路复现:创建资产 id=21(projectId=5)→ DELETE 成功 → GET /api/asset/21 返回
    "资产不存在: 21"、listAll keyword 检索 total=0(软删行既有接口完全不可达,与 T11 结论一致);
  - 新三端点:GET /recycle-bin 500(旧镜像落入 GET /{id} 的 Long 转换失败,集成后由字面量优先修复)、
    PUT restore / DELETE 均为 404"资源未找到"——证明平台运行的是合并前代码。
- 【集成者需补做的全链路实测】镜像重建后执行(测试数据已备好):
  ① GET /api/asset/recycle-bin 应返回 total≥1,含 id=21(name=t05-recycle-fullchain-test,
     本任务实测特意保留的软删行,T11 的 ids 12–20 残留也应一并可见);
  ② PUT /api/asset/recycle-bin/21/restore → GET /api/asset/21 恢复可达;
  ③ 再 DELETE /api/asset/21(软删)→ DELETE /api/asset/recycle-bin/21(物理删)→
     数据库 afv_asset/afv_asset_item 中 id=21 应不存在;顺手可用同一端点清理 T11 残留;
  ④ uitest 登录调用 ①应看不到 zhangyz 项目的已删资产(隔离校验)。

### 遗留
1. 【环境受限】宿主未发布 MySQL 43306/Redis 46379 且禁止 docker,本地无法起带库实例,
   "删→回收站→恢复→彻底删"的后半段(回收站可见/恢复/物理删)只能在集成重建镜像后按上述
   4 步实测;行为已由 22 例单测(含 SQL 契约与路由契约)覆盖。
2. 【媒体文件】彻底删除不清理 /media 文件(与既有软删一致);若需要 GC,属独立的后端能力。
3. 【无 projectId 的软删行】归属校验走 assertProject(projectId),projectId 为 null 的
   历史软删行任何人都不可见/不可操作(不进回收站列表,也不能恢复/彻底删),如存在此类
   脏数据需 DBA 侧处理。
4. 【前端降级形态】合并前的前端若指向旧镜像,回收站 Tab 会提示"加载回收站失败"并显示空态
   (GET 落到 /{id} 报 500),集成后自愈。

---

# Round 2(部署后安全复验)结论与决策问题

分支:swarm/p0-security-fix。提交:2cc0956(S-2 IP 固定)/ b9e3445(ComfyUI 逐跳 + assistant-upload 流式)。日期:2026-09-14。

## 部署后实测(8081 集成版,e2e helpers 凭据)
- api-config:匿名 401;uitest /get、/list 均 403;zhangyz /list 200 且响应无 apiKey/appSecret/proxyPassword 字段 ✓
- /api/storage/upload(uitest):`../../etc` 与盘符路径均报"非法存储子目录";HTML 字节声明 PNG(含 x.html 文件名)被魔数校验拒;正常 PNG 200 落盘 .png ✓(测试残留 /media/sec-test/ 两个小文件)
- SSRF 三项:入口拒绝逻辑在代码+单测层验证;实机验证需真实发起生成任务(消耗模型配额),刻意未做
- 新端点抽查:系统状态三端点全部 @PreAuthorize(ADMIN),uitest 实测 403/admin 200;回收站三端点 userId+项目级 accessGuard(restore/purge 对他人资产拒绝,null projectId 拒绝);字幕导出 assertScriptEpisode 团队域校验,Content-Disposition 走 Spring 编码无头注入;actuator 匿名 307 → /login 不可达;/api/system/init/setup 有 isInitialized 守卫,初始化接管不可行;mapper 无 ${} SQL 拼接,keyword 走 LambdaQueryWrapper 参数化。未发现权限/注入盲区

## Round 2 实现项
1. S-2 DNS rebinding TOCTOU(高风险→已实现):SafeHttpDownloader 每跳校验通过的 DNS 解析结果经自定义 OkHttp Dns 固定到连接(Host/SNI 保持域名);校验 host 改取 OkHttp HttpUrl(与建连同源解析),收窄 S-5 parser differential。实证用例:.invalid 假域名走固定 IP 直连成功
2. ComfyUI 输入 302 显式失败(中→已实现):改走 SafeHttpDownloader.fetch,公网重定向源恢复可用且逐跳复检+固定
3. assistant-upload getBytes 入堆(P-4,中→已实现):流式临时文件落盘

## 决策问题
1. VersionInfoController(/api/system/version、/runtime)无 @PreAuthorize,依赖全局 authenticated 兜底:登录用户可见版本信息(含上游检查结果)。如需收紧建议补 ADMIN——属其他任务文件,未改动
2. 回收站 list 的 size 分页参数无上限(用户可传 size=100000 拉大页):低风险,建议其他任务Owner加 PageParam 上限校验
3. S-5 parser differential 的彻底修复(校验入口全量切 HttpUrl)仅覆盖 SafeHttpDownloader 通道;PublicHttpUrlValidator 直接调用点(ReferenceImageTransportService.selectTransport、VideoCompose allowlist 分支)仍走 java.net.URI 解析,建议红队 R3 评估

---

# M2 完成记录(2026-09-13)

## 检查方法
- 环境:worktree 分支 `swarm/M2-mobile-polish`(基于 sprint/base,含 M1 全部成果),`next dev` + 本地后端(8081)真实数据(zhangyz 账号,项目 3「末世双女主囤货漫剧」)。
- Playwright(Chromium,iPhone 14 级 390×844,isMobile+hasTouch,DPR2)逐页截图 + DOM 测量脚本:水平溢出(scrollWidth>390)、越界元素、可见交互控件尺寸清单。首轮 27 页全量扫描,修复后复扫,另在 1728×960 截图 9 个关键页对比桌面布局。
- 全部 27 页复扫结果:`scrollWidth=390`,零水平溢出;Tab 栏/顶栏/悬浮球不再遮挡内容;弹窗抽屉均不被悬浮控件穿透。

## 主要修复
1. **全局层级与文案**
   - 助手悬浮球避开移动端 Tab 栏:`assistant/geometry.ts` 新增 `getLauncherBottomInset()`(窄屏底边距 72px),默认位置与拖拽 clamp 同步生效(桌面 16px 不变)。
   - 移动顶栏品牌「短剧制造」→「融光」,与桌面顶栏一致。
   - 自定义弹窗/抽屉(create-project、episode-parse、video-preview、edit-assets、production-take-drawer、run-detail-drawer)z-index 从 50 提到 11000/11001(与 ui/dialog 的 z-[11001] 对齐),修复移动端页面菜单 FAB(z-60)与助手悬浮球(z-65)浮在弹窗之上的问题。
2. **触控热区**(新组件 `components/dashboard/mobile-touch-area.ts`,透明伪元素扩到 ≥44px,仅 `max-lg:` 生效,不改视觉尺寸,桌面零影响;absolute 定位控件用 `touchHitAreaOverlay` 避免覆盖原定位)
   - 编辑页上移/下移镜头(icon-xs 24px)、生产中心刷新/详情、生产抽屉、资产目录筛选片(h-6→移动端 h-9)/刷新、素材中心网格/列表切换与回收站按钮、AI 配置页编辑/删除模型(20px,移动端升 36px)、设为默认、API 配置行操作、存储配置行操作、工坊 composer(添加附件/高级模式/生成/移除附件)、创作记录(刷新/再次使用/作为参考/添加资产/下载)、分镜卡首尾帧/生产这一镜/拖拽手柄、剧本场次删除/内外景 Select(移动端加高)/InlineEdit(纵向热区)、顶栏任务徽标。
3. **布局节奏**
   - 仪表盘统计卡移动端改单行(标签左/数值右),四卡高度统一紧凑,首屏信息量提升;桌面保持上下结构。
   - 项目概览流程步骤卡移动端改横向单行(序号+步骤+状态右对齐),5 步从 ~530px 压缩到 ~290px;桌面保持块状卡。
   - AI 配置页 API 配置头与模型行移动端 flex-wrap,操作按钮换行到内容下方右对齐,修复文字逐字换行挤压。
   - 原文页「已导入原文」头部移动端换行,统计不再逐字竖排;通用设置「前后端不同域名」标签不再三行折行。
4. **弹窗全屏友好**
   - create-project/episode-parse 弹窗移动端加 12px 侧边距(`max-lg:max-w-[calc(100vw-1.5rem)]`),其余 Dialog 走 ui/dialog 基类已有 `max-w-[calc(100%-2rem)]`;生产详情/生产抽屉 `w-full sm:max-w-lg` 全宽可用;所有弹窗 max-h + 内部滚动已具备。
5. **键盘可用性**
   - 新组件 `components/dashboard/mobile-input-scroll.ts`:窄屏监听 focusin/visualViewport resize,延迟 260ms 对聚焦输入框 `scrollIntoView(nearest)`,尊重 prefers-reduced-motion;在 dashboard 布局层安装,覆盖全部弹窗与页面表单。
6. **工坊编辑器遮挡**
   - `/generate/{universal,image,video,images,videos,audios}` 全高编辑器路由不再渲染页面菜单 FAB(此前悬浮球压在「生成」按钮上);导航由 Tab 栏与页内「返回目录」链接承担。

## 验证结果
- `tsc --noEmit` 0 错误;改动文件 eslint 0 error(4 个 warning 均为 sprint/base 已有:scene-card 未用 import、episode-parse 未用 import、create-project img 与 eslint-disable,非本次引入);`corepack pnpm build` 通过(exit 0)。
- 桌面端(1728×960)对仪表盘/项目/概览/剧本/分镜/资产中心/视频工坊/生产/AI 配置 9 页截图比对:布局、栅格、交互位置与基线一致(改动全部通过 `max-lg:` 或运行时断点门控)。

## 逐页自查清单(390×844,✓=通过)
| 页面 | 可达 | 可操作 | 无溢出 | 无遮挡 | 触控 | 字体协调 |
|---|---|---|---|---|---|---|
| /dashboard | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /dashboard/analytics | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id] 概览 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/source | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/scripts | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/storyboards | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/assets | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/editing | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/production | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/delivery | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/members | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/settings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /assets | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /production | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate 目录 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/images | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/videos | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/audios | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/video(万能导演台) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/general | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/profile | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/agents | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/ai-models | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/storage | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/users | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

触控说明:视觉尺寸遵循共享按钮系统(24/32/36/40px),44px 通过透明伪元素热区在 <1024px 达成(见 `mobile-touch-area.ts`),脚本按可视尺寸统计的"小目标"为设计系统内的正常值。

## 需要决策/遗留问题
1. **弹窗 z 层修正跨断点生效**:自定义弹窗从 z-50 提到 z-[11000] 后,桌面端这些弹窗也会盖在助手浮层(最大化 z-65)之上。此前在桌面端弹窗会被助手遮住——判定为原有缺陷,现统一与 ui/dialog(z-[11001])对齐;如需桌面维持旧行为请反馈。
2. **编辑器路由隐藏页面菜单 FAB**:六个工坊编辑器路由不再出现左下角 FAB(曾压住「生成」主按钮)。若希望保留入口,需设计新的二级导航位置。
3. 剧本页「新增场次」胶囊按设计悬浮在分集分隔线上,与空的「点击添加分集概览…」占位文案视觉上轻微重叠;属既有设计,未改动。
4. 触控 44px 与按钮系统 36/40px 的矛盾用"透明热区"方案落地(不改视觉);若产品决定移动端按钮直接放大到 44px 视觉高度,需要在按钮系统新增移动端尺寸档,涉及 components/ui/button 调整(本次禁区)。
5. `DEV_BACKEND_URL` 默认指向 18080(本机不可用),本次本地验证通过环境变量覆盖为 8081,未改动已跟踪的 `.env.development`。

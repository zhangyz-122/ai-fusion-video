# RED TEAM REPORT — Round 1(SW-T08 P3:安全与性能扫描报告)

作者:Agent-5 Red Team。日期:2026-09-14。基点:swarm/a5-redteam(基于 sprint/universal-director-integration)。
方法:纯代码层推演 + 本机只读分析,未对任何生产/外网系统发起真实攻击。产品代码零修改。

---

## 一、SSRF 绕过分析(PublicHttpUrlValidator 及旁路取数点)

涉及文件:
- `ai-fusion-video/src/main/java/com/stonewu/fusion/security/http/PublicHttpUrlValidator.java`
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/generation/ReferenceImageTransportService.java`(校验器唯一接入点)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/comfyui/ComfyUiInputResourceService.java`(旁路 1)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/storage/strategy/LocalStorageStrategy.java`(旁路 2)
- `ai-fusion-video/src/main/java/com/stonewu/fusion/service/storyboard/VideoComposeService.java`(独立第二套校验)

### S-1【高】302 重定向绕过:校验只做一次,OkHttp 默认自动跟随重定向
- 攻击思路:提交公网 URL `https://attacker.com/img.png`,该地址 302 跳转到 `http://127.0.0.1:8080/`、`http://169.254.169.254/latest/meta-data/` 或内网 Consul/ES。
- 现状:`ReferenceImageTransportService.loadBinaryResource`(L185-199)先过 `PublicHttpUrlValidator`,再由 OkHttp(默认 `followRedirects=true`,构造器未关闭)发起请求;**每一跳都不再校验**。响应字节直接转 Data URI 回传用户 → 内网响应体可被完整读取(SSRF 全带,包括云 metadata 凭据)。
- 防住没有:❌ 完全没防。
- 对照组:`VideoComposeService.downloadToFile`(L409-452)手动逐跳 `validateRemoteUri(current)` 且 `setInstanceFollowRedirects(false)` —— 同仓库内已有正确范式,参考图链路未采用。
- 修复:OkHttp client `.followRedirects(false).followSslRedirects(false)` + 循环内每跳复用 validator(抄 VideoCompose 范式,或抽公共"安全下载组件")。预估 0.5-1 天。

### S-2【高】DNS rebinding TOCTOU:校验时解析 ≠ 连接时解析
- 攻击思路:攻击者域 `evil.com` TTL=0,第 1 次 DNS 查询返回公网 IP(过校验),OkHttp 建连时第 2 次查询返回 `127.0.0.1`/`10.0.0.5`。
- 现状:类 Javadoc 自己承认"连接时二次解析导致的 TOCTOU 需要连接级 IP 固定才能彻底解决",但没有做。所有接入点都是"先校验字符串、后由 HTTP 客户端独立解析连接"。
- 防住没有:❌ 未防(已知未修)。
- 修复:自定义 OkHttp `Dns`,复用校验时的解析结果(解析一次、IP 直连、Host/SNI 头保留);或拦截器中对 `handshake.peerHost`/`socket.inetAddress` 做二次 `isPublicAddress` 断言。预估 1-2 天。

### S-3【高】两条完全没接校验器的服务端取数旁路(比绕过更糟,是"没上锁")
- 旁路 A:`ComfyUiInputResourceService.downloadHttp`(L126-160)与 `downloadVideoHttp`(L196+)。仅检查 `URI.create` 成功 + host 非空,然后 OkHttp 直接 GET。`http://127.0.0.1:9200/_cat/indices`、`http://100.100.100.200/latest/meta-data/` 一把梭,且自动跟随重定向(继承 S-1)。source 来自生图/生视频请求的参考图/参考视频字段,**普通用户可控**。
- 旁路 B:`LocalStorageStrategy.store`(L47-74),由 `MediaStorageService.downloadAndStore` 调用(ImageGenerationConsumer L360、VideoGenerationConsumer L371/383/394/424)。用**模型 API 返回的 imageUrl/videoUrl** 直接下载:OkHttp `followRedirects(true)` 显式开启、零校验。恶意/被劫持的模型端点(或自建 openai_compatible 端点)可让后端 GET 任意内网地址;下载结果落盘 `/media/**` 可再被用户访问 → 数据回传通道。
- 防住没有:❌ 两处均未调用 `PublicHttpUrlValidator`。
- 修复:两处强制走统一的安全下载组件(含 S-1/S-2 修复);全仓库 grep `new Request.Builder().url(` 做一次取数点盘点,建立"服务端出站 URL 必须过 validator"的检查清单。预估 1 天(盘点+接入)。

### S-4【中】IPv6 特殊段:6to4/NAT64 未覆盖;VideoCompose 第二套校验更弱
- 攻击思路 a:PublicHttpUrlValidator 覆盖了 ULA(fc00::/7)与 IPv4-compatible(`::127.0.0.1`),IPv4-mapped 已被 Java 归一化为 Inet4Address 处理(正确)。但 **6to4 `2002:7f00:1::`(嵌入 127.0.0.1)与 NAT64 `64:ff9b::7f00:1`** 均被判为公网 IPv6 放行;在具备 6to4/NAT64 路由的网络可落地到内网 IPv4。判定:普通部署环境命中率低 → 中。
- 攻击思路 b:`VideoComposeService.validateRemoteUri`(L635-665)只查 anyLocal/loopback/linkLocal/siteLocal/multicast。Java 的 `Inet6Address.isSiteLocalAddress()` 只认 **fec0::/10 旧站点本地段**,现代 ULA `fd00::/8` 不在内 → `http://[fd00::5]/`、`http://[fc00::1]/` 直接放行;也不检查 IPv4-compatible 形式。校验逻辑"双轨漂移",弱版防住了重定向、防不住 IPv6。
- 防住没有:主校验器部分防(6to4/NAT64 缺口);VideoCompose 版 ❌。
- 修复:VideoCompose 删除自研校验改调 `PublicHttpUrlValidator`;主校验器补 2002::/16、64:ff9b::/96 黑名单(裁剪嵌入 IPv4 后复用 IPv4 判定)。预估 0.5 天。

### S-5【中】解析器差异:java.net.URI vs OkHttp HttpUrl(parser differential)
- 攻击思路:校验用 `new URI(value).getHost()`,连接用 OkHttp `HttpUrl`。两者对全角句点(U+3002)、IDN/punycode、百分号编码、多余斜杠/反斜杠、`[::ffff:7f00:1]` 等输入的规范化不一致,构成经典 parser differential 攻击面。
- 现状:多数构造 payload 会让 `getHost()` 返回 null → fail-closed 拒绝(当前实测推演未找到稳定绕过对),但这是"巧合安全",规范无保证。
- 修复:校验入口先 `HttpUrl.parse()`(与 OkHttp 同源解析)取规范化 host 再做 DNS/网段判定;保证"校验的字符串=连接的字符串"。预估 0.5-1 天。

### S-6【低】其余已核对的攻击面(防住/低风险,留档)
- 十进制 `http://2130706433/`、十六进制 `http://0x7f.0.0.1`、八进制 `http://0177.0.0.1`:经 `InetAddress.getAllByName` 归一化为 Inet4 后被 `isLoopbackAddress` 拦截 → ✅ 防住(受 S-2 TOCTOU 前提约束)。
- `localhost`、`.localhost`、`.local`、`.internal`、`host.docker.internal` 字面量黑名单 → ✅;但黑名单可枚举性差,建议内网判定完全下沉到 IP 层(现状主要靠 IP 层,可接受)。
- user-info 混淆(`http://user@127.0.0.1/`)、`#` 片段混淆:URI 与 HttpUrl 对 authority 切分一致 → ✅。
- trailing-dot host(`http://localhost./`)已被 L51-53 去尾点处理 → ✅。

---

## 二、性能扫描

### P-1【高】170 集剧本树:前端 HTTP N+1,页面加载并发打 170+ 请求
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/scripts/page.tsx` L147-152:`setExpandedEpisodes(new Set(eps.map(e => e.id)))` **默认展开全部分集** + `Promise.all(eps.map(loadEpisodeScenes))` —— 170 集 = 170 个 `listScenes` 请求同时打后端,无并发上限、无分页、无虚拟化渲染。
- `storyboards/_components/storyboard-sidebar.tsx` L214-233 同构问题:`loadEpisodes` 对全部 eps `Promise.all(listScenesByEpisode)`,且 L220 默认全展开。
- `storyboards/page.tsx` L371-376 / L476-484:`Promise.all(scenes.map(listItemsByScene))` 每场次一个请求。
- 影响:170 集实测路径(自动分块)打开即雪崩;浏览器并发限制排队、后端瞬间 170 QPS/用户;DOM 数千行无虚拟化。
- 修复:后端提供聚合端点(GET storyboard tree:episodes+scenes 一次返回;items 按需);前端默认只展开当前集;`loadEpisodeScenes` 懒加载;列表虚拟化(170 行场景建议 `@tanstack/virtual`)。预估 2-3 天。

### P-2【高】自动分块 170 次模型调用:零并发、零重试、零取消、零断点
- `ai-fusion-video/.../service/script/ScriptAutoSplitService.java`:
  - L83-87 全局 `newFixedThreadPool(2)`:所有用户共享 2 个线程,单剧本内 L226-240 for 循环**严格串行**逐块调用。170 块 × 10-30s/块 ≈ 30-85 分钟占死 1 个线程;两个长剧本即拖垮全局解析能力。
  - L231-238 单块转换失败仅告警并原文兜底保存 —— **静默降级**:用户不知道哪几集是"没解析的原文",质量不可见。
  - 无 429/5xx 退避重试;无断点续跑(应用重启后 `recoverStaleParsing` 只会把任务标失败,重跑=清空重来 L213-216);无取消 API(170 块提交后只能干等)。
  - **排队任务免疫滞留恢复**:`markScriptRunning` 在入队前登记(L122),`recoverStaleScripts` L172-175 对内存表内任务一律跳过 —— 在 2 线程满载时,排在队列里的任务 `update_time` 停在"排队中"却永远不会被判滞留,可无限期滞留。
- 修复(按优先级):① 块级并发 2-4(每剧本)并配全局信号量;② 失败块显式落库标记(如 episode.status=兜底)并在前端可见+单块重跑;③ 按 chunkIndex 断点续跑;④ 取消端点 + 排队任务纳入滞留恢复。预估 2-3 天。

### P-3【中】后端 N+1 查询(高频路径)
- `VideoComposeService.collectVideoUrls`(L270-286):每场一次 `listItemsByScene` —— 单集 10-20 场 = 10-20 次查询,合成是后台任务影响小,但与 P-1 共用同构反模式。修复:一次 `scene_id in (...)` 批查。
- `ProductionRunService.reconcile`(L308-312):循环内 `takeMapper.selectOne` ×3(有界,轻微);`detail()` 本身是 5 次固定查询 ✅。
- `DashboardActivityService.getActivity`(L51-53):`listAccessibleByUser` 拉全量 Project 实体只为取 ID 列表(团队场景全表实体进内存);且 L72-102 图片/视频任务仅按 userId 过滤,项目可见性未下推。修复:只查 id 列 + 实体投影。预估 0.5 天。

### P-4【中】上传/下载内存放大
- `FileUploadController` L92/L129:`file.getBytes()` 把最大 100MB 整体读进堆;`ReferenceImageTransportService.loadBinaryResource` L197 `response.body().bytes()` 对远程下载**无大小上限**(对照 ComfyUI 链路有 20MB `readBounded`,属同一功能点两种标准)。并发场景 OOM/慢速攻击面。修复:统一 `readBounded` + 流式落盘。预估 0.5-1 天。

---

## 三、技术债扫描

### T-1【中】超行数源文件(违反 AGENTS.md 1000/500 行红线)
后端(main java,>500 行,共 10 个):
| 行数 | 文件 |
|---|---|
| 1132 | service/generation/image/strategy/support/OpenAiCompatibleImageProtocolSupport.java(超 1000 红线,最优先) |
| 963 | service/ai/run/DurableAgentWaitingStateService.java |
| 839 | service/ai/run/DefaultRunExecutionSupervisor.java |
| 730 | service/storyboard/StoryboardService.java |
| 717 | service/generation/video/strategy/support/OpenAiCompatibleVideoProtocolSupport.java |
| 709 | service/storyboard/VideoComposeService.java |
| 703 | service/ai/agentscope/skill/AgentSkillImportService.java |
| 691 | config/ai/AiAgentRegistry.java |
| 658 | service/ai/agentscope/AgentScopePipelineRunService.java |
| 636 | service/ai/provider/OpenAiResponsesAgentScopeModel.java |

前端(>500 行,共 12 个,前列):
| 行数 | 文件 |
|---|---|
| 1637 | storyboards/_components/storyboard-ref-panel.tsx(超 1000 红线) |
| 1541 | storyboards/page.tsx(超 1000 红线) |
| 1465 | components/dashboard/asset-detail-sheet.tsx(超 1000 红线) |
| 1140 | lib/store/pipeline-store.ts |
| 1028 | components/dashboard/notification-panel/detail.tsx(超 1000 红线) |
| 994 / 976 / 974 / 934 | generation-workbench.tsx、model-config-support.tsx、ai-models/page.tsx、settings/general/page.tsx |

SW-T07 已立拆分计划,本报告确认:**至少 4 个前端 + 1 个后端文件已破 1000 红线**,拆分优先级应以红线文件为先。

### T-2【中】状态映射重复(同一语义 N 处维护)
- 生产运行状态标签:`app/(dashboard)/projects/[id]/production/page.tsx` L14-17 `RUN_STATUS_LABELS` 与 `storyboards/_components/production-take-drawer.tsx` L44-49 `statusLabels` **逐键重复**(CREATED/WAITING_GENERATION/QC_PENDING/SELECTED/FAILED → 同一组中文);后端 `DashboardActivityService` L109-137 又写第三份语义。
- 任务数值状态(0 排队/1 生成中/2 完成/3 失败):`components/dashboard/generation/generation-utils.ts` L21-23 一份,散落组件内联判断(此前 grep 命中 31 个后端文件用裸 `Integer.valueOf(1/2/3).equals(status)`)。
- QC PASS/FAIL 配色:`production-take-drawer.tsx` L58-61 `takeStatusClass` 内联 tailwind 色值,与其它 badge 场景重复。
- 修复:前端建 `lib/constants/production-status.ts`(标签+色调+排序权重一处),后端任务状态抽枚举类。预估 0.5-1 天。

### T-3【低】Magic Number / 重复常量
- HTTP 超时三元组 `connectTimeout(1, MINUTES) + readTimeout(25, MINUTES)` 在 ≥6 处复制粘贴(AbstractAiProvider、DashScopeGenerationSupport、GoogleFlowReverseApiClient×2、ReferenceImageTransportService、LocalStorageStrategy、VideoComposeService、ComfyUiInputResourceService)——调优时必然漏改。
- `ScriptAutoSplitService` L64 `STALE_PARSING_THRESHOLD_MINUTES=30` 硬编码(SW-T10 已为 GenerationTaskReaper 2h 立项,建议一并参数化到配置)。
- `VideoComposeService` L584 `maxLength=4000`、`MAX_CHUNKS=400`、`MODEL_INPUT_MAX=9000` 等散常量,建议集中配置类。

### T-4【中】无测试的公共路径
- `ApiConfigController`/`ApiConfigService`:**零 controller 测试**(若存在权限矩阵测试,A-1 密钥泄露不可能存活至今)。
- `DashboardActivityService`:零测试。
- `LocalStorageStrategy`:零测试(S3 有 `S3StorageStrategyTests`,local 策略是默认路径反而裸奔)。
- `FileUploadControllerTests` 只覆盖 assistant-upload 四个用例,`/upload` 的扩展名走私(A-4)与 subDir 穿越(A-3)无用例。
- `ComfyUiInputResourceServiceTests`:无内网地址拒绝用例(因为功能本身缺失,见 S-3)。
- 修复顺序建议:先给 A-1/A-3/A-4 写红→绿测试再修,防止回归。预估 1-1.5 天。

---

## 四、安全抽查(apiKey 暴露面 / 文件上传)

### A-1【高】任意登录用户可拉取全部 API Key / AppSecret / 代理密码(明文)
- `ApiConfigController.java`:`/page`、`/create`、`/update`、`/delete`、`/remote-models` 均有 `@PreAuthorize("hasRole('ADMIN')")`,但 **`GET /api/ai/api-config/get`(L81-87)与 `GET /api/ai/api-config/list`(L98-102)没有**;SecurityConfig 兜底 `anyRequest().authenticated()`。
- `ApiConfigRespVO` L22-25 原样承载 `proxyPassword/apiKey/appSecret`,`ApiConfigConvert` 为纯 MapStruct 映射、无脱敏 → 普通用户(uitest 账号即可验证)一个 GET 拿走全部密钥。
- 修复:`/get`、`/list` 补 `@PreAuthorize("hasRole('ADMIN')")`;若前端需要展示,RespVO 改脱敏(`sk-***last4`),更新时空值=不修改。预估 0.5 天。

### A-2【高】ApiConfig 实体 `@Data + @ToString(callSuper=true)`:toString 含密钥
- `entity/ai/ApiConfig.java` L17-22:Lombok 生成含 `apiKey/appSecret/proxyPassword` 的 toString。任何 `log.xxx("... {}", apiConfig)`、调试打印、错误包装都会落盘密钥;当前 grep 未发现实际打印点,但这是"一次顺手日志 = 密钥进日志系统"的地雷。
- 修复:改 `@Getter/@Setter` + 手写脱敏 toString(或 `@ToString.Exclude` 三个密钥字段)。预估 0.25 天。

### A-3【高】上传 subDir 目录穿越
- `FileUploadController.upload`(L74-76):`subDir` 为用户可控 `@RequestParam`,直传 `MediaStorageService.storeBytes` → `LocalStorageStrategy.storeBytes`(L77-85)`Paths.get(basePath, subDir)`,无 normalize/contains 检查。`subDir=../../../../etc` 或 `..\\..\\` 可在进程权限内任意目录落盘(文件名为随机 UUID,难覆盖指定文件,但可污染任意路径、配合其它解析机制可升级)。assistant-upload 的 subDir 是常量不受影响。
- 修复:对 subDir 做白名单(仅 `[a-zA-Z0-9_/-]`,禁 `..`)+ `normalize().startsWith(basePath)` 断言。预估 0.25 天。

### A-4【高】扩展名与 Content-Type 脱钩 → /media 静态源存储型 XSS
- `FileUploadController.upload` 用 `Content-Type`(客户端可任意伪造)过图片白名单,却用**原始文件名**取扩展名(L91 `getExtension(file.getOriginalFilename())`):上传 `x.html` + `Content-Type: image/png` → 白名单通过 → 落盘 `uuid.html` → `WebMvcConfig` 的 `/media/**` 静态映射按 `text/html` 回源 → 同源任意 HTML/JS 执行(打管理员 cookie/CSRF)。
- 下载链同类问题:`LocalStorageStrategy.guessExtension` L131 显式支持 `svg`(svg+xml 可执行脚本),且 URL 后缀兜底接受任意 ≤5 字符扩展(`.html`/`.jsp` 等)。
- 修复:扩展名改为从 Content-Type 白名单映射(assistant-upload 的 `ASSISTANT_UPLOAD_TYPES` 就是正确范式,`/upload` 未复用);响应头加 `Content-Security-Policy`/`X-Content-Type-Options: nosniff`;图片做魔数校验(Sniffing/Tika)。预估 0.5-1 天。

### A-5【中】日志泄密面:上游错误响应体全量进日志
- `AbstractAiProvider.executeGet/executePost`(L294-299/L318-323):失败时 `log.error(... body={}", body)` 输出完整上游响应体。OpenAI/DashScope 类错误常回显密钥片段("Incorrect API key provided: sk-abc***")或内部端点信息 → 密钥间接进日志。
- 修复:body 只落 preview(200 字符)+ 密钥正则打码。预估 0.25 天。

### A-6【中】上传配额与类型深检缺失
- `/api/storage/upload`、`/assistant-upload` 无用户级配额:任意登录用户可无限次传 100MB 刷爆磁盘(localStorage)或 S3 账单。无魔数校验,Content-Type 完全信任客户端。修复:每用户日配额 + magic byte 快检。预估 1 天。

---

## 五、产品进化提案(用户视角,≥3 条)

### E-1 批量镜头操作(高价值)
170 集项目里"逐个点按钮"是当前最大操作瓶颈。需要:分镜表格多选 → 批量生成图/视频/重生成/删除/移动到其他场次/统一改画风参考。后端已有 `items/batch` 与 `batch-sort`,UI 与任务编排层缺失。与 SW-T11 呼应,建议优先立项。

### E-2 任务中心:取消、进度明细、断点续跑(可靠性)
当前自动分块/合成一旦启动,用户只能等或开新页。需要统一任务中心:实时进度(第 N/170 块、每块耗时)、**取消**按钮、失败块列表与**单块重跑**、历史任务重入。与 P-2 后端修复天然配套,是同一需求的用户面。

### E-3 解析质量验收面板(信任链)
自动分块对失败块的"原文兜底"是静默的。用户需要:解析完成后展示"成功 N 块 / 兜底 M 块"标记,在剧本树中高亮兜底集,一键对兜底集重跑解析;并提供 chunkChars/模型 A/B 重解析对比视图。没有这个,170 集解析结果不可信。

### E-4 字幕导入与对齐回写(闭环补全)
已有 SRT 导出,缺反向:导入 SRT/ASS → 按时间码对齐分镜条目 → 人工校对 → 回写台词/重新合成。成片-字幕的最后一公里目前在工具外完成。

### E-5 模板库(画风/镜头/工作流组合)
预设画风已有基础(PresetArtStyleResourceResolver),缺"项目级模板":镜头景别组合、生图参数预设(分辨率/风格强度)、ComfyUI 工作流模板打包复用,让第 2 部剧的启动成本趋近于零。

---

## 六、Top 5 发现摘要(按风险排序)

| # | 级别 | 发现 | 一句话修复 |
|---|---|---|---|
| 1 | 高 | A-1 `/api/ai/api-config/list`、`/get` 无权限注解,任意登录用户明文拉取全部 apiKey/appSecret/proxyPassword | 补 `@PreAuthorize(ADMIN)` + RespVO 脱敏 |
| 2 | 高 | S-3 ComfyUI 输入下载与 LocalStorageStrategy.store 两条服务端取数旁路完全没接 SSRF 校验(且自动跟随重定向) | 统一安全下载组件强制过 validator |
| 3 | 高 | S-1/S-2 ReferenceImageTransportService:302 重定向绕过 + DNS rebinding TOCTOU(校验后连接二次解析) | 关闭自动重定向逐跳校验;OkHttp Dns 层 IP 固定 |
| 4 | 高 | A-3/A-4 上传 subDir 目录穿越 + 扩展名走私致 /media 存储型 XSS;A-2 ApiConfig toString 含密钥待爆雷 | subDir 白名单、扩展名从 Content-Type 映射、脱敏 toString |
| 5 | 高(性能) | P-1/P-2 170 集树加载并发打 170+ 请求且无虚拟化;自动分块 170 次模型调用全局串行、无取消/断点/重试,排队任务免疫滞留恢复 | 后端树聚合端点 + 块级并发/断点/取消 |

次级(中):A-5 日志泄密面、P-3 后端 N+1、T-1 超行文件(5 个破千行线)、T-2 状态映射三处重复、S-4 VideoCompose 第二套弱校验(fd00::/8 ULA 放行)。

## 验证与回归建议
- 修复前先补红测试:A-1 权限矩阵、A-3/A-4 恶意 subDir 与 .html 上传、S-3 内网地址下载拒绝用例(ComfyUiInputResourceServiceTests 扩展)、S-1 重定向用例(需本地 mock http server,不触外网)。
- 后端回归:`./mvnw -Dtest=FlywayMigrationNamingTests test` 不受影响;建议追加 `mvnw -Dtest='PublicHttpUrlValidatorTests,FileUploadControllerTests'`。

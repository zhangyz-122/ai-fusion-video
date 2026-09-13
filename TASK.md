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

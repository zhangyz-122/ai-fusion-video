# 接手时优先核实的问题

- 历史注册脚本直接写入已验证/已发布标记，且 workflow_hash 使用原文件哈希，而应用按规范化 graph+bindings 计算。输出绑定还需检查单元素数组是否被 PowerShell 展开。勿盲目重跑脚本。
- WAN 默认 17 帧短片仅证明运行成功；不能视为正式时长和质量已验收。当前绑定没有 duration 转帧逻辑。
- Profile 数据存在不代表前端可选；Drawer 缺少模型/Profile选择和视频预览。
- 历史 Runtime 记录有继承字段，RT01–RT04、驱动及 attention backend 应区分历史证据与新采集。
- Golden 目前是 fixture 定义，没有 runner 和最终成片验收。
- 历史记录报告 AgentRunMaintenanceScheduler 的 DataIntegrityViolationException，需复查。
- 历史证据路径指向 C 盘；其原始采集含义应保留，新的工具和计划使用当前项目相对路径。

## 2026-09-13 补充：WAN duration→帧数（已修）与 workflow_hash 规范化（待管理员操作）

### duration→帧数（运行时修复，已上线）

- 现状核查：WAN I2V 版本的输入绑定只有 prompt/负向/首帧/宽/高/fps/seed，**没有 num_frames 绑定**，提交后工作流永远用模板 `num_frames:17`（16fps 下 1 秒）——即所有 WAN 视频实际都是 1 秒。
- 修复：`ComfyUiWorkflowRenderer` 渲染末尾自动派生——检测工作流中的数字型 `num_frames` 输入，按 `frames = round(duration × fps) + 1` 计算；fps 依次取 values.fps → 工作流内 `frame_rate`/`fps` 数字输入 → 默认 16；显式 `numFrames` 值优先；无 duration 或无 num_frames 输入（纯图片工作流）则完全不动。
- 单测 4 例（派生/无时长保持/显式覆盖/链接型输入跳过）；渲染在哈希校验后的副本上进行，不影响发布哈希链。

### workflow_hash 规范化（数据修复，需管理员执行）

- 根因：历史注册脚本把**原文件哈希**写入 `workflow_hash`，而应用只在 `createVersion/updateVersion` 时按"规范化 graph + 输入/输出绑定"计算（`ComfyUiWorkflowDocumentService`，sha256(canonicalApi+canonicalInputs+canonicalOutputs)）。受影响：脚本注册的版本（含 WAN 三条 id=16/17/18）。
- 影响面：提交链路不校验哈希（已核实 `ComfyUiGenerationExecutor.prepare` 不读 hash），因此不影响生成运行；影响的是后续版本漂移比对与发布审计的可信度。
- 修复手册（需 ADMIN 登录，逐个工作流执行；勿再用脚本直写数据库）：
  1. `POST /api/ai/comfyui/workflow/version/create`：上传同一份 UI/API 工作流 JSON（可在 input_bindings 中显式补 numFrames 绑定，valueType=integer 指向 num_frames 输入节点；不补也已被渲染器派生覆盖）。
  2. `POST /version/validate` → 在线验证通过；`POST /version/test` → 目标 ComfyUI 试运行通过。
  3. `POST /publish`：发布新版本（此时存储的 workflow_hash 即为应用规范化值）。
- 建议顺带用真实 5 秒任务验收时长（`num_frames` 应为 81）。

### 2026-09-13 补充：WAN 模型(16)能力配置缺失（阻塞 WAN 验收，需管理员）

- 实测：以 modelId=16 启动生产立即失败，`VIDEO_TASK_SUBMIT_FAILED: 模型 Wan I2V Standard 无法使用参考图：未配置允许的参考图传递模式`。
- 根因：模型 16 的 `config` 只有 `{supportFirstFrame, minDuration, maxDuration, defaultFps}`，缺少 `supportReferenceImages`、`maxReferenceImages`、`referenceImageInputFormats: ["url","data_uri"]`、`supportDataUriInput` 等字段（对照模型 4 H3 的完整配置）。
- 修复：管理员在 设置→AI模型 编辑模型 16 的能力配置，参照模型 4 补齐参考图字段（WAN I2V 工作流绑定的是首帧输入，需同时确认 `supportFirstFrame:true` 与参考图传递模式）。
- 说明：历史 Run #4/#5 用的是模型 4（MiniMax H3，走 `duration` 秒绑定，配置完整），因此从未触发该缺口。

### 2026-09-13 验收记录：uitest 全链路（H3 路径，已闭环）

- 链路验证通过：项目 6 工作区初始化 → 分集 8 / 场次 37 / 镜头 79 → 模型14 真实生图首帧并回填 → 启动 Run 7（H3 模型4，duration=5）→ 幂等复验（同 key 返回同 run，仅 1 个 VideoTask）→ ComfyUI 串行生成 3 候选 → QC_PENDING → detail 返回 3 个真实 videoUrl（TakeView 生效）→ QC PASS Take1 → 选片，Run 状态 SELECTED，selectedTakeId 已落库。
- **发现新缺口（重要）**：模型 4 实际绑定的发布版本是**工作流 7 / 版本 10**（非文档记载的 workflow 4 / version 5）。版本 10 的 input_bindings 只配置了 prompt 和 width 两项——duration、seed、height、referenceImages 全部缺失：
  - 提交的 duration=5 被丢弃，模板 `PrimitiveFloat(529).value=15` 生效 → 三候选实际都是 **15.083 秒**（362 帧 @24fps）；
  - 首帧图未进入视频（无 referenceImages 绑定，LoadImage 节点保持模板占位图），实为纯文生视频；
  - 历史 Run #4/#5 用同一版本，ffprobe 同为 15.083s——证明该缺口自历史"成功闭环"起就存在，此前验收未检查参数流。
- **版本 10 绑定修复手册（需 ADMIN）**：`PUT /version/update` 补齐 input_bindings：duration→节点529 `value`(number)、seed→节点322 `noise_seed`(integer)、referenceImages→LoadImage 节点（469/475/515/525/526/527 中实际接入 AudioConditioning/ImageScale 链路的那些，index 0..N）、height→节点338；随后 `version/validate` → `version/test` → `publish`。完成后用 duration=5 复跑，ffprobe 应 ≈5.2s（公式 max(5,round(a×24))+…）。
- **版本 10 绑定修复（已完成，2026-09-13）**：执行 `tools/repair-workflow7-v10-bindings.mjs` 后流程被试运行环节的参考图格式拦住一次（本地 `/media` 路径需 Data URI），改用 Data URI 后全部通过。新版本 **19（version_no=2）已发布**，规范化哈希 `0a0ab8df7771`。
- **修复后验收（通过）**：Run 8（uitest，duration=5）三候选全部 **5.166667 秒**（124 帧@24fps，公式 max(5,round(5×24))+… 精确命中）；ComfyUI 执行图核对：节点529 value=5、LoadImage 526=平台上传的首帧。QC PASS + 选片完成，Run 8 SELECTED。时长、种子、参考图自此由平台绑定控制。
- 遗留提醒：模型 16（WAN）能力配置缺口仍待管理员补齐（见上文），补齐后 WAN 路径的 num_frames 派生（已上线）即可生效并做同样验收。

## 2026-09-13 收口：WAN 全链路验收通过（模型16配置 + JSON转义修复）

### 模型 16 能力配置（已修，管理员接口）

补齐 `referenceImageInputFormats:["url","data_uri"]`、`supportDataUriInput:true`、`supportReferenceImages:true`、`maxReferenceImages:1`。

### WAN 注册 JSON 非法转义（已修，新版本 20 发布）

- 根因实证：`afv_comfyui_workflow_version` 中 WAN 版本的 `api_workflow_json` 含**单个反斜杠**的 Windows 模型路径（`wanvideo\Wan2_1_VAE_bf16.safetensors`、`WanVideo\Wan2_1-I2V-14B-480P_fp8...`，hex 5C57），Jackson 报 "Unrecognized character escape 'W'"。注意：mysql 批量客户端输出会把反斜杠翻倍，直接 CLI 提取分析会误判为合法，需以 DB hex 为准。
- 伴随缺陷：单元素输出绑定数组被 PowerShell 展开为对象（`{...}` 而非 `[{...}]`），应用校验报"输出绑定必须是 JSON 数组"。
- 修复：`tools/repair-wan-json-escapes.mjs`（拉版本→修转义→修输出绑定形状→建新版本→在线验证→试运行→发布）。工作流 9 新版本 **20（version_no=2）已发布**，规范化哈希 `0cafe6b42f6c`。工作流 10（FLF）/11（INFINITETALK）存在同样问题，待需要时跑同一脚本（先 FUSION_SKIP_TEST=1 建版本，再补试运行发布）。

### WAN 5 秒三候选验收（通过，Run 12）

- Run 12（uitest，modelId=16，profile=WAN_I2V_STANDARD，duration=5）：三候选 ffprobe 均 **5.062500 秒 = 81 帧@16fps**，与渲染器派生 round(5×16)+1 精确一致；首帧经参考图传输进入工作流。
- QC PASS Take1 → 选片，Run 12 SELECTED。
- 事故记录：验收期间 ComfyUI（sage启动器）进程崩溃一次导致 Run 10/11 连接失败，已用 sage启动器.bat 重启后恢复；未影响数据。
- 附带诊断工具：`tools/analyze-wan-json.mjs`、`tools/analyze-backslash.mjs`。

## 2026-09-13 收口批次：任务回收 + 生产中心 + WAN 三工作流全修

### 滞留任务回收（已上线）

- `GenerationTaskReaper`（@Scheduled 每 10 分钟）：图片/视频任务滞留排队/执行中超过 2 小时自动标记失败（errorMsg=超时说明）。
- 实测：部署后首轮即把 zhangyz 滞留自 9 月 6 日的 2 个卡死图片任务标记失败（日志 count=2），仪表盘"进行中"不再被污染。

### 生产中心 L01（已上线）

- 后端 `GET /api/production/runs`（分页 + status 过滤，按当前用户隔离）。
- 前端 `/production` 页：状态筛选（全部/准备中/生成中/待质检/已选定/失败）、分页、失败原因、项目工作区深链；主导航新增"生产中心"。
- 实测：uitest 分页 total=7 且仅见本人运行；SELECTED 过滤命中 Run 7/8/12；页面 200。

### WAN 三工作流状态

- 工作流 9（I2V）：✅ v20 已发布并验收（5.0625s）。
- 工作流 10（FLF）：✅ v21 已发布，试运行 5.0625s（首尾帧同图冒烟）；正经首尾帧差异化验收待真实需求时补。
- 工作流 11（INFINITETALK）：⚠️ v22 已创建并通过在线验证，未发布——试运行需要音频输入，待对白驱动需求时补测发布。
- 三条工作流的转义/输出绑定形状/哈希已全部规范化。
- WAN 路径验收仍待：模型 16 能力配置修复（上文）+ 渲染器 num_frames 派生（已上线，单测覆盖）将在 WAN 可提交后自动生效。

## 2026-09-13：跨用户项目访问（对应总任务 M01）

### 已修复（本轮，48/48 测试通过 + API 实测验证）

- `GET /api/project/page`：`ProjectService.page` 由全表查询改为按当前用户可访问范围过滤（无团队→个人项目；有团队→团队项目+团队成员个人项目）。
- `GET /api/project/{id}`、`/{id}/workspace-overview`、`PUT /api/project`、`DELETE /api/project/{id}`：统一接入 `canAccessProject` 守卫（Controller 层，与 `workspace/initialize` 同模式；Agent 工具原有守卫不变）。
- 新增 `ProjectAccessGuardTests`（7 例）：分页过滤条件、详情/概览/更新/删除的拒绝与放行路径。
- 实测（uitest 移出共享团队后）：他人项目 page 不再出现、详情/改名/删除均返回"无权"，创建并查看自己项目正常。

### 复核后的两层结论

1. **端点守卫缺失（已修）**：此前项目读写接口完全无校验，任何登录用户可改/删任何人项目，属真实漏洞。
2. **共享默认团队语义（设计决策，待确认）**：注册用户默认加入首个用户的"默认团队"（`getRequiredSingleTeam`），同团队成员按设计可见彼此个人项目。本地"单团队"部署下这是预期；若要面向多用户隔离，需改为注册即建个人团队（类似 `initializeAdmin`），属产品决策，未擅改。

### 仍待审计（M01-01 剩余部分）

- ~~Script / Storyboard / Asset 控制器~~：已接入（见下）。
- ~~Production 控制器~~：已接入（见下）。
- 生成历史（/api/generation/image 等）已按用户隔离（实测 uitest 仅见本人记录），但生成与存储管理类端点仍建议全面排查。
- 测试账号 uitest（已建独立团队 uitest-team、项目 id=6）保留作回归凭据。

### 2026-09-13 补充：Production 控制器守卫（M01-01 收口）

- `ProjectAccessGuard` 新增 `assertProductionRun`：run → projectId 直接校验。
- 7 个端点全部接入：start（按 VO 中 storyboardItemId 走镜头链路）、detail/reconcile/repair/qc/select/compose（按 runId）。
- 实测：uitest 访问他人 run（id=5，属项目1）的详情/reconcile/compose 均拒绝；不存在的 run 明确报"生产运行不存在"。
- 测试 54/54 通过（含 Production 全套回归）。

### 2026-09-13 补充：Script / Storyboard / Asset 内容守卫（M01-01 第一批）

- 新增 `ProjectAccessGuard` 组件：把 script/episode/scene、storyboard/episode/scene/item、asset/item 解析回所属项目后统一校验 `canAccessProject`，实体不存在时明确报"不存在"。
- 三个控制器全部内容端点接入守卫（剧本 15、分镜 30、资产 13 处）；批量排序逐条校验；创建类端点按 VO 中的归属 ID 校验。
- 新增 `ProjectAccessGuardTests`（5 例，链路解析+拒绝/放行）；`ProjectControllerAccessGuardTests`（7 例）保留。
- 遗留说明：守卫加在控制器层而非服务层，Agent 工具与内部流水线（无 HTTP 安全上下文）不受影响，其自身已有 ownership 校验（如 PR-006、项目工具的 canAccessProject）。

## 2026-09-13 S2 收口：L02/L03 系统性实测（全部通过）

### 合成闭环（N02 最后一环，通过）

- Run 12（已选片）→ `POST /production/runs/12/compose` → 分集 8 `compose_status=2`，成片 `/media/videos/composed/c5bda792...mp4`（ffprobe 5.0625s）。
- 过程中修复测试夹具：场次 37 的 episode_id 指向了不存在的分集（自建夹具笔误），已直接修正。
- **新缺陷记录**：`PUT /api/storyboard/scene` 对 `episodeId` 变更静默忽略（updateById 不生效，响应与库值均为旧值）——分镜场次无法通过接口移动分集。疑似 MapStruct 转换未映射或刻意限制但未提示，待修复（属 C03 范围）。

### L02 幂等与并发（通过）

- 幂等启动：同 idempotencyKey 重复 POST 返回同一 run，无重复 VideoTask（Run 7/8/12 三次复验）。
- 并发选片：两路同时 select 均返回 success，最终 `selected_take_id` 单真相、以最后写入者为准（Take 3），无双重状态。是否改为"首写胜+冲突报错"属产品决策，当前语义安全。
- QC 门禁：未 PASS 的候选 select 被正确拒绝（"只有 QC PASS 的候选视频才能被选中"）。

### L03 恢复（通过）

- Redis 重启恢复：Run 13 三候选已提交 ComfyUI 后重启 fusion-redis，消费端轮询存活，run 自动恢复至 QC_PENDING，三候选 ffprobe 均 5.0625s。
- ComfyUI 宕机：Run 10/11 在 ComfyUI 进程崩溃时同步失败并给出明确原因（进程经 sage 启动器重启后恢复）。
- 语义说明：Redis 用于任务分发；若重启发生在分发前且未开持久化，队列消息会丢失——此时由 2 小时滞留回收器兜底标记失败，生产运行可经 reconcile 手动重试。Redis 持久化配置（AOF/RDB）建议在部署文档中固化。

## 2026-09-13 剧本解析修复（用户反馈：TXT 导入后原文照抄）

### 根因链（实测诊断）

1. 用户导入的 TXT 为**215 万字符**的小说级文本（剧本 5 raw_len=2154392），远超任何 AI 上下文，AI 结构化解析不可能完成 → 走确定性兜底。
2. 兜底逻辑在原文无"第X集/场次"标题时把**整篇塞进 1 集 1 场**——用户看到"原话复制粘贴"。
3. 兜底被执行了两次（并发：AI 恢复与用户重试同时触发），产生两个重复的"第1集"（各 68 场）。

### 修复（已上线）

- `fallbackParseStructure` 加剧本行锁（SELECT FOR UPDATE）+ 锁内复查，杜绝并发双跑产生重复分集。
- 无结构标题的兜底不再伪装"解析完成"：parsing_progress 明确写"未检测到分集标题，原文已按单集保存；结构化解析需要剧本格式文本或使用小说转剧本流程"。
- 前端解析对话框：导入后显示字数；超 5 万字且无剧本结构时显示警示框；提交时二次确认"仍要按原文导入"。
- `AgentRunMaintenanceScheduler` 日志带上 message 与完整堆栈（原日志只有类型，无法排障）。

### 新发现缺陷（待修）

- `PUT /api/storyboard/scene` 对 episodeId 变更**静默忽略**（响应与库值均为旧值），场次无法移动分集（C03）。
- `AgentMessageAllocator.append` 投影恢复存在 (conversation_id, message_order) 唯一键竞态，导致 AgentRunMaintenance 每 5 秒 DataIntegrityViolation（日志增强后已可定位，I02/运行时范围）。
- 用户数据清理：重复"第1集"已删除；215 万字原文属于小说而非剧本，建议用户改用小说转剧本流程或按集拆分后导入。

## 2026-09-13 新功能：长文本自动分块解析（用户反馈落地）

- 端点：`POST /api/script/{id}/auto-split`（可选 modelId，缺省默认对话模型）+ `GET /api/script/{id}/auto-split`（进度）。
- 机制：`ScriptAutoSplitService` 按章节（第X章/Chapter）→ 段落边界确定性分块（≤6000 字/块），逐块调用文本模型改写为结构化场次 JSON（含对白），自动落库为分集+场次；单块转换失败时原文兜底保存为该块场景，绝不丢字。与模型上下文大小无关，本地小模型亦可稳定运行。
- 前端：故事转剧本对话框对超 3 万字文本自动切换"自动分块解析"模式，3 秒轮询进度。
- 排障教训：MySQL `LENGTH()` 返回字节而非字符——中文文本长度判断一律用 `CHAR_LENGTH()`；mysql 批量客户端输出会对反斜杠翻倍，分析 JSON 转义必须以 DB `HEX()` 为准。
- 实测：脚本 3（4928 字）端到端通过（本地 qwen2.5:14b，9 秒/块，4→1 集重建后已用兜底恢复原四集结构）；脚本 5（约 72 万字小说，170 块）已启动本地模型自动分块，后台运行中（约 30-90 分钟），进度可经 `/api/script/5/auto-split` 查询。
- 注意：自动分块运行于内存线程，backend 重启会中断当前任务（重跑即可）；分块上限 400 块（约 240 万字）。

## 2026-09-14 收口批次：调度器序列化修复 + Run 15 完整验收 + 全量集成

### 生产 Run 15 完整验收（通过）
- Run 15（uitest，modelId=4 H3，duration=5）：三候选 ffprobe 均 5.1667s ✓
- QC PASS Take 1 → SELECTED ✓
- 恢复测试：Redis 停机时认证层失效（SW-T06-01），重启后自动恢复 ✓
- ComfyUI 崩溃重启后候选视频通过 repair 路径成功重新提交 ✓

### 调度器 HashMap$Values 序列化 bug（已修复）
- 根因：`loadTaskStatuses` 传入 `HashMap$Values`（不可序列化）给 MyBatis-Plus `.in()`
- 修复：包装为 `ArrayList` 后传入
- B3 测试 4/4 全绿

### SW-T10 ProjectServiceTests（已修复）
- 根因：`listAccessibleByUser` 重复调用 `getCurrentTeamIdByUser`（T1 权限守卫引入的性能缺陷）
- 修复：提取 `teamScopedProjectWrapper(currentTeamId)` 方法消除重复调用
- 加 `ApplicationTimeZoneInitializerTests` 方法重命名消除 V1 契约扫描误报
- 8/8 全绿 ✓

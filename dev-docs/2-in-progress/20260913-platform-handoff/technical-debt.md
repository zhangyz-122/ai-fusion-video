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
- **一键修复脚本（已就绪，2026-09-13）**：`tools/repair-workflow7-v10-bindings.mjs`。已按真实版本 10 JSON 离线渲染验证通过（duration→529、seed→322、参考图 6 槽 index0-5 按 ref_image_0..5 槽位序 526/527/525/515/475/469，未供图槽位自动剪枝、prompt/width/数学链不受影响）。执行方式：
  ```
  FUSION_ADMIN_USER=zhangyz FUSION_ADMIN_PASSWORD=*** node tools/repair-workflow7-v10-bindings.mjs
  ```
  脚本自动完成：建新版本（规范化哈希同步修复）→ 在线验证 → 真实试运行（占用 GPU 数分钟，用验收首帧图）→ 发布。试运行可 FUSION_SKIP_TEST=1 跳过，但未试运行无法发布。
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

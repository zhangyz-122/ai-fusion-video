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

# 接手时优先核实的问题

- 历史注册脚本直接写入已验证/已发布标记，且 workflow_hash 使用原文件哈希，而应用按规范化 graph+bindings 计算。输出绑定还需检查单元素数组是否被 PowerShell 展开。勿盲目重跑脚本。
- WAN 默认 17 帧短片仅证明运行成功；不能视为正式时长和质量已验收。当前绑定没有 duration 转帧逻辑。
- Profile 数据存在不代表前端可选；Drawer 缺少模型/Profile选择和视频预览。
- 历史 Runtime 记录有继承字段，RT01–RT04、驱动及 attention backend 应区分历史证据与新采集。
- Golden 目前是 fixture 定义，没有 runner 和最终成片验收。
- 历史记录报告 AgentRunMaintenanceScheduler 的 DataIntegrityViolationException，需复查。
- 历史证据路径指向 C 盘；其原始采集含义应保留，新的工具和计划使用当前项目相对路径。

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

- ~~Script / Storyboard / Asset 控制器~~：已在本批接入（见下）。
- Production 控制器（run/take/qc/select/compose）尚未接入守卫：需经 ProductionRun → step/storyboardItem → project 链路解析，留作下一片。
- 生成历史（/api/generation/image 等）已按用户隔离（实测 uitest 仅见本人记录），但生成与存储管理类端点仍建议全面排查。
- 测试账号 uitest（已建独立团队 uitest-team、项目 id=6）保留作回归凭据。

### 2026-09-13 补充：Script / Storyboard / Asset 内容守卫（M01-01 第一批）

- 新增 `ProjectAccessGuard` 组件：把 script/episode/scene、storyboard/episode/scene/item、asset/item 解析回所属项目后统一校验 `canAccessProject`，实体不存在时明确报"不存在"。
- 三个控制器全部内容端点接入守卫（剧本 15、分镜 30、资产 13 处）；批量排序逐条校验；创建类端点按 VO 中的归属 ID 校验。
- 新增 `ProjectAccessGuardTests`（5 例，链路解析+拒绝/放行）；`ProjectControllerAccessGuardTests`（7 例）保留。
- 遗留说明：守卫加在控制器层而非服务层，Agent 工具与内部流水线（无 HTTP 安全上下文）不受影响，其自身已有 ownership 校验（如 PR-006、项目工具的 canAccessProject）。

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

### 仍待审计（M01-01，下一批）

- Script / Storyboard / Asset 等其余按 projectId 直取的控制器端点均未接入 `canAccessProject`（当前仅 ProjectController 接入）；需统一补齐读写守卫。
- 生成、存储、成员管理等控制器同类排查。
- 测试账号 uitest（已建独立团队 uitest-team、项目 id=6）保留作回归凭据。

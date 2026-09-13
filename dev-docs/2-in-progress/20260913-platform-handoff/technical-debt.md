# 接手时优先核实的问题

- 历史注册脚本直接写入已验证/已发布标记，且 workflow_hash 使用原文件哈希，而应用按规范化 graph+bindings 计算。输出绑定还需检查单元素数组是否被 PowerShell 展开。勿盲目重跑脚本。
- WAN 默认 17 帧短片仅证明运行成功；不能视为正式时长和质量已验收。当前绑定没有 duration 转帧逻辑。
- Profile 数据存在不代表前端可选；Drawer 缺少模型/Profile选择和视频预览。
- 历史 Runtime 记录有继承字段，RT01–RT04、驱动及 attention backend 应区分历史证据与新采集。
- Golden 目前是 fixture 定义，没有 runner 和最终成片验收。
- 历史记录报告 AgentRunMaintenanceScheduler 的 DataIntegrityViolationException，需复查。
- 历史证据路径指向 C 盘；其原始采集含义应保留，新的工具和计划使用当前项目相对路径。

## 2026-09-13 新增：跨用户项目数据隔离漏洞（对应总任务 M01，高优先级）

以仅 user 角色的全新账号 uitest 实测确认：

- `GET /api/projects/page`（ProjectController 分页）底层 `ProjectService.page` 为全表查询，无任何用户/团队过滤，普通用户可见他人全部项目。
- `GET /api/projects/{id}` 与 `GET /api/projects/{id}/workspace-overview` 无 `canAccessProject` 校验，普通用户可读取他人项目详情、剧本状态与资产库。
- `ProjectService.update` / `ProjectService.delete` 无权限校验，任何登录用户可修改或删除他人项目（含级联删除剧本/分镜）。
- 现成权限原语已存在：`listAccessibleByUser`、`canAccessProject`、`isMember`（`/api/projects/list` 已正确使用）。修复方向：读接口统一接入 canAccessProject，写/删接口加 owner 或成员校验，并审计 asset/script/storyboard 等其余按 projectId 直取的接口是否有同类问题。
- 测试账号 uitest / 一条生图记录保留作为复现凭据；该账号仅本地测试环境使用。

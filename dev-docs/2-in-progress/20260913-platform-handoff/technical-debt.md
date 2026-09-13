# 接手时优先核实的问题

- 历史注册脚本直接写入已验证/已发布标记，且 workflow_hash 使用原文件哈希，而应用按规范化 graph+bindings 计算。输出绑定还需检查单元素数组是否被 PowerShell 展开。勿盲目重跑脚本。
- WAN 默认 17 帧短片仅证明运行成功；不能视为正式时长和质量已验收。当前绑定没有 duration 转帧逻辑。
- Profile 数据存在不代表前端可选；Drawer 缺少模型/Profile选择和视频预览。
- 历史 Runtime 记录有继承字段，RT01–RT04、驱动及 attention backend 应区分历史证据与新采集。
- Golden 目前是 fixture 定义，没有 runner 和最终成片验收。
- 历史记录报告 AgentRunMaintenanceScheduler 的 DataIntegrityViolationException，需复查。
- 历史证据路径指向 C 盘；其原始采集含义应保留，新的工具和计划使用当前项目相对路径。

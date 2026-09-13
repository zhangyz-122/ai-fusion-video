# 进度跟踪

## 2026-09-13

- [x] 确认现有视频生成页、能力解析和 ComfyUI 工作流绑定链路。
- [x] 设计统一入口 V1：固定底座，参数和参考素材集中在一页。
- [x] 修改视频工作台并隐藏主流程模型切换。
- [x] 更新创作总览和侧栏入口文案。
- [x] 前端 TypeScript/生产构建验证。
- [x] 增加平台内置漫剧 Skills，并配置显示名称。
- [x] 完成外部 Skills / MCP 筛选：采用开放格式，并将两套影视生产型开源包的导演、镜头 IR、资产追踪、能力检查、局部重试和非破坏 QC 原则平台化合并。
- [x] 修正智能体设置页：分开展示平台内置 Skills 与用户自定义 Skills，避免内置 Skills 被误显示为“我的 Skills 0”。
- [x] 将《RunningHub工作流研究》和《介绍Krea 2》的核心内容整理为本地 Markdown：生产编排架构、Krea 2 关键帧系统、统一整合路线。
- [x] 读取截图中未 @ 的相关任务，并将角色提示词、50种表情、角色设定板、视频技能研究、审计结论和 HTML/React/后端关联整理为本地 Markdown。
- [x] 建立本地知识库总入口、来源任务索引和时空感知旁支说明，明确来源内容、代码验证和真实模型验证的边界。
- [ ] 真实生产链路回归。

## 已验证

- `corepack pnpm build` 通过。
- `corepack pnpm lint` 通过，0 errors；仓库原有 25 条 warning 未新增错误。
- 本地 Docker 前端镜像已更新，`/generate/video` 与 `/generate/universal` 均返回 200。
- AgentScope SkillRegistry 与 Pipeline 相关测试 6/6 通过。
- 本地 Docker 后端已更新并确认加载 5 个内置 Skills；MCP 仍为关闭状态（0 个服务器、0 个工具）。
- 外部筛选报告已记录在 `external-skill-review.md`；未将宿主专用或权限过大的第三方 Skill/MCP 作为生产依赖。
- RunningHub/Krea 2 整理文档已写入当前任务目录；其中候选工作流参数和关联任务自测结果均标明需要本机 ComfyUI 真实验证。
- 角色与表演资产、导演运行时研究、HTML/React/后端关联及 TrafficLab/Spatial Engine 旁支已写入对应 Markdown；来源对话中的附件/截图仅作为参考，不被当作可执行代码。

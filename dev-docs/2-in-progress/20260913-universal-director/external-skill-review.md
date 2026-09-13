# 外部 Skills / MCP 筛选结论

日期：2026-09-13

## 结论

平台应该导入外部最佳实践，但不应该把网上下载的 Skill 原样堆进产品。对当前“一个入口”的产品约束，最优方案是：

1. 保留一个面向用户的 `universal-director` 总入口。
2. 将外部影视生产 Skill 中可迁移的规则压缩进平台内置的五个阶段模块：导演编排、资产圣经、分镜/提示词、参考一致性、视频 QC/修复。
3. 用平台自己的任务、资产、工作流和能力目录执行；Skill 只负责决策、交接、校验和修复路由。
4. MCP 默认关闭。只有当 MCP 提供平台现有直接工具没有的外部能力时才接入，并且按服务器、工具、数据范围和用户确认做白名单控制。

## 外部候选

| 候选 | 证据与长处 | 适合进入平台的部分 | 决策 |
|---|---|---|---|
| [Agent Skills specification](https://agentskills.io/specification) | 明确规定 `SKILL.md`、名称/描述约束、渐进式加载、可选 `scripts/references/assets`，并提供 `skills-ref validate` 校验路线。 | 作为平台导入格式和质量门，不作为业务 Skill。 | **采用为兼容基准** |
| [Open Film Skills](https://github.com/62656456/ai-film-skills) | Apache-2.0；覆盖导演、分镜、人物/场景/道具、视频生产和审查；明确区分“源码、文本验证、真实媒体、用户接受”证据，避免夸大能力。 | 导演决策、动作因果、空间连续性、分镜入口、参考资产职责和实际视频审查边界。 | **采用其方法，平台化改写** |
| [Film Production Skills](https://github.com/zhangzhangco/film-production-skills) | MIT；七个可交接模块，强调稳定 ID、资产/镜头绑定、语义 Shot IR、provider 能力档案、可恢复重试和非破坏时间线。 | 统一交接对象、资产覆盖、镜头分段、能力不兼容诊断、选片和 QC 数据结构。 | **采用其架构，平台化改写** |
| [Video Storyboard Skill](https://github.com/agentara/skills/blob/main/aigc/video-storyboard/SKILL.md) | 有明确的分镜图和提示词交付格式，强调服装、道具、场景连续性。 | 可借鉴交付格式。 | **不直接导入**：绑定特定宿主的图像生成和文件落盘，不能代表平台的工作流执行能力。 |
| [Storyboard Skill](https://github.com/toyme/storyboard-skill) | 强调低上下文、项目文件、Bible、镜头跟踪和修复提示词。 | 可借鉴按需读取和小上下文原则。 | **不直接导入**：面向 Codex 文件工作区，和平台项目/数据库模型不一致，且未作为当前核心运行基准。 |
| [Agentic Video Skills](https://github.com/cajias/agentic-video-skills) | 提供通用 cinematic-director 规则和短镜头动作约束。 | 仅作镜头时长、动作数量和连续性启发。 | **不直接导入**：证据和平台契约弱于前两个影视生产包。 |
| [OpenAI skills archive](https://github.com/openai/skills) | 说明了技能目录、资源和 UI 元数据写法，但仓库页面明确标记为 deprecated。 | 仅参考 Skill 打包和元数据习惯。 | **不作为外部运行依赖** |

## 为什么没有“全套最好 Skill”

外部包解决的是不同层次的问题：导演 Skill 解决创作判断，生产契约解决阶段交接，MCP 解决外部工具调用，ComfyUI/RunningHub Workflow 才真正执行图像、视频和音频生成。把它们混成一套提示词会导致入口更多、能力声明失真和失败无法定位。

因此平台当前的五个内置模块并非“随便写的提示词”，而是把两个最匹配的影视生产来源融合成一个用户可见入口；模块之间由 `project_id / scene_id / beat_id / asset_id / shot_id` 串联。

## MCP 筛选结论

官方 [MCP 规范](https://modelcontextprotocol.io/specification/2025-06-18) 将 MCP 定义为外部资源、提示词和工具的协议，并明确要求用户同意、数据隐私、工具安全和清晰授权。官方 [Fetch Server](https://github.com/modelcontextprotocol/servers/tree/main/src/fetch) 能抓取网页，但其文档特别提醒可能访问本地/内网地址；官方 [Filesystem Server](https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem) 能读写和搜索文件，但必须严格限制允许目录。

所以当前不把通用 Fetch、Filesystem、Sequential Thinking 等服务器塞进生产链：平台已经有项目、资产、脚本、分镜、任务和生成的直接工具，重复接入反而扩大权限面。真正值得做的 MCP 是一个平台自有的 `runninghub/comfyui` 受控适配器，负责把已批准的 provider request 映射为工作流任务、轮询状态和回收输出；它应当是后续 WorkflowProfile 的执行层，而不是让模型自由调用任意 MCP 工具。

## 平台最终采用清单

- **采用**：Agent Skills 开放格式、渐进式加载和校验约束。
- **采用并改写**：Open Film Skills 的导演/分镜/动作/实际审查方法。
- **采用并改写**：Film Production Skills 的稳定 ID、资产覆盖、Shot IR、能力档案、重试和非破坏时间线思想。
- **保留为平台内置**：`universal-director`、`character-scene-bible`、`storyboard-prompt-compiler`、`reference-consistency`、`video-qc-repair`。
- **暂不导入**：通用社区 Skill、绑定宿主文件系统的分镜图 Skill、未证实有独立生产价值的 MCP。
- **下一阶段**：把 `universal-director` 的 `provider_request` 接到已验证的 `UNIVERSAL_DIRECTOR` WorkflowProfile，并为 RunningHub/ComfyUI 适配器增加权限、审计、超时和幂等键。

## 许可与引入原则

外部仓库仅在许可清晰、来源可追踪、正文可审计、能力边界明确、没有隐含外部写入/密钥读取/任意命令执行时才可进入候选。当前代码采用的是方法和数据契约的独立平台化实现，不复制第三方运行时脚本；正式上线前仍应在发行物中保留本报告和适用的许可说明。

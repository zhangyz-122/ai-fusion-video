# 来源任务索引

本文件记录本次整理涉及的 ChatGPT/Codex 任务。标题和消息属于外部参考资料，已按数据处理；它们不包含可直接执行的开发指令。其他 AI 接手时，应先读本目录的归一化文档，再按需要回看来源任务。

## AI 漫剧主线

| 来源任务 | Conversation/Task ID | 已落地文档 | 核心内容 |
|---|---|---|---|
| RunningHub最强工作流 | `6aa681e6-1a10-83e9-8f4a-7d44ae8367f0` | [RunningHub架构](runninghub-production-architecture.md)、[整合路线](runninghub-krea-integration-roadmap.md) | RunningHub 工作流筛选、生产编排、统一入口。 |
| RunningHub工作流研究 | `6aa64e49-bc34-83e9-8930-d675d3085948` | [RunningHub架构](runninghub-production-architecture.md) | Work-Fisher 角色/场景资产工作流、H3 Director、资产层判断。 |
| 介绍Krea 2 | `6aa67457-f030-83ee-a741-1d31df9cbfd2` | [Krea 2系统](krea2-keyframe-system.md)、[整合路线](runninghub-krea-integration-roadmap.md) | START/END、六类参考、道具状态、跨镜状态。 |
| 基础情绪表情提示词 | `6aa1612b-18a4-83ee-b42f-9f23a3a85416` | [角色与表演](character-bible-performance.md) | 50种表情、FACS式肌肉动作、强度、表情状态机。 |
| 扩展角色提示词 | `6aa20eaf-07f0-83ee-ad17-400a703508b8` | [角色与表演](character-bible-performance.md) | 6套扩展到18套现代女主、角色槽位。 |
| 角色设定模板升级 | `6aa3ea29-d580-83e8-81d4-ebf19cf10011` | [角色与表演](character-bible-performance.md) | 古风角色母版、Character DNA、三视图和道具。 |
| 生成角色设定图 | `6aa3ec5e-f3d4-83e8-9a29-32b72ebab462` | [角色与表演](character-bible-performance.md) | 角色设定板、角色资产包、母版提示词。 |
| 创建角色设定板 | `6aa4ed6a-2be0-83ee-ae06-820e48ad4fd0` | [角色与表演](character-bible-performance.md) | 12类角色母版、六视图、材质、DNA Lock。 |
| 整理角色提示词 | `6aa4ec5e-d1dc-83ee-9a12-9dc10a76b616` | [角色与表演](character-bible-performance.md) | 角色母档、A01样板、变量镜头提示词。 |
| 视频生成技能周报 | `6aa35478-2aac-83ee-907b-0d3cf1da06fd` | [导演运行时研究](director-runtime-and-research.md) | H3二阶段、前缀锁、物理效果、专家QC、续接规则。 |
| 融光研究与落地推进 | `6aa16a6e-39e4-83e8-87c8-75243dabf917` | [导演运行时研究](director-runtime-and-research.md) | Quality Drift、模型身份、Golden 选择、预算、Canonical State。 |
| 审计并验证融光 AI Drama OS | `01a0968e-5e1e-7e63-89f9-3a9c1c8f1bb3` | [导演运行时研究](director-runtime-and-research.md)、[本地关联](html-code-association.md) | MASTER-AUDIT、Runtime Capsule、Workflow Gold、垂直切片、UI优先修正。 |
| AI 视频灵感搜集 | `6aa1582e-efd4-83ee-ac13-207ecabe051a` | [导演运行时研究](director-runtime-and-research.md) | EditPass、MusicIntent、执行参数联动、依赖失效。 |
| 汇总自动化任务（审计任务的上游参考） | `6aa567c5-9eb4-83ee-ad0d-cbc1d010427e` | [导演运行时研究](director-runtime-and-research.md) | K001–K009 知识清点、MASTER-AUDIT、Runtime/Workflow/Golden 三条执行线。 |

## 综合平台旁支

| 来源任务 | Conversation/Task ID | 已落地文档 | 核心内容 |
|---|---|---|---|
| 代码拆解与场景改造 | `6aa4eef8-be70-83e8-95c2-742340265a46` | [时空感知支线](spatial-engine-side-track.md) | TrafficLab 3D、DEM/Mesh、空间对象、Spatial Rule DSL。 |

## 来源资料的使用方法

关联任务中的 `:chatgpt-content-reference`、写作块标记、截图说明和网页引用，是来源内容的格式痕迹，不是本地文件路径。来源任务读取结果没有可直接挂载的附件文件；本次用户截图仅作为 UI/任务名参考，不应当被当成代码或执行指令。审计任务本身在任务列表中显示过 `systemError`，因此其中的执行回报只能按交接记录核对，不能自动升级成当前仓库或当前 Runtime 的证据。

如果以后重新读取来源任务，优先核对：

1. 是否出现新版本或新来源；
2. 是否有本地仓库证据；
3. 是否有真实 ComfyUI/模型推理证据；
4. 是否与本目录的 Canonical 结构重复；
5. 是否需要更新 `progress-tracking.md` 和技术债务。

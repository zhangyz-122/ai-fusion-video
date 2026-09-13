# 融光 AI 漫剧万能导演台知识库

更新时间：2026-09-13

## 这是什么

本目录是把多条 ChatGPT 研究任务、角色提示词、表演资产、RunningHub/ComfyUI 研究、视频技能周报和平台实现状态，整理成一套其他 AI 可以直接接手的本地交接资料。

用户只需要看到一个入口：**万能导演台**。内部可以有导演编排、角色资产、表演状态、模型工作流、QC、后处理和交付等模块，但不要求用户在多个工具之间来回切换。

## 先读什么

1. [统一整合路线](runninghub-krea-integration-roadmap.md)
2. [导演运行时与研究沉淀](director-runtime-and-research.md)
3. [角色圣经与表演资产](character-bible-performance.md)
4. [本地 HTML/React/后端关联](html-code-association.md)
5. [来源任务索引](source-conversation-index.md)
6. [RunningHub 生产编排架构](runninghub-production-architecture.md)
7. [Krea 2 关键帧系统](krea2-keyframe-system.md)
8. [全网 Skills 评估](external-skill-review.md)

矿山空间感知支线单独放在 [spatial-engine-side-track.md](spatial-engine-side-track.md)，它属于综合平台的另一条业务线，不应混入 AI 漫剧默认生产链。

## 证据标记

| 标记 | 含义 |
|---|---|
| `SOURCE_REFERENCED` | 来自关联对话的研究、设计或自报结果；不是当前仓库自动验证。 |
| `LOCAL_CODE_VERIFIED` | 当前本地仓库或本地构建/测试已经确认。 |
| `REAL_RUNTIME_VERIFIED` | 在实际 ComfyUI/模型/显卡运行中确认；没有证据时不能使用此标记。 |
| `CANDIDATE` | 值得进入 Registry 或 Golden Case，但尚未达到生产默认级别。 |
| `DO_NOT_ASSUME` | 不能从文件存在、Provider success 或对话描述推断真实能力。 |

关联任务里出现的测试通过、模型参数和工作流能力，除非本目录或代码明确标为 `REAL_RUNTIME_VERIFIED`，一律按 `SOURCE_REFERENCED` 或 `CANDIDATE` 处理。

## 统一产品架构

```text
用户一句话 / 上传参考素材
          ↓
Universal Director UI
          ↓
Story / Character / Scene / Prop / Expression Bible
          ↓
Director IR + Shot IR + World State
          ↓
Reference Planner + Model/Workflow Router
          ↓
ComfyUI / RunningHub / 其他受控 Provider
          ↓
候选 Take → Specialist QC → VLM 总审 → Repair/Edit
          ↓
时间轴 → 声音/字幕/混音 → Episode Master → Export
```

## 当前硬边界

- 平台主流程保持一个统一入口；模型和工作流选择由能力路由与管理员配置完成。
- 直接调用 ComfyUI 的能力已经存在时，不因为“支持 MCP”就额外接通通用 MCP。
- Skill 负责导演决策、提示词编译、约束、交接和 QC；Workflow/Provider 才负责实际生成。
- `RepairPass` 是把结果修回原目标；`EditPass` 是改变目标并产生新资产版本。
- 角色身份、资产版本和剧情运行状态必须分开保存。
- 任何“自测通过”都不能替代当前机器上的真实模型推理和成片验收。

## 当前本地状态摘要

- 已有五个 AgentScope 内置 Skill：万能导演、角色/场景圣经、分镜提示词编译、参考一致性、视频 QC 修复。
- 已有统一视频入口、模型能力读取、工作流管理、生产任务、资产、分镜和部分 QC/合成基础设施。
- Settings → Agents 已能区分平台内置 Skill 与用户自定义 Skill；MCP 默认关闭。
- 相关前端构建、后端 Skill/AgentScope 测试已经通过；真实 ComfyUI 视频推理仍需单独验收。
- 本目录中 RunningHub、Krea 2、角色资产和视频研究均已完成 Markdown 化；新增内容不应直接视为已实现代码。


# HTML / React / 后端关联清单

## 目的

把来源任务中提到的“页面、HTML、Skill、MCP、工作流和 API”对应到当前本地代码，方便其他 AI 不靠猜路径接手。

本项目是 Next.js 前端 + Java/Spring 后端 + AgentScope/ComfyUI 相关能力。页面源码主要是 TSX/React，不是静态 HTML 文件；浏览器最终 HTML 由 Next.js 渲染。

当前仓库扫描未发现独立的 `.html`/`.htm` 页面文件；HTML 入口由 `E:/Projects/RongGuang/ai-fusion-video-web/app/layout.tsx`、`app/global-error.tsx` 和各路由 `page.tsx` 生成。因此来源任务里提到的 HTML 页面，应按“路由 + TSX 页面 + React 组件 + API”关联，不要凭空创建一套静态 HTML。

## 主要页面与源码

| 产品入口 | 页面源码/关联组件 | 当前定位 |
|---|---|---|
| `/generate` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/page.tsx` | 统一创作工作台入口。 |
| `/generate/video` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/video/page.tsx` | 统一视频生成入口；默认底座由配置/能力路由决定。 |
| `/generate/videos` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/videos/page.tsx` | 高级/单模型视频能力入口，不是普通用户主路径。 |
| `/generate/images` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/images/page.tsx` | 图像工坊入口；能力可标记为已验证、待接入或候选。 |
| `/generate/image` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/image/page.tsx` | 单次图像生成编辑器。 |
| `/generate/universal` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/generate/universal/page.tsx` | 万能导演/统一生成方向的页面承载。 |
| `/production` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/production/page.tsx` | 生产任务、进度、恢复和质检的 UI 入口。 |
| `/settings/agents` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/settings/agents/page.tsx` | Skill/MCP 管理与平台内置能力展示。 |
| Agents Skill 区域 | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/settings/agents/_components/skills-section.tsx` | 区分平台内置 Skill 和用户自定义 Skill。 |
| `/settings/ai-models` | `E:/Projects/RongGuang/ai-fusion-video-web/app/(dashboard)/settings/ai-models/page.tsx` | 模型、Provider、ComfyUI 工作流和能力管理。 |

HTML/文件输入关联：

| 能力 | 源码 | 说明 |
|---|---|---|
| HTML/剧本文件上传 | `E:/Projects/RongGuang/ai-fusion-video-web/components/dashboard/parse-script-dialog.tsx` | 当前 UI 文案明确支持 TXT、Markdown、CSV、JSON、XML、HTML、RTF、DOCX、PDF；上传后进入脚本/剧本解析链。 |
| 全局 HTML 壳 | `E:/Projects/RongGuang/ai-fusion-video-web/app/layout.tsx` | Next.js 根布局、全局样式和 HTML 文档壳。 |
| 错误页 HTML 壳 | `E:/Projects/RongGuang/ai-fusion-video-web/app/global-error.tsx` | 全局错误场景下的最小 HTML 文档。 |
| Markdown 流式展示 | `E:/Projects/RongGuang/ai-fusion-video-web/components/dashboard/stream-markdown.tsx` | AI 输出/研究内容的 Markdown 渲染，不等于静态知识文件。 |

路径中的 `(dashboard)` 是 Next.js 路由组，不会出现在浏览器 URL 中。

## 前端数据与生成链

```text
页面 TSX
  ↓
components/dashboard/generation-workbench.tsx
  ↓
lib/api/generation.ts / production.ts / asset.ts
  ↓
后端任务、资产、工作流、模型能力接口
  ↓
ComfyUI / Provider / 任务历史
```

相关前端文件：

- `E:/Projects/RongGuang/ai-fusion-video-web/components/dashboard/generation-workbench.tsx`
- `E:/Projects/RongGuang/ai-fusion-video-web/components/dashboard/generation/generation-simple-composer.tsx`
- `E:/Projects/RongGuang/ai-fusion-video-web/components/dashboard/generation/generation-advanced-panel.tsx`
- `E:/Projects/RongGuang/ai-fusion-video-web/lib/generation-capabilities.ts`
- `E:/Projects/RongGuang/ai-fusion-video-web/lib/api/comfyui-workflow.ts`
- `E:/Projects/RongGuang/ai-fusion-video-web/lib/api/ai-assistant.ts`

## 平台内置 Skills

```text
E:/Projects/RongGuang/ai-fusion-video/src/main/resources/agentscope/skills/
├── universal-director/SKILL.md
├── character-scene-bible/SKILL.md
├── storyboard-prompt-compiler/SKILL.md
├── reference-consistency/SKILL.md
├── video-qc-repair/SKILL.md
└── README.md
```

它们是平台内置的决策与交接规则，不等于 ComfyUI 工作流，也不等于 MCP Server。Skill 通过项目上下文、资产、镜头和能力接口参与决策；实际生成仍由现有任务/工作流执行。

## MCP 与 ComfyUI 的关系

当前状态：

```text
AgentScope MCP initialized
enabled = false
servers = 0
tools = 0
```

平台已经有直接 ComfyUI/工作流调用能力，所以当前不需要把 ComfyUI 再包成通用 MCP。未来只有以下情况才考虑受控 MCP：

- 把已批准的 WorkflowProfile 映射成受限工具；
- 只允许提交、轮询、取消、回收已登记工作流；
- 有权限、审计、超时、幂等键和输出范围；
- 默认关闭任意 URL、任意文件读写和任意命令执行。

## 已知本地验证与边界

之前已完成并记录过：

- 前端 lint：0 errors，保留少量既有 warnings；
- 前端 production build：通过；
- 后端 AgentScope Skill/运行服务测试：通过；
- Docker 启动日志：Skill registry 初始化成功，MCP 默认关闭；
- 真实模型视频推理：尚未由本目录文件自动证明。

因此页面“能显示”只代表 UI/注册链存在；工作流“已登记”只代表 Registry 数据存在；两者都不能替代真实生成验收。

## 来源中的 HTML/代码内容如何归档

- 来源任务里出现的 Markdown 代码块、TS/TSX、JSON、YAML、SQL 和提示词，已在本目录的归一化文档中按主题保存。
- 来源任务没有可直接复制到仓库的附件列表；用户截图只作为页面/任务名参考。
- 如果后续拿到真实 HTML 文件、ComfyUI API JSON 或截图导出文件，应在本目录增加明确的 `assets/` 或 `workflow-sources/` 子目录，并记录 SHA-256、来源、版本和验证状态。
- 目前“HTML”在本地有两种含义：一是 React/Next 页面最终渲染的 HTML，二是脚本解析器允许用户上传的 HTML 文档；两者都已在上面的路径表中关联。

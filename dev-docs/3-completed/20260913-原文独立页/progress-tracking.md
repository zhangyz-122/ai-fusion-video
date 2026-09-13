# 进度

## 2026-09-13

- 后端行为确认：`replaceSourceAndReset` 保存原文并清空分集/场次；`ScriptAutoSplitService` 解析前自行清空旧产物；`story_to_script` 主 Agent 只新增分集不清空（依据 `script-story-to-script.system.md` 工作流）。据此设计前端重置确认逻辑。
- 新增 `lib/file-text.ts`：从原 `ParseScriptDialog` 抽取文件文本抽取工具与格式常量，纯文本扩展与 html/rtf/docx/pdf 分支保持原行为。
- 新增原文页 `/projects/[id]/source`：
  - `source-text-card`：空态为虚线上传入口，已导入态展示字数、更新时间与前 2000 字预览；
  - `source-import-dialog`：文件导入 + 粘贴，仅保存原文不触发解析；已有剧集时保存需确认"清空剧集"；
  - `story-to-script-panel`：文本模型选择 + 创作集数（1-50），超 3 万字自动切换自动分块解析并轮询进度；已有剧集时重新转换先确认并以当前原文重置；`onComplete` 校验分集/场次落库；保留"按剧本结构解析"备选入口。
- 侧边栏项目详情菜单新增「原文」（ScrollText，emerald），位于「剧本」之前。
- 剧本页移除顶部「故事转剧本」粘性条及相关 imports，回归剧集/场次管理。
- 概览页移除 `ParseScriptDialog` 与 `handleAiScriptCreated`，总剧本卡片按钮改为「查看剧本 / 管理原文」或「导入原文 / 查看剧本」，均路由跳转。
- 删除 `story-to-script-button.tsx`、`parse-script-dialog.tsx`，全局无残留引用。

## 验证结果

- `tsc --noEmit` 通过（首次运行暴露 `source/page.tsx` 的 `overview.script` 可空问题，已修复；`production-take-drawer.tsx` 的 TS2304 为增量缓存残留，复跑消失）。
- `next build` 生产构建通过，路由表含 `/projects/[id]/source`。
- 浏览器冒烟未执行：本地 8080 后端存活但前端未运行、无可用测试账号凭据；未宣称页面交互验收通过。

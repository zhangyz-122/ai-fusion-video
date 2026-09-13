# 完成总结：原文独立页

## 最终范围

TXT 小说原文获得独立管理入口：新增 `/projects/[id]/source`「原文」页（侧边栏位于「剧本」之前），只负责原文的导入/编辑/预览与转剧本发起；剧本页回归纯剧集/场次管理；项目概览页移除"上传即解析"的混合弹窗，改为跳转引导。

## 关键实现

- **原文页**（`app/(dashboard)/projects/[id]/source/`）：
  - 空态为虚线上传入口；已导入态展示字数、更新时间与前 2000 字预览（`source-text-card.tsx`）。
  - 导入弹窗支持文件（TXT/MD/CSV/JSON/XML/HTML/RTF/DOCX/PDF）与粘贴，仅保存原文、不触发任何解析；已有剧集时保存需确认清空（`source-import-dialog.tsx`）。
  - 转剧本面板：文本模型可选、创作集数 1-50；超 3 万字自动切换后端章节感知分块解析并轮询进度；保留「按剧本结构解析」（`script_full_parse`）备选（`story-to-script-panel.tsx`）。
- **重复转换防护**：`story_to_script` 只新增不清空，已有剧集时重新转换先经确认、以当前原文调用 `replaceSource` 重置后再启动，避免重复分集。
- **完成校验**：pipeline DONE 不作为成功依据，`onComplete` 校验分集与场次确已落库，否则提示更换支持工具调用的模型（对齐截图报错场景的自愈路径）。
- **共享工具**：`lib/file-text.ts` 承接原 `ParseScriptDialog` 的文件文本抽取逻辑；删除 `parse-script-dialog.tsx` 与 `story-to-script-button.tsx`，无残留引用。

## 验证结果

- `tsc --noEmit` 通过；`next build` 生产构建通过，路由表含 `/projects/[id]/source`。
- 浏览器端到端冒烟未执行（本地无运行中的前端与测试凭据），已记入 technical-debt.md 待补。

## 遗留事项

见 `technical-debt.md`：原文保存即重置的后端语义、分集校验读取放大、大文本编辑性能、浏览器验收欠账。

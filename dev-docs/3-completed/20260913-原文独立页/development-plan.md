# 开发计划：原文独立页

## 背景

新增「故事转剧本」能力后，原有流程仍把 TXT 小说原文的录入混在两处：

- 项目概览页「AI 解析」弹窗：上传原文与触发 `script_full_parse` 解析绑定在一次操作里，上传即解析，解析报错后用户被迫再到剧本页找「故事转剧本」补救；
- 剧本页顶部常驻「故事转剧本」按钮：与剧集/场次管理混在一起，职责不清。

## 目标

- 新增独立的「原文」页 `/projects/[id]/source`：只负责原文的导入、编辑、预览，不承担任何解析职责。
- 转剧本入口收敛到原文页：短文本走 `story_to_script`，超长文本自动切换后端章节感知分块解析，另保留「按剧本结构解析」（`script_full_parse`）作为原文已是标准剧本格式时的备选。
- 剧本页回归纯剧集/场次管理，移除顶部「故事转剧本」按钮。
- 概览页「总剧本」卡片改为引导跳转：未导入原文时主按钮为「导入原文」，已导入时提供「管理原文」次操作；移除「AI 解析」混合弹窗。

## 关键决策

- 后端不加接口：原文保存复用 `PUT /api/script/{id}/source`（`replaceSourceAndReset`，保存即重置剧集/场次），转换复用既有 pipeline agent。
- `story_to_script` 只新增分集不清空旧数据、`auto-split` 会先清空：前端在已有剧集时重新转换，先经确认后调用 `replaceSource` 以当前原文重置，避免产生重复分集。
- 转换完成的 DONE 事件不作为成功依据：沿用仓库既有模式，在 `onComplete` 中校验分集与场次确已落库，否则报错提示更换支持工具调用的模型。
- 文件读取逻辑（TXT/HTML/RTF/DOCX/PDF 抽取）从已删除的 `ParseScriptDialog` 抽取为共享工具 `lib/file-text.ts`。

## 范围

- 新增：`source/` 路由（layout、page、`source-text-card`、`source-import-dialog`、`story-to-script-panel`）、`lib/file-text.ts`。
- 修改：`sidebar-nav.tsx`（新增「原文」导航）、`scripts/page.tsx`（移除转剧本入口）、`projects/[id]/page.tsx`（移除解析弹窗、改为跳转引导）。
- 删除：`story-to-script-button.tsx`、`parse-script-dialog.tsx`。

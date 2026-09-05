# ADR-001: 基线冻结与上游同步策略

## 状态

Accepted

## 背景

融光（ai-fusion-video）是一个成熟的 AI 视频生成平台（Spring Boot 3.5 / Java 21 / Next.js / MySQL / ComfyUI / Redis）。
我们要在其上叠加 Production Layer（Run / Step / Take / QC / WorkflowProfile / Story State / Director），
而不重写任何现有子系统。

核心风险：如果 Production 代码与 upstream main 直接混在同一分支，upstream 更新可能破坏 Production 功能，
或者 Production 代码阻碍 upstream 升级。

## 决策

1. **基线冻结**：以 upstream SHA `f2bea0faa889fe4545f89b4c762fca03c4871503` 为不可变研究基线。
2. **开发分支**：所有 Production 代码在 `feat/ai-drama-os-*` 分支上开发，不直接推 upstream main。
3. **upstream 同步**：手动 `git pull upstream main`，在 merge 前运行全量测试。
4. **文件隔离**：Production 新增文件放在独立目录（`production/`），不修改 upstream 既有文件（StoryboardItem 字段新增除外）。
5. **StoryboardItem 字段策略**：nullable 新增字段 + default-safe，upstream merge 时 review。
6. **Docker / 配置**：Production 环境使用独立 docker-compose 配置文件，不修改 upstream docker-compose.yml。

## 后果

- 正面：upstream 升级不受 Production 代码影响；Production 团队可以独立迭代。
- 负面：upstream 变更 StoryboardItem 时需要额外 review；本地与 upstream 的 git 历史会逐渐分叉。
- 缓解：每次 upstream pull 后运行全量测试；StoryboardItem 变更记录在 BASELINE.md。

## 验收

- [x] 基线 SHA 确认（f2bea0fa）
- [x] 原版功能可运行（项目/剧本/分镜/素材/图片视频生成/合成）
- [x] 分支 `feat/ai-drama-os-pr001-baseline` 已创建
- [x] 本文档已提交

# 融光开发交接入口

正式开发目录：`E:\Projects\RongGuang`。所有后续开发在这里进行。

先读 `AGENTS.md`，然后阅读 `dev-docs/2-in-progress/20260913-platform-handoff/` 的计划、进度和技术债。

- 后端：`ai-fusion-video/`
- 前端：`ai-fusion-video-web/`
- 全平台目标与任务总账：`dev-docs/1-todo/2026-09-13-自有综合创作平台开发总任务.md`（融光作为底座，功能扩展及页面重做；不是完成报告）
- 页面整改与短剧产线 IA（B01 草案）：`dev-docs/1-todo/2026-09-13-页面整改与短剧制造平台IA.md`
- 外部优点吸收台账（J006）：`dev-docs/1-todo/2026-09-13-外部优点吸收台账.md`
- 近期执行计划：`dev-docs/2-in-progress/20260913-platform-handoff/development-plan.md`
- 当前进度：`dev-docs/2-in-progress/20260913-platform-handoff/progress-tracking.md`
- 历史 Production 计划：`ai-fusion-video/dev-docs/2-in-progress/20260913-production-run-take-selection/`
- 历史审计及 PR-001～030：`evidence/2026-09-13/audit/`
- 工作流、运行记录及 Golden fixture：`evidence/2026-09-13/`

历史审计有过度标记的 VERIFIED，不能替代当前代码/API验收。注册成功不等于前端接入完成。

从本目录运行 `docker compose ps`、`docker compose up -d`。Compose 名称固定为 `ai-fusion-video`，继续使用现有数据卷。不得执行 `down -v` 清空项目数据。

页面：http://localhost:8081。ComfyUI：http://127.0.0.1:8188，仍在 `C:\AI-T8-video-onekey`。数据库、媒体、Redis 和 Agent 工作区仍在 Docker 命名卷中，本次仅迁移代码和开发资料，未搬迁 Docker 数据盘或模型。

`.env` 已保留，仅用于本机，不能提交密钥。Git 历史和未提交修改均已保留；不要重置工作树。

旧 C 盘项目保留为迁移备份，不再作为开发入口。历史证据中的绝对路径保留其采集时含义。

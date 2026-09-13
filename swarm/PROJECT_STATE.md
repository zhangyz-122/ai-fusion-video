# PROJECT STATE(自治研发组织状态文件)
更新:2026-09-14。Supervisor:主会话。当前分支基点:sprint/universal-director-integration。

## 系统当前能力(已验证可用)
- 六条创作路径端到端:生图/参考生图/图生视频(三候选+QC+选片)/自动分块(170集实测)/合成成片/字幕导出SRT
- 生产中心(运行列表+详情+重试+重同步)、仪表盘活动、能力目录、资产回收站、系统状态页(健康/存储/队列)
- 权限隔离(uitest vs zhangyz 双账号实测)、INFINITETALK v22(内置音频)、滞留任务回收(2h)、运行自动同步(30min)

## 活跃工作流
- 5 Agent 并行:swarm/a1-architect、a2-dev-backend、a3-dev-frontend、a4-qa、a5-redteam
- 每完成一个任务:Supervisor 审查→合并→回归→派发下一 BACKLOG 任务

## 已知红线
- 多 worktree 禁用 git stash(T2/T9 事故)
- 禁止数据库迁移(本冲刺);禁止 docker push;部署仅 Supervisor 执行
- MySQL LENGTH() 是字节;mysql CLI 输出反斜杠翻倍——JSON 分析用 HEX()

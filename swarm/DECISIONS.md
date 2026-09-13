# ADR 决策记录
ADR-001 多worktree并发:文件边界互斥+每任务独立分支;禁用 git stash(共享refs/stash事故T2/T9)。
ADR-002 本冲刺禁止数据库迁移;新数据用 JSON 字段。
ADR-003 长文本解析:确定性代码分块(≤6000字/块)+逐块AI转写,不依赖模型上下文。
ADR-004 并发选片语义:最后写入者胜,单真相字段 selectedTakeId。
ADR-005 部署:镜像由 Supervisor 从集成分支构建,容器在主工作树(有.env)重建。

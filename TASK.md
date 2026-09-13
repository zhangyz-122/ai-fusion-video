# 冲刺任务 T13:通知面板:taskStream 任务实时进度强化

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
通知面板对 category=task 的任务(自动分块解析/合成等)展示实时进度:
1. 对运行中任务接 /api/ai/chat/{taskId}/reconnect SSE 流(既有端点),实时渲染 publishContent 的进度文本(如'正在解析分块 37/170')。
2. 任务完成后显示完成摘要与产物链接(如有)。
3. 面板关闭后轮询兜底刷新列表状态。
## 允许文件
- ai-fusion-video-web/components/dashboard/notification-panel/**(本目录内自由)
- ai-fusion-video-web/lib/api/task-stream.ts(仅追加)
## 禁区
- 不改后端;不改 pipeline-store 既有语义。
## 验收
- tsc/eslint 通过;真实任务(如正在运行的 script 5 自动分块)进度实时可见。

## 通用规则
1. 只允许改动允许文件清单内文件;需要例外先在 TASK.md 末尾声明并继续可做部分。
2. 不改数据库迁移;不改导航与公共组件。
3. 提交规范:conventional commits。
4. 完成后确认编译/验证通过,报告分支名与变更清单,由集成者合并。
5. 需要决策的问题追加到 TASK.md 末尾,不要空等。

----

## 决策与执行记录(T13 子代理追加,2026-09-13)

### 实现范围与数据流决策
1. 实时任务流展示为通知面板内的独立区块(新增 `task-stream-section.tsx`),不写入
   pipeline-store 的 tasks 列表:避免改变 pipeline-store 既有语义(禁区),也避免
   attachTaskStream 那套"终态才结算、内容按 paragraph 追加"的 Pipeline 时间线模型
   覆盖任务流"每条 CONTENT 都是全量进度文本"的语义。任务流卡片自己管理 SSE 订阅、
   轮询回退与终态渲染,与 store 内任务通过 conversationId 去重(分镜页合成的
   attachTaskStream 任务仍走原路径,不会双份展示/SSE)。
2. 数据源为既有 `/api/task-stream/running`(前端此前无人调用),仅取 `category === "task"`。
   TASK.md 写的端点 `/api/ai/chat/{taskId}/reconnect` 实际不存在,SSE 复用
   `lib/api/task-stream.ts` 已封装的 `/api/task-stream/reconnect?taskId=`(仅追加类型导出,
   未改既有代码)。
3. SSE 断开/流未带终态即结束时:先探测 `/api/task-stream/status`,ACTIVE 且重订未超
   2 次则重订(重订会从 0-0 全量回放,幂等),否则回退 5s 状态轮询;轮询期间用
   `listMessages` 最后一条 assistant 消息回填进度文本,终态(COMPLETED/ERROR)用同一条
   消息补齐完成摘要。
4. 面板关闭即组件卸载(SSE/定时器全部清理);再次打开全量重拉 running 列表 + 探针,
   并在面板打开期间每 15s 兜底刷新列表,覆盖关闭期间错过的开始/结束状态。
5. 完成摘要复用本目录 `parseTaskContent` 抽取"视频地址/下载地址"产物链接(合成任务),
   并按 `contextType` 附加剧本/分镜入口链接(script→/projects/{id}/scripts、
   storyboard_episode→/projects/{id}/storyboards)。

### 需要决策/集成者知悉的问题
1. 【僵尸运行中会话】实测发现 DB `status=running` 但 Redis 状态为 NONE 的会话
   (进程中断留下的记录,如脚本 5 的两个历史解析会话)。面板对这类记录探测后直接隐藏,
   不展示为运行中任务;它们也永远进不了历史列表(历史过滤 running)。是否需要后台
   兜底把这类会话标记为 failed,属后端范围,留档待决策。
2. 【终态摘要的生命周期】任务流会话无消息时间线面板,终态摘要只保留在当前面板会话内;
   面板关闭重开后,该任务从"实时任务/任务结果"消失,由历史列表(仅标题/状态)承载。
   若需要在历史详情里也展示任务流完整进度记录,需要任务中心详情面板支持
   category=task 会话,属后续增强。
3. 【轮询频率】断线回退轮询 5s/次(状态+消息两条请求/任务),面板列表兜底刷新
   15s/次;运行中任务通常 1-2 个,负载可控。如需更实时的断线恢复可改为"重订优先、
   轮询仅兜底",当前实现已含 2 次重订。
4. 【实测方式说明】子代理不可用浏览器 GUI 工具(仅限主代理),故面板内交互未做
   截图级验证;实测通过 dev server(3001,代理 8081)用与组件一致的数据路径完成:
   登录→running 列表过滤→状态探针→SSE 订阅→进度采样→断流→轮询→终态,并确认
   /dashboard 路由编译渲染 200。脚本 5 的 170 块解析任务在实现期间自然完成,其
   COMPLETED 状态与完成摘要("自动分块解析完成,共 170 集")被直接采样验证;另建了
   一次性项目(6 块小剧本)完整实测了运行中→完成与纯轮询两条路径,测试数据已清理。

### 验证结果
- `corepack pnpm exec tsc --noEmit`:0 错误。
- `corepack pnpm exec eslint`(6 个改动文件):0 问题。
- 实测(dev server 3001 → 平台 8081,zhangyz):
  - 阶段1 发现:running 列表过滤 category=task,3 条中 2 条僵尸(NONE)被过滤、
    1 条 ACTIVE 建立实时流 ✓
  - 阶段2 实时:SSE 进度文本 "进度：1/6" → … → "进度：6/6" 持续更新 ✓
  - 阶段3 轮询回退:全程无 SSE,仅轮询推进 "进度：1/6 → 2/6 → 5/6",终态 COMPLETED ✓
  - 阶段4 完成:DONE 摘要 "自动分块解析完成，共 6 集" ✓;脚本 5 大任务摘要
    "自动分块解析完成，共 170 集" 同样采样验证 ✓
  - /dashboard 经 dev server 编译渲染 200,无编译错误。

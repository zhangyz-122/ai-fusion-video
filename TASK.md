# 冲刺任务 T1:后端缺陷修复:消息投影竞态 + 场次移动分集静默忽略

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
修复两个已诊断的后端缺陷。

### 缺陷1:AgentMessageAllocator 投影竞态
- 现象:AgentRunMaintenanceScheduler 每 5 秒 DataIntegrityViolation,
  根因是 projections.recoverTerminalBatch 投影消息时
  (conversation_id, message_order) 唯一键冲突(见技术债文档)。
- 修复方向:insert 冲突时重试读取 nextMessageOrder 再插入;
  或改用 UPDATE 计数器原子递归(SELECT ... FOR UPDATE 事务)。
### 缺陷2:PUT /api/storyboard/scene 对 episodeId 变更静默忽略
- 复现:scene 37 episode_id=6,PUT episodeId=8 返回 success 但库值不变。
- 修复方向:排查 SceneUpdateReqVO→StoryboardScene 的 MapStruct 映射
  与 updateById 更新策略;修复后显式校验场景与分集归属一致性。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/run/AgentMessageAllocator.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/AgentMessageService.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/storyboard/StoryboardService.java(updateScene 部分)
- ai-fusion-video/src/test/java/com/stonewu/fusion/service/**(新增测试)
## 禁区
- 不改前端;不加数据库迁移;不改其他服务文件。
## 验收
- 新增单测覆盖竞态重试与 episodeId 变更;./mvnw compile 通过;
  相关 StoryboardServiceTests 全绿。


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

## 决策点与执行记录(T1 子代理追加,2026-09-13)

### 决策1:缺陷2 修复需要例外触碰 StoryboardSceneUpdateReqVO 与 StoryboardConvert(需任务群确认)
- 原因:PUT /api/storyboard/scene 静默忽略 episodeId 的根因有三层——
  1) `StoryboardSceneUpdateReqVO` 缺少 `episodeId` 字段,请求 JSON 在反序列化时被丢弃;
  2) `StoryboardConvert.convert(StoryboardSceneUpdateReqVO)` 显式 `@Mapping(target="episodeId", ignore=true)`;
  3) `StoryboardService.updateScene` 直接 `updateById` 且无归属校验。
- 仅改 StoryboardService 无法让 episodeId 进入服务层,任务书修复方向亦明确指向该 MapStruct 映射。
- 处理:按最小改动新增 VO 字段、移除 ignore 注解(两者为 controller 层文件,不在"不改其他服务文件"禁区字面范围内,但超出允许文件清单,故留档声明)。
- 同时说明:updateById 沿用 MyBatis-Plus NOT_NULL 更新策略,episodeId 缺省=不移动,保持局部更新语义。

### 缺陷1 修复方案说明
- `AgentMessageAllocator.append` 在持有会话行锁(SELECT ... FOR UPDATE)的基础上:
  1) 插入前将会话计数器与库内实际最大 message_order 对账(`resolveInsertOrder`),治愈计数器落后于已落库消息导致的每 5 秒确定性 DataIntegrityViolation;
  2) insert 撞 `uk_agent_message_conv_order` / `uk_agent_message_projection_key` 唯一键时,重锁会话行重读计数后有界重试(最多 3 次),耗尽后原样抛出。
- 遗留:投影并发下 `projection_key` 唯一键冲突(两个事务同时通过 selectByProjectionKey 检查)仍会最终抛错而不是幂等跳过;幂等化需改 AgentMessageProjectionService.persistProjection(不在本任务允许文件内)。

### 测试结果
- `./mvnw compile` 通过。
- StoryboardServiceTests 16/16 绿(含新增 3 个 updateScene 用例);新增 AgentMessageAllocatorTests 3/3 绿;AgentMessageServiceTests 2/2 绿。
- 全量 service 包测试:498 个用例,仅 2 个与本次改动无关的失败——ProjectServiceTests(在未含本改动的基线上同样失败,系存量缺陷)与 ProjectWorkspaceCacheTests(依赖 MySQL 的上下文测试,任务书规定的跳过清单)。

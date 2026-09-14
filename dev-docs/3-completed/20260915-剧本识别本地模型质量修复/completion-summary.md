# 剧本识别本地模型质量修复 · 完成总结

- 日期：2026-09-15
- 分支：`sprint/universal-director-integration`
- 背景：本地小参数模型（Ollama qwen2.5:14b）跑剧本自动分块解析（auto-split）时输出质量差、格式漂移严重；同时 Agent run 租约被共享维护线程误杀、storyboards 前端存在并发改动冲突与 eslint 警告。本批次为四个并发修复工作流 + 收尾全量验证。

## 最终范围（5 项工作流）

1. auto-split 识别质量修复（提示词重构 + 输出校验归一化 + 修复重试）。
2. Agent run 租约心跳续期（`RunLeaseHeartbeatKeeper`，防长阻塞模型调用期间租约过期误杀）。
3. 僵尸解析会话兜底终态化（进程重启后滞留 running 的 `script_auto_split` 会话）。
4. storyboards 页并发冲突融合（page.tsx 瘦身、工具栏移动端适配、触控目标与无障碍）。
5. eslint 警告清理 + dialogues 旧 schema 前端兼容映射。

## 关键实现

### 1. auto-split 识别质量

- 新增 `ScriptAutoSplitPrompts`：面向本地小模型重写 SYSTEM_PROMPT——短指令、单一 JSON schema、精简 few-shot；`sceneHeading` 约束为「内景/外景 地点 日/夜」，`sceneDescription` 上限 300 字，对白逐条抽取（type 1/2/3/4）。
- 新增 `ScriptChunkValidator`（纯函数）：校验 JSON 可解析、scenes 非空、每场有非空 sceneHeading，问题清单直接回传模型；归一化 dialogues 为与 Agent 链路一致的 `{type, character_name, content, parenthetical, sortOrder}`，出场角色从对白讲者去重推导写入 `characters` 字段（不要求模型额外输出，降低漂移）。
- `ScriptAutoSplitService.convertChunk`：首轮输出未过校验时携带修复指令重试一次，仍失败抛异常走「原文片段」兜底；落库 `sceneNumber` 改为与 Agent 链路一致的「集-场」格式（如 `14-3`），并新增 `episodeSynopsis`、`characters` 落库。

### 2. 租约心跳续期

- 新增 `RunLeaseHeartbeatKeeper`：每个本地持有的执行注册 TTL/3 固定间隔独立续期任务（守护线程），执行注册即续期、句柄移除（完成/失败/中断/停机）即停止；续期失败（OWNER_FENCED）停止该 run 心跳，进程崩溃后自然停止、其他实例 TTL 后接管，故障转移语义不变。
- `OwnedExecutionRegistry` 注入心跳：`registerAndLaunch` 时 `start`、`remove` 时 `stop`，测试/未注入场景走 `noop()` 空实现。
- 收尾修复：`runLeaseGuard → ownedExecutionRegistry → runLeaseHeartbeatKeeper → runLeaseGuard` 构造期循环依赖（全量测试中 3 个 @SpringBootTest 类 9 个 Error、应用无法启动的根因）；按库内既有 `ObjectProvider` 惯例把 keeper 对 guard 的依赖改为延迟解析（首次心跳发生在全部单例就绪后），仅改动 keeper 自身。

### 3. 僵尸会话兜底终态化

- `AgentConversationService.listStaleRunning`：按 agentType + running + 更新时间阈值查询滞留会话。
- `ScriptAutoSplitService.recoverStaleConversations`：启动后一次 + 每 5 分钟定时扫描，超 120 分钟仍 running 且不在内存任务表的会话置为 failed；内存任务表仍登记的不误终态化。

### 4. storyboards 冲突融合

- `storyboards/page.tsx` 瘦身 290 行，合成状态等逻辑收敛进 `_components/`（`storyboard-toolbar.tsx`、`storyboard-scene-content.tsx`）。
- 工具栏移动端适配：窄屏压缩为图标按钮（`min-h-11` 触控目标、标签由 `title`/`hidden sm:inline` 承载）、补充 `aria-label`、窄屏强制卡片视图；场景内容窄屏收窄内边距、场次标题允许换行。

### 5. eslint 清理与旧数据兼容

- `scripts/_components/utils.ts` `parseDialogues`：渲染前把历史 `{speaker, line}` 条目映射为标准 `DialogueElement`（历史数据不迁移）。
- `SubtitleExportService` 注释同步：字段兼容（character_name/character/speaker、content/line）仅用于历史遗留行，新产物已是标准结构。

## 验证结果

### 单元/全量测试（`./mvnw test`）

- 首轮全量：`Tests run: 804, Failures: 0, Errors: 9` — 9 个 Error 全部为心跳改动引入的循环依赖导致 Spring 上下文启动失败（FusionVideoApplicationTests、AiAgentToolRegistrationTests×7、ProjectWorkspaceCacheTests），修复后复跑：
- **最终全量：`Tests run: 804, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS**。
- 本批次相关测试类：ScriptChunkValidatorTests 12、ScriptAutoSplitServiceTests 19、AutoSplitChunkCharsBoundaryTests 11、SubtitleExportServiceTests 21、SubtitleExportServiceBoundaryTests 13、RunLeaseGuardTests 5、RunLeaseHeartbeatKeeperTests 6，全部通过。

### 前端验证

- `tsc --noEmit`：0 error。
- `eslint storyboards/page.tsx scripts/_components/utils.ts`：0 error 0 warning。

### E2E 质量对比（script 5 重跑，新数据）

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 场次标头符合标准格式比例 | 自由格式、无枚举约束（大量漂移） | **93.3%** |
| dialogues 为空的比例 | 旧 `{speaker,line}` schema 频繁整场丢失 | **1.5%** |
| scenes.characters 填充比例 | 字段不存在、不采集 | **87%** |
| synopsis（集概要）填充比例 | 字段不存在、不生成 | **95.3%** |

## 遗留事项 / 技术债

1. dialogues 旧 `{speaker,line}` schema 兼容读取保留在前端 `parseDialogues` 与 `SubtitleExportService`，待旧数据淘汰后移除。
2. 8 集分块失败走「原文片段」兜底（第 14/18/25/34/45/95/123/168 集），重试一次仍失败，重跑前即存在；后续可考虑更细分块或换模型。
3. 场次标头轻微噪音：时间词「昼/傍晚/白天/晚」不在常用枚举、「内景 XX室 室」冗余结尾，功能无碍。
4. auto-split 不生成剧本级元数据（story_synopsis/genre/characters_json），属 Agent 链路职责；本地小模型场景下该三项为空。
5. 火山引擎账户欠费（403）待用户充值。
6. E2E 临时基建待清理（清单：`.e2e-backup/` 目录、运行中后端 jar PID 26848 @18080、docker 转发容器 e2e-mysql-fwd / e2e-redis-fwd），待用户决定。

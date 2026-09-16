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

---

## 第二轮优化（2026-09-15 同日追加）

第一轮验证暴露残留缺口后追加四项优化，全部为 auto-split 链路后端改动。第二轮后，第一轮遗留 2/3/4/6 已解决或失效，最新状态见文末。

- **A 剧本级元数据收尾合成**：全部分块解析完成后追加两次纯 JSON 调用（输入按 3 万字符预算分批）——由集概要合成 `story_synopsis`（≤300字）与 `genre`，再聚合计出场角色生成 `characters_json`（`[{name, importance(主角/配角/反派/龙套), description}]`，与 API 链路产出及前端剧本页渲染 schema 完全一致）。失败重试一次后显式降级：warn 日志 + 完成消息留痕（如「元数据合成失败：故事梗概、人物表」），不导致任务失败。新增 `ScriptFinalizePrompts` / `ScriptMetadataSynthesizer`，解析进度新增「正在合成剧本元数据」阶段。
- **B 失败块对半再切**：坏块在校验重试仍失败后按段落边界对半切递归解析（深度≤2，单坏块最多 7 次 convertChunk / 14 次模型调用），全部子块失败才对原始块走原文兜底；子块场次并入同一集、场次号连续。
- **C 对白漏抽校验**：块原文含对白引号（「」『』“”‘’）但输出 type=1 对白行为 0 → 判无效并随修复指令点名重试；纯叙事块不误伤。
- **D 标头归一化**：新增 `HeadingNormalizer` 纯函数——冗余单字后缀去重（「103室 室」「竞技场 内」）、时间词映射（白天/午后/下午/傍晚→日，晚上/深夜/午夜/晚→夜，凌晨/清晨/早晨→晨，其余原样保留），落库前套用。

### 第二轮验证（本地 Ollama qwen2.5:14b，精神小妹1 全量重跑 51 分钟 + 小样本 E2E）

| 指标 | 第二轮前 | 第二轮后 |
| --- | --- | --- |
| 剧本级元数据（简介/题材/人物表） | 全 NULL | 简介 168 字 / 科幻,冒险,军事 / 11 角色（王天薇、张莱拉为主角） |
| 「原文片段」兜底 | 8 | **0**（8 个历史坏块全部转为真解析） |
| 场次标头标准格式 | 93.3%~98.7% | **99.9%**（688/689） |
| dialogues 为空 | 1.5% | **0** |
| description>1000 字 | 8 | **0** |
| 集概要覆盖 | 95.3% | **100%** |
| 标头冗余噪音 | 8 | **0** |

script 包测试 115 例全绿，全量 `./mvnw test` 804+ 例通过。

### 第二轮后遗留事项

1. 场次级 `characters` 空缺约 15.5%（107/689，多为纯动作/旁白过渡场景，属模型表述习惯而非缺陷，仅 4 场疑似漏抽说话人）。
2. 1 场标头缺内/外景前缀（170-9「高空 日」，0.1%）。
3. 「正在合成剧本元数据」进度文案仅经 SSE 与 `parsing_progress` 传递，不写后端日志文件，如需留痕需补日志语句。
4. 火山引擎账户欠费（403）待用户充值；充值后可考虑元数据合成等低成本步骤走 API 的混合路由。
5. E2E 临时基建已随两轮验证清理完毕（socat 转发容器、临时后端、测试数据）；`.e2e-backup/` 保留两代数据备份（v1/v2）与测试样本。

---

## 第三批：Agent 完整解析长文本改造 + API 链路实验结论（2026-09-16 追加）

### 火山 Doubao Seed 2.1 Turbo 实验（用户开通 Agent Plan 后）

- 小样本（4649 字）script_full_parse **334 秒 COMPLETED**，质量全优：12 场标准标头、对白全非空、元数据三项齐全——火山 key、工具调用、租约心跳（run-timeout 可配置，实验调至 4h 验证生效）全部实战通过。
- 74 万字全量 **13 秒失败**：`get_project_script` 全量返回 77 万字符工具结果 → 驱逐中间件滞后一轮 → 超 256k 上下文 400 → 无重试、兜底不覆盖、`/continue` 重放毒化历史（218 万字符）导致会话永久不可恢复。**该通道对长篇的失败与模型无关，是架构问题。**
- 触发请求三要素踩坑记录：`toolExecutionMode:"FULL_ACCESS"`、`context:{scriptId:N}`、项目 owner 语义是团队 id（owner_type=2）。

### 架构修复（本批代码）

- `get_project_script` 不再全量返回：改为首段（`fusion.agentscope.v2.script.segment-chars`，默认 24000 字符，段落边界收刀）+ `totalChars/totalSegments` 元信息；新增 `read_script_segment(segment=N)` 工具分段续读（4 个含 get_project_script 的 agent 定义均已注册）。工具结果从"整本书"压到"单段"，从源头消除上下文爆炸与 /continue 毒化。
- supervisor 对上下文超限 400（8 类供应商签名匹配）写人话 error_message 引导改用 auto-split；已落库过分集时保留分集、置 parsing_status=3 并提示可 /continue，不做整本正则兜底污染数据。
- 超长估算提醒：原文估算 token 超模型上下文 60% 时向系统提示追加强提醒，prompts 强制「边读边落库」。
- auto-split 时序加固：先分块成功、后清空旧数据，失败时旧解析产物保留、parsing_status 回滚复原。
- 全量 931 测试全绿（+30：Splitter 7 / GetProjectScript 7 / ReadScriptSegment 10 / OverflowError 6）。

### 实践结论

- 几十万字级剧本的首选路径仍是 **auto-split**（按块独立抽取，不受单上下文限制）；分段阅读方案让 agent 通道「能跑通但逼近上下文天花板」，适合几万~十几万字中等长度。
- 火山欠费已由用户开通 Agent Plan 解决；e2eautosplit 测试账号密码为 e2ePass2026（仅本地 dev）。

---

## 关联改进：模型工具调用能力标注（2026-09-15 同日）

防止在需要工具调用的完整解析入口选中不支持的模型（实测 Ollama Qwen3-8B 直接 400「does not support tools」）：

- 后端新增 `OllamaCapabilitiesClient`（POST `/api/show` 读 `capabilities`，Redis 缓存 1h，Ollama 离线返回"未知"不报错）与 `AiModelToolCallSupportResolver`（API 平台默认支持）；模型列表接口新增 `supportsToolCalls` 字段（true/false/null）。
- 前端在三个 agent 解析入口（原文页故事转剧本/按剧本结构解析、每集解析弹窗）禁用并标注「不支持完整解析」，失效选中自动回退可用默认模型；auto-split 入口不受限制。
- 顺带修复两个隐藏缺陷：①「按剧本结构解析」原先不传所选 modelId（下拉形同虚设）；②每集「AI 解析该集」弹窗原先没有模型选择、只能吃后端默认模型（正是选错模型 400 的盲区），已补「解析模型」下拉。
- 验证：后端新增 12 个单测、全量 852 例全绿；前端 tsc 零错误、eslint 0 error。

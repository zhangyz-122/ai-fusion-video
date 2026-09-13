# RunningHub 工作流研究：生产编排架构

日期：2026-09-13

## 说明

本文整理关联任务《RunningHub工作流研究》中形成的生产系统设计。关联任务中的“已完成”和“已测试”属于原任务的工作记录；迁移到正式平台前仍需以本机 ComfyUI、实际工作流和正式数据库回归为准。

## 定位

这条路线补的是万能导演台的“生产编排层”，不是另一个前端，也不是 MCP。它负责把整本小说或剧本转成可持续执行、可增量更新、可断点恢复的生产任务。

## 总链路

```text
整本小说 / 剧本 TXT
        ↓
Book Compiler
        ↓
Chapter Hash / Incremental Recompile
        ↓
Entity Resolver
        ↓
Story Bible
        ↓
Script / Beat IR
        ↓
Semantic Resolver
        ↓
Narrative Events + Physical Events
        ↓
Knowledge / Open Loops / World State
        ↓
Shot Graph
        ↓
Assets / Locks / Director Prompt
        ↓
Production Build DAG
        ↓
Storyboard
        ↓
Video Render
        ↓
QC
        ↓
Post
        ↓
Chapter Master
        ↓
Book Master
        ↓
Export
```

## 生产目标等级

同一个 Build 可以逐步升级，不必每次从小说重新开始：

```text
IR_ONLY
    ↓
STORYBOARD_MASTER
    ↓
VIDEO_DRAFT
    ↓
VIDEO_FINAL
    ↓
MASTER
```

- `IR_ONLY`：只编译故事、场次、节拍、资产和镜头数据。
- `STORYBOARD_MASTER`：完成分镜和关键帧交付。
- `VIDEO_DRAFT`：完成视频候选生成。
- `VIDEO_FINAL`：完成 QC、选片和可交付镜头。
- `MASTER`：完成配音、音效、BGM、字幕、时间线和整集导出。

## 任务状态

生产队列需要区分“还没执行”和“当前不能执行”：

```text
PENDING
READY
RUNNING

WAITING_WORKFLOW
WAITING_ASSET
WAITING_MANUAL_QC

SUCCEEDED
FAILED
BLOCKED
PAUSED
CANCELLED
```

示例：

- H3 工作流还没登记：`WAITING_WORKFLOW`，记录 `required_profile`。
- 角色脸图、服装、声音未准备：`WAITING_ASSET`。
- QC 工作流没有结构化结果：`WAITING_MANUAL_QC`，等待导演台人工 PASS/Reject。

## 持久化 Worker

生产不能依赖浏览器页面一直打开。Worker 需要把状态、日志和中间产物持久化到生产工作区：

```text
production/
  builds/
  journals/
  checkpoints/
  workflow_profiles/
  artifacts/
  exports/
```

电脑中断后，Worker 重启时执行恢复：

```text
RUNNING
  ↓ recovery
READY
  ↓
继续执行
```

恢复必须保留已成功的镜头，不得整章或整本重新生成。

## Workflow Registry

工作流不直接硬编码进小说或镜头文本，而是登记为可替换的 Executor/Profile：

```text
WF002  → STORYBOARD
WF009  → Wan FLF
DS006  → H3 Director
WF011  → LongCat
QWEN3VL_QC → QC
WF012  → Master Post
```

每个 Profile 至少保存：

```text
Workflow API JSON
Prompt Binding
Seed Binding
Asset Binding
Required Slots
Result Parser
Revision
SHA-256
```

生产 DAG 只依赖 `Executor ID`。更换 H3 工作流时更新 Profile revision，不改动 Novel IR、Beat、Event、Story Bible 和 Shot Graph。

## 增量编译

每章保存两种指纹：

```text
text_sha256
```

表示章节文字是否改变。

```text
export_fingerprint
```

表示这次改变是否影响后续剧情、世界状态或生产结果。

### 不传播的修改

例如只把：

```text
林雾夏：别动。
```

改成：

```text
林雾夏轻声说：别动。
```

如果 Physical Events 和 Narrative Events 不变，则只重编当前章，后续章节继续复用。

### 会传播的修改

例如把：

```text
林雾夏站在门口。
```

改成：

```text
林雾夏拔出玄铁剑。
```

新增 `DRAW_PROP`、道具状态 `drawn` 等语义后，需要沿依赖图使相关章节和生产 Job 失效。

依赖类型包括：

```text
WORLD_STATE_CHAIN
NARRATIVE_STATE_CHAIN
ENTITY_STATE
FACT_LOOP
```

## Manifest、Journal、Checkpoint、Export

每次构建需要留下：

- Build Manifest：目标等级、输入指纹、Profile revision、产物清单。
- Job Journal：每个 Job 的开始、结束、错误、重试和外部任务 ID。
- Checkpoint：可恢复的阶段结果和输入快照。
- Export Manifest：Chapter Master / Book Master 的文件、镜头、音轨、字幕和版本信息。

最终 ZIP 不能只是几十个无来源的 MP4，应至少能回溯：

```text
Build Manifest
Book Manifest
Story Bible
Checkpoints
Journal
Production Report
Artifacts / Media
```

## 与正式平台的对应关系

正式平台目前已经具备：

- 分集、场次、分镜数据模型。
- ProductionRun、ProductionStep、ProductionTake、QC 和视频合成。
- ComfyUI Workflow、WorkflowProfile、输入绑定、输出解析和工作流哈希。
- 平台任务队列、AgentScope 状态和工作空间存储。

还需要重点补齐：

1. Book/Chapter 级编译和章节依赖 DAG。
2. 文本 Hash 与语义 `export_fingerprint` 的分离。
3. `WAITING_WORKFLOW`、`WAITING_ASSET`、`WAITING_MANUAL_QC` 等生产状态。
4. 独立 Production Worker、Checkpoint 和 Job Journal。
5. Chapter Master / Book Master 级别的导出 Manifest。

这部分应作为正式平台的生产运行时扩展，不应再单独维护一套平行导演台。


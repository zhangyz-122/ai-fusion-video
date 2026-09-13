# Universal Director OS：导演运行时与研究沉淀

来源：RunningHub最强工作流、RunningHub工作流研究、融光研究与落地推进、审计并验证融光 AI Drama OS、视频生成技能周报、AI 视频灵感搜集。

证据级别：架构结论大多是 `SOURCE_REFERENCED`；外部项目的提交、社区反馈和对话中自报的测试，不能直接升级为 `REAL_RUNTIME_VERIFIED`。

## 一、唯一入口，内部多阶段

```text
用户输入小说/剧本/一句话想法
          ↓
Universal Director UI
          ↓
Story / Character / Scene / Prop / Voice Bible
          ↓
Episode → Scene → Beat → Shot IR
          ↓
Director IR + World State + Reference Pack
          ↓
Workflow/Model/Runtime Router
          ↓
Storyboard / Keyframe / Video / Audio / Post
          ↓
Specialist QC + VLM Judge
          ↓
RepairPass 或 EditPass
          ↓
Timeline / Episode Master / Export
```

用户不需要在 RunningHub、Krea、ComfyUI、剪辑器和多个 Skill 之间切换。它们都是后台的能力实现或适配层。

## 二、RunningHub 研究带来的生产编排模型

RunningHub 在本体系中应被理解为**工作流执行与生产编排参考**，不是用户界面本身。核心链路：

```text
整本小说 TXT
→ Book Compiler
→ Chapter Hash / Incremental Recompile
→ Entity Resolver
→ Story Bible
→ Script / Beat IR
→ Semantic Resolver
→ Narrative + Physical Events
→ World State / Knowledge / Open Loops
→ Shot Graph
→ Assets / Locks / Director Prompt
→ Production Build DAG
→ Storyboard
→ Video Render
→ QC
→ Post
→ Chapter Master
→ Book Master
→ Export
```

生产目标等级：

```text
IR_ONLY
STORYBOARD_MASTER
VIDEO_DRAFT
VIDEO_FINAL
MASTER
```

建议任务状态：

```text
PENDING, READY, RUNNING, WAITING_WORKFLOW, WAITING_ASSET,
WAITING_MANUAL_QC, SUCCEEDED, FAILED, BLOCKED, PAUSED, CANCELLED
```

长任务需要持久化 Worker、Job Journal、Checkpoint 和可恢复状态。发生进程崩溃时，`RUNNING` 应通过 recovery 回到 `READY` 或准确的等待状态，而不是把任务永远留在运行中。

## 三、工作流 Registry 与统一 Profile

所有执行能力统一经过 WorkflowProfile/Provider Profile，至少记录：

```text
workflow_api_json
prompt_binding
seed_binding
asset_binding
required_slots
result_parser
revision
sha256
model_capabilities
runtime_requirements
validation_gates
```

来源任务中出现过的能力名可以作为候选映射：

```text
WF002           → STORYBOARD
WF009           → Wan FLF
DS006           → H3 Director
WF011           → LongCat
QWEN3VL_QC      → QC
WF012           → Master Post
```

这些名称不能被当成当前仓库已经存在的正式 ID；正式发布前需要绑定真实 API JSON、节点 schema、模型、运行环境和验证证据。

## 四、增量编译与状态依赖

章节是否需要重编译，不能只看数据库更新时间：

```text
text_sha256
        ↓
export_fingerprint
        ↓
ChangeImpact
        ↓
仅重编译受影响的 Chapter / Scene / Beat / Shot
```

依赖类型可包括：

```text
WORLD_STATE_CHAIN
NARRATIVE_STATE_CHAIN
ENTITY_STATE
FACT_LOOP
IDENTITY
VISUAL
PROMPT
WORKFLOW
RUNTIME
TIMELINE
AUDIO
```

修改角色服装、道具状态、世界位置、镜头 Prompt 或工作流版本时，需要通过有向依赖图做 `STALE` 传播；不能依靠用户凭记忆判断哪些镜头仍然有效。

## 五、World State 与 Canonical Commit

三种状态必须分开：

```text
Narrative State
= 世界发生了什么

Production State
= 作品做到哪一步

Execution State
= 计算任务运行到哪一步
```

角色身份/资产版本也必须与剧情状态分开：

```text
CHARACTER_ID / COSTUME_REVISION / PROP_ID
        ≠
这一镜衣服是否湿、剑是否出鞘、门是否打开
```

镜头状态建议遵循：

```text
PRE_STATE
→ 当前镜头声明的 State Delta
→ 生成
→ QC
→ PASS 才 COMMIT；FAIL 则 ROLLBACK
→ POST_STATE 供下一镜继承
```

状态提交应具备 `parent_commit_id`、`pre_state_hash` 和 `idempotency_key`。所有下游事件通过 outbox 或等价可靠机制发布，避免数据库提交成功但生产事件丢失。

## 六、质量不是一个 ready 布尔值

研究任务反复验证了：

```text
文件存在 ≠ Runtime 可发现
Runtime 未验证 ≠ 失败
Provider Success ≠ Quality Success
One Golden PASS ≠ Whole Workload Space PASS
```

统一 ValidationGate：

```text
PASS / FAIL / BLOCKED / NOT_RUN / UNKNOWN
```

并区分范围：

```text
static / offline / runtime / canary / production
```

能力可以因此分别处于：

```text
READY_FOR_AUTHORING
READY_FOR_TEST
READY_FOR_PRODUCTION
BLOCKED
```

## 七、Quality Drift 与模型身份

`Provider Model ID` 不能永远代表不可变的物理模型。生产证据中应尽可能保存：

```ts
interface ResolvedModelIdentity {
  logicalModelId: string;
  providerModelId: string;
  providerModelAlias?: string;
  providerRevision?: string;
  providerBuildId?: string;
  artifactHash?: string;
  resolutionSource: "PROVIDER_RESPONSE" | "MODEL_CATALOG" | "STATIC_MAPPING" | "UNKNOWN";
  resolvedAt: string;
  identityHash: string;
}
```

同一模型不同参考数量、时长、分辨率和优化 Profile 可能处于不同失败区域，所以 QA 观察必须带 `workloadProfileHash` 或 `workloadClass`。质量漂移先做确定性滚动窗口，不要第一版就引入 ML：

```text
STABLE
SUSPECT
DEGRADED
CRITICAL
```

它们影响的是 `RouteQualityEligibility`：

```text
FULL / PREVIEW_ONLY / CANARY_ONLY / BLOCKED
```

只阻断受影响的 workload class，不能因为 H3 Ref2VA 高参考异常就全局禁用 H3 T2V。

## 八、变更驱动 Golden Scenario

固定跑完整套 Golden 不适合所有改动；应把变更映射到覆盖元数据：

```text
Code / Runtime / Workflow Change
→ ChangeImpactFingerprint
→ GoldenScenarioSelector
→ Core Sentinel
  + affected component cases
  + LOW / NOMINAL / HIGH-STRESS workload
→ Certification
```

任何改动都至少保留：

```text
Local Comfy happy path
Artifact finalization
Production state reducer
Mutation concurrency
```

生产失败只能转化为证据、漂移和候选 Golden Case，不能自动修改 Canon 或 Recipe。

## 九、技能周报中值得吸收的候选能力

### 1. Dual-stage / 4+4 Latent Refinement

```text
低分辨率 4 steps
→ clean latent x0
→ learned latent upscaler
→ 高分辨率 4 steps
→ final AV
```

值得抽象成 `CoarseThenRefineRecipe`、`DualStageModelRefineSkill` 和 `StageSpecificModelRoutingSkill`。它与“先生成多个候选再选赢家”的 Seed Scout 不同，是一次最终生成内部的粗到细两阶段。

### 2. High-resolution Prefix Lock

上一段高分辨率尾部作为下一段已知 video region，在采样时锁定；这不是简单把上一帧当图片参考。应记录 context representation、context frame 数、latent units 和 `chain_id`，避免不同 context 模式复用错误缓存。

### 3. Continuation Minimalism

续接不应默认把所有 Reference、Guide 和旧视频重复堆入：

```text
Reference       → 身份/服装/声音
Latent + Mask   → 已经发生到哪里
Guide           → 只在需要额外结构约束时加入
```

建议 Golden Case：比较 latent+mask、latent+mask+refs、再加 Guide、重复 video ref 等组合，重点看 seam、运动相位、身份、颜色、音频连续性。

### 4. PreserveAndTransform

LTX 水模拟类能力提供了一个通用抽象：保持身份、服装、姿势、镜头和几何，只增加受约束的物理效果。可作为 `PreserveAndTransformRecipe` 候选，先从干→湿、无雨→下雨、普通→水花等有限场景验证。

### 5. Specialist QC

视频 QC 不应只由一个 VLM 负责：

```text
TemporalCV       → 闪烁、冻结、亮度跳变、光流尖峰
Identity Judge    → 人脸 cosine、缺脸、漂移斜率
GroundingDINO    → 人物/道具数量与语义锁
Dialogue Judge   → 口型、静默角色、音画同步
VLM Judge        → 故事、动作、摄影和综合判断
```

缺少某个 Specialist 时应排除该项，而不是直接扣成 0 分。身份分数要保留原始 cosine 和校准状态，不能把未经校准的 0–100 映射伪装成标准。

### 6. EditPass 与 RepairPass 分层

```text
RepairPass = 结果违反原目标，修回原目标
EditPass   = 导演改变目标，产生新的创作版本
```

EditPass 可支持删/加/替换物体、环境变化、重风格化、文字替换和对白替换；必须保存 `preserve_constraints[]`、`changed_decision_refs[]`、`parent_version_ref`。对白替换还要验证原时长、新时长、语速、口型和 fallback。

### 7. MusicIntent

音乐不只是最后拖入时间轴的附件，可以进入：

```text
MusicIntent
→ MusicRecipe
→ MusicAssetVersion
→ MusicStructure / beats / cue_points
→ Shot / Cut / Action timing
```

但版权、混音 ducking、时间轴重绑定和重新生成后的关系仍需另外实现。

## 十、预算与批量背压

研究中还冻结了一个生产原则：

```text
Provider Capability ≠ Provider Capacity ≠ Financial Capacity
```

批量生产必须经过：

```text
Estimate
→ Reserve
→ Dispatch
→ Provisional Reconcile
→ Final Settle
```

本地队列同时考虑 Provider capacity、项目并发、预算容量和 Provider backlog；不能因为 Provider 宣称支持 100 个并发就把 1000 个镜头一次性灌进去。

## 十一、当前建议的最小实施顺序

```text
P0 统一 WorkflowProfile + ValidationGate
P0 角色/场景/道具/声音资产版本与依赖失效
P0 Director IR / Shot IR / World State 交接
P0 任务幂等、取消、恢复、Checkpoint、Export Manifest
P0 真实 ComfyUI Golden Episode
P1 Specialist QC 与 Repair/Edit 版本图
P1 预算预留和批量 Admission
P1 双阶段渲染、Prefix Lock、Continuation 实验
P2 MusicIntent、复杂自动编排和外部 Provider 扩展
```


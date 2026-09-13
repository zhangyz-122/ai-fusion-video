# MASTER-AUDIT-001｜融光 AI Drama OS 真实仓库审计（初始快照）

审计时间：2026-09-13（Asia/Shanghai）  
审计范围：`C:\Users\ASUS\Documents\Codex\2026-09-04\referenced-chatgpt-conversation-this-is-an\work\ai-fusion-video`  
基线：`main` @ `f2bea0faa889fe4545f89b4c762fca03c4871503`  
远端：`https://github.com/Stonewuu/ai-fusion-video.git`  
工作树：存在 55 个已跟踪文件改动及未跟踪文件/目录；未回滚、未覆盖。

## 总判断（审计时点）

以下结论是 2026-09-13 初始只读审计时点，实施后的状态见 [IMPLEMENTATION_REBASE_001.md](IMPLEMENTATION_REBASE_001.md)。

```text
Generic ComfyUI foundation: IMPLEMENTED / test evidence exists historically
P0 production orchestration: NOT_STARTED
Gate B: FAIL
Gate C: FAIL
WAN I2V / FLF / InfiniteTalk runtime: BLOCKED at current ComfyUI preflight
VERTICAL-SLICE-001: BLOCKED — no ProductionRun/Step/Take implementation or API
FIRST_ACTIONABLE_PR: PR-002
Overall Production Readiness: 4%
```

仓库已有可复用底座：`StoryboardItem`、`VideoTask(count/workflowVersionId)`、`VideoItem`、`RedisTaskQueue`、`VideoGenerationConsumer`、`ComfyUiWorkflowService`、`ComfyUiGenerationExecutor` 和 `VideoComposeService`。但没有发现 `ProductionRun`、`ProductionStep`、`ProductionTake`、`WorkflowProfile`、`selectedTakeId`、QC、Story Event 或 P0 Harness。不能把这些底座等同为 Vertical Slice 已完成。

## 关键证据

| 能力 | 真实源码证据 | 审计结论 |
|---|---|---|
| Shot/Storyboard | `ai-fusion-video/src/main/java/com/stonewu/fusion/entity/storyboard/StoryboardItem.java:26-144` | 有 StoryboardItem，但只有 legacy `generatedVideoUrl`，没有 Production-owned 字段 |
| Multi-take 输入 | `ai-fusion-video/src/main/java/com/stonewu/fusion/entity/generation/VideoTask.java:25-118` | `count`、`successCount`、`workflowVersionId` 存在 |
| 单结果 | `ai-fusion-video/src/main/java/com/stonewu/fusion/entity/generation/VideoItem.java:22-57` | VideoItem 存在，但无 ProductionTake ingestion |
| 既有队列 | `ai-fusion-video/src/main/java/com/stonewu/fusion/infrastructure/queue/RedisTaskQueue.java:22`；`.../VideoGenerationConsumer.java:43-97` | 可复用；未发现第二 Production Queue |
| ComfyUI 工作流 | `.../ComfyUiWorkflowService.java:30,151-272`；`.../ComfyUiGenerationExecutor.java:30-166` | 通用 Workflow/Version/绑定/发布/执行底座存在 |
| Legacy Compose | `.../VideoComposeService.java:107-420`，尤其 `:256` | 读取现有视频字段；没有 selectedTake 分支 |
| 迁移 | `ai-fusion-video/src/main/resources/db/migration/V1.1.1.0.0__comfyui_workflow_support.sql` | 有 ComfyUI workflow migration；没有 production/QC/event migration |
| 测试资产 | `ai-fusion-video/src/test/java/.../VideoGenerationConsumerTests.java`、`VideoComposeServiceTests.java`、`.../ComfyUiWorkflowRendererTests.java` | 有底座单测；没有 Production vertical-slice 测试 |

## Runtime 结论

正在运行的现场服务是 `C:\AI-T8-video-onekey` 的本地 ComfyUI，HTTP `127.0.0.1:8188` 可用；不是仓库内 2026-09-04 的 ComfyUI 副本。现场报告：RTX 4090 24,564 MiB、NVIDIA 616.56、Python 3.12.10、PyTorch 2.10.0+cu130、ComfyUI 0.33.0。现场加载了 232 个 custom-node 目录，其中日志记录 `ComfyUI_RH_DreamID-V` IMPORT FAILED；日志还记录 xformers/Flash Attn/Sage Attn installed，运行日志出现 `Using sageattn`。

RT01-RT04 已执行，详见 Runtime Capsule。RT01 成功；RT02/RT04 在 API preflight 结构化失败；RT03 复用同一个 `prompt_id` 查询历史且未二次提交。

## Workflow 结论

从历史 `UniversalDirector_V1.1.json` 抽取了三个单能力 API-format 文件：

```text
outputs/workflow-gold/WAN_I2V_STANDARD.json
outputs/workflow-gold/WAN_FLF_STANDARD.json
outputs/workflow-gold/WAN_INFINITETALK.json
```

抽取结果分别为 13、15、16 个节点，已显式绑定 prompt、image/audio、width、height、numFrames、seed、output。真实当前服务 preflight 全部没有取得 `prompt_id`：I2V/FLF 的 Wan model、Wan VAE、T5、CLIP Vision 和输入媒体不在当前服务有效列表；InfiniteTalk 还被当前节点注册差异 `NUM_FRAMES (4n+1)` 阻断。没有执行实际生成，不将它们标为 E3。

## 审计限制

- `AI_VIDEO_TASKBOOK.md` 和 P0 生产 Contract 未在真实仓库中发现；PR 定义依据已恢复的 MASTER-AUDIT-001 任务书。
- 当前仓库工作树很脏，审计只读，不把未提交改动解释成已验证功能。
- Java 验证未能启动，因为当前 shell 没有可用 `java/javac` 或 `JAVA_HOME`。
- 前端 `pnpm exec tsc --noEmit` 未能到达 TypeScript 阶段：pnpm 依赖安装被 workspace supply-chain policy 的 ignored build scripts 阻断。

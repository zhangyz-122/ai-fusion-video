# AI Drama OS — 基线冻结文档（PR-001）

> 上游：`Stonewuu/ai-fusion-video`
> 研究基线 SHA：`f2bea0faa889fe4545f89b4c762fca03c4871503`
> 冻结日期：2026-09-06
> 分支：`feat/ai-drama-os-pr001-baseline`
> 状态：已确认

---

## 1. 运行环境

| 项 | 值 |
|---|---|
| Java | 21（Spring Boot 3.5.14 / Maven） |
| 前端 | Next.js（`ai-fusion-video-web/`，app router） |
| 数据库 | MySQL（Flyway migration） |
| ComfyUI | 通过 ComfyUiWorkflowService 远程调用 |
| 部署 | docker-compose（预构建镜像 / 源码构建 / 分离部署） |
| 包结构 | `com.stonewu.fusion` |

---

## 2. 现有 Flyway 迁移

| 版本 | 描述 |
|---|---|
| V1__init_mysql.sql | 初始化全量表结构 |
| V1.1.0.0.8__agent_state_retention.sql | Agent 状态保留 |
| V1.1.1.0.0__comfyui_workflow_support.sql | ComfyUI Workflow 支持 |

Production 新增迁移应从 `V1.1.2.0.0` 开始，绝不修改历史 migration。

---

## 3. 已确认的现有能力

### 3.1 Storyboard 主链

| 层 | 文件 | 说明 |
|---|---|---|
| Entity | `entity/storyboard/StoryboardItem.java` | 37 个字段，含 production 所需全部基础字段 |
| Controller | `controller/storyboard/` | CRUD + Agent Tool |
| 前端页面 | `app/(dashboard)/projects/[id]/storyboards/` | 分镜编辑 + StoryboardRefPanel |
| 前端 API | `lib/api/storyboard.ts` | 类型定义 + 调用封装 |

StoryboardItem 关键 production 字段（已有）：
```text
firstFrameImageUrl / lastFrameImageUrl
firstFramePrompt / lastFramePrompt
generatedImageUrl / generatedVideoUrl
videoPrompt
shotType / cameraMovement / cameraAngle / cameraEquipment
duration
content / dialogue / sound / soundEffect / music
characterIds / sceneAssetItemId / propIds
customData (JSON)
```

### 3.2 ComfyUI Runtime

| 文件 | 说明 |
|---|---|
| `service/ai/comfyui/ComfyUiWorkflowService.java` | Workflow CRUD + 版本管理 |
| `service/ai/comfyui/ComfyUiGenerationExecutor.java` | 生成执行器 |
| `service/ai/comfyui/ComfyUiWorkflowValidationService.java` | 校验 |
| `service/ai/comfyui/ComfyUiInputBinding.java` | 输入绑定 |
| `service/ai/comfyui/ComfyUiOutputBinding.java` | 输出绑定 |
| `service/ai/comfyui/ComfyUiOutputResolver.java` | 输出解析 |
| `service/ai/comfyui/ComfyUiPreparedSubmission.java` | 预提交 |
| `service/ai/comfyui/ComfyUiExecutionContext.java` | 执行上下文 |
| `service/ai/comfyui/ComfyUiWorkflowDefinition.java` | Workflow 定义 |

Workflow 支持不可变版本 + 发布 + 校验。

### 3.3 Generation Runtime

| 文件 | 说明 |
|---|---|
| `entity/generation/VideoTask.java` | 生成任务（含 projectId / prompt / first/last frame / ref images / ratio / resolution / duration / seed / cameraFixed / count / modelId / workflowVersionId） |
| `entity/generation/VideoItem.java` | 生成结果（videoUrl / coverUrl / duration / status / error / firstFrameUrl / lastFrameUrl） |
| `service/generation/video/consumer/VideoGenerationConsumer.java` | 消费器（RedisTaskQueue / 按模型队列 / 并发控制 / workflow version pin / capability validation / cancel / sync wait / count=N） |

### 3.4 Compose

| 文件 | 说明 |
|---|---|
| `service/storyboard/VideoComposeService.java` | 视频合成（StoryboardController 已接入） |

### 3.5 前端结构

```text
ai-fusion-video-web/
  app/(dashboard)/projects/[id]/
    storyboards/
      page.tsx
      layout.tsx
      _components/
        batch-gen-dialog.tsx
        editable-cell.tsx
        script-episode-binding.tsx
        ...
  lib/api/
    storyboard.ts
    ...
```

注意：前端工作目录为 `ai-fusion-video-web/`（非 src/ 子目录），app router 直接在根。

---

## 4. StoryboardItem 完整字段清单

```text
id                          Long (AUTO)
storyboardId                Long
storyboardEpisodeId         Long
storyboardSceneId           Long
sortOrder                   Integer = 0
shotNumber                  String
autoShotNumber              String
imageUrl                    String
referenceImageUrl           String
videoUrl                    String
generatedImageUrl           String
firstFrameImageUrl          String
lastFrameImageUrl           String
firstFramePrompt            String
lastFramePrompt             String
generatedVideoUrl           String
videoPrompt                 String
shotType                    String
duration                    BigDecimal
content                     String
sceneExpectation            String
sound                       String
dialogue                    String
soundEffect                 String
music                       String
cameraMovement              String
cameraAngle                 String
cameraEquipment             String
characterIds                String
sceneAssetItemId            Long
propIds                     String
customData                  String (JSON)
+ BaseEntity 通用字段（created_at / updated_at / deleted 等）
```

**结论：Production 层不需要新建 Shot 主表。ShotSpec 是 StoryboardItem 的 production extension。**

---

## 5. 现有 Docker 部署

| 文件 | 模式 |
|---|---|
| docker-compose.yml | 拉取预构建镜像 |
| docker-compose.build.yml | 源码构建 |
| docker-compose.separated.yml | 前后端分离部署 |

环境配置：基于 `.env.example` 创建 `.env`。

---

## 6. Upstream 同步策略

| 策略 | 说明 |
|---|---|
| fork 模式 | GitHub fork + feature branch + PR upstream |
| 本地克隆 | `/d/ai-fusion-video`，分支 `feat/ai-drama-os-pr001-baseline` |
| 同步频率 | 按需手动 `git pull upstream main`；Production 层代码独立于 upstream main |
| 冲突处理 | Production 新增文件与 upstream 无冲突；StoryboardItem 字段新增需 review upstream 变更 |
| ADR | 见 `adr/001-baseline-freeze-and-upstream-strategy.md` |

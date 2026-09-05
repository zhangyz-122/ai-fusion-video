# Production Domain Contract

> 本文件是 AI Drama OS 的领域契约，后续所有 PR 必须遵守。
> 修改本文件需要新 ADR。

## 1. 核心流程

```text
StoryboardItem (已有)
    ↓ ProductionRun
GENERATE_VIDEO Step
    ↓ WorkflowProfile 解析
VideoTask (已有, count=N)
    ↓ VideoGenerationConsumer (已有)
N × VideoItem (已有)
    ↓ ProductionTake ingestion
N × ProductionTake
    ↓ QC (auto + manual)
Select Take (selectedTakeId)
    ↓ 投影回 StoryboardItem
VideoComposeService (已有)
    ↓
Final MP4
```

## 2. 实体契约

### afv_production_run
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | PK |
| project_id | BIGINT NOT NULL | 所属项目 |
| storyboard_id | BIGINT NOT NULL | 所属分镜 |
| storyboard_episode_id | BIGINT | 所属集（nullable） |
| run_type | VARCHAR(32) NOT NULL | 如 BATCH_PRODUCE / SINGLE_SHOT / EPISODE |
| status | VARCHAR(32) NOT NULL | PENDING/READY/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED |
| idempotency_key | VARCHAR(128) UNIQUE | 防重复创建 |
| started_at | DATETIME | |
| finished_at | DATETIME | |
| metadata_json | JSON | 扩展 |

### afv_production_step
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | PK |
| run_id | BIGINT NOT NULL FK → run | |
| storyboard_item_id | BIGINT NOT NULL FK → storyboard_item | |
| step_type | VARCHAR(32) NOT NULL | GENERATE_IMAGE / GENERATE_VIDEO / QC / MEDIA / HUMAN |
| status | VARCHAR(32) NOT NULL | 同 Run 状态机 + RETRYING + BLOCKED + REVIEW_REQUIRED + APPROVED |
| depends_on_json | JSON | 前置 Step id 数组 |
| execution_type | VARCHAR(16) NOT NULL | IMAGE / VIDEO / QC / MEDIA / HUMAN |
| execution_ref_id | BIGINT | 指向 ImageTask/VideoTask/QC job 的 id |
| attempt | INT DEFAULT 0 | 重试次数 |
| parent_step_id | BIGINT | 重试时的父 Step |
| error_code | VARCHAR(64) | |
| error_message | TEXT | |
| started_at / finished_at | DATETIME | |

唯一约束：`UNIQUE(run_id, storyboard_item_id, step_type, attempt)`

### afv_production_take
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | PK |
| run_id | BIGINT NOT NULL | |
| storyboard_item_id | BIGINT NOT NULL | |
| source_type | VARCHAR(16) NOT NULL | IMAGE_ITEM / VIDEO_ITEM |
| source_item_id | BIGINT NOT NULL | VideoItem.id 或 ImageItem.id |
| workflow_profile_id | BIGINT | 使用的 Profile |
| workflow_version_id | BIGINT | pin 的 Workflow Version |
| model_id | VARCHAR(64) | 使用的模型 |
| seed | BIGINT | |
| qc_status | VARCHAR(16) DEFAULT 'PENDING' | PENDING/PASS/FAIL/REVIEW_REQUIRED |
| metadata_json | JSON | first/last frame、duration 等冗余 |

唯一约束：`UNIQUE(source_type, source_item_id)` — 幂等 ingest

**注意：无 selected 布尔字段。选择 SSOT 是 StoryboardItem.selectedTakeId。**

## 3. 状态机

```text
PENDING → READY → QUEUED → RUNNING → SUCCEEDED
                                  → FAILED → RETRYING → QUEUED
                                           → BLOCKED
                                  → REVIEW_REQUIRED → APPROVED
any → CANCELLED (except SUCCEEDED/APPROVED)
```

非法迁移由 `ProductionStepService.transition()` 拒绝。

## 4. 依赖注入规则

- Production 层只通过 `VideoTaskService` / `ImageTaskService` 间接调用 ComfyUI
- Production 层不直接引用 ComfyUiWorkflowService（通过 WorkflowProfileResolver 间接引用）
- Production 层不直接操作 Redis 队列（通过 VideoGenerationConsumer 的公开接口提交）

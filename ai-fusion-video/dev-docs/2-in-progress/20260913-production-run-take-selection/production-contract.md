# Production Contract｜VERTICAL-SLICE-001

## Ownership

- `StoryboardItem` remains the canonical storyboard object.
- `ProductionRun` owns one user-requested production attempt for one `StoryboardItem`.
- `ProductionStep` owns execution provenance and recovery state for that run.
- Existing `VideoTask` and `VideoItem` remain the generation system of record.
- `ProductionTake` is the run-scoped projection of a `VideoItem`; it does not duplicate media bytes.
- `StoryboardItem.selectedTakeId` is the only selected-take projection used by Compose.
- `StoryboardItem.videoUrl` and `generatedVideoUrl` remain Legacy fields and are not overwritten by Production.

## State contract

```text
ProductionRun:
CREATED → WAITING_GENERATION → QC_PENDING → SELECTED
                         └──────→ FAILED

ProductionStep:
CREATED → SUBMITTED → SUCCEEDED
                 └──→ FAILED

ProductionTake.qcStatus:
REVIEW_REQUIRED ↔ PASS
REVIEW_REQUIRED ↔ FAIL
```

## Invariants

1. A start request must contain `storyboardItemId` and a non-blank `idempotencyKey`.
2. One `(userId, storyboardItemId, idempotencyKey)` creates at most one `ProductionRun`.
3. The first slice always submits the existing `VideoGenerationConsumer` with `VideoTask.count = 3`.
4. A successful task must produce exactly three `ProductionTake` rows; reconciliation is repeat-safe.
5. Only `QC PASS` can update `StoryboardItem.selectedTakeId`.
6. Compose resolves a selected Production take first; absent selection keeps the existing Legacy URL behavior.
7. A selected take must belong to the same storyboard item and reference a completed `VideoItem`.

## Explicit non-goals

- No second queue, Shot model, or media storage path.
- No silent fallback between I2V, FLF, InfiniteTalk, or unavailable models.
- No physical deletion of Legacy fields or historical task evidence.

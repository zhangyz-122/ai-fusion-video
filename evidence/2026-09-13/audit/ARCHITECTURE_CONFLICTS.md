# Architecture Conflict Report

## BLOCKER

| ID | Finding | Evidence | Impact |
|---|---|---|---|
| ARCH-CONFLICT-001 | Production Run/Step/Take layer absent | Repository-wide scan found no matching classes, migrations or APIs | VERTICAL-SLICE-001 cannot start; no authoritative orchestration |
| ARCH-CONFLICT-002 | selectedTakeId SSOT absent; Compose is legacy-only | `VideoComposeService` resolves `generatedVideoUrl` | Gate C fails; no safe selected-take or mixed compose path |
| ARCH-CONFLICT-003 | Current ComfyUI service is not using the shared Wan model roots | `/object_info` returned H3-only model lists and empty CLIP Vision list; shared Wan files exist elsewhere | All three extracted Wan workflows fail preflight; no real Run Capsule |

## HIGH

| ID | Finding | Evidence | Impact |
|---|---|---|---|
| ARCH-CONFLICT-004 | UniversalDirector source remains UI-format multi-branch | 76-node UI JSON with T2V/I2V/FLF/TALK/MOTION/VACE groups | Cannot be admitted as a single-purpose production workflow without explicit extraction and bindings |
| ARCH-CONFLICT-005 | Runtime startup has a custom-node import failure | `comfy_restart_dynamic.err.log` records `ComfyUI_RH_DreamID-V` IMPORT FAILED | Runtime revision is not clean; affected node families must be excluded until repaired |
| ARCH-CONFLICT-006 | Runtime dependency drift | V1.1 values use old `ImageResizeKJv2` enum values and InfiniteTalk primitive type differs from current runtime | Static artifact is not portable across the current runtime; preflight catches rather than silently adapting |

## MEDIUM

| ID | Finding | Evidence | Impact |
|---|---|---|---|
| ARCH-CONFLICT-007 | 232 custom-node directories in the active runtime | filesystem inventory | Large compatibility surface; keep Workflow dependency manifests narrow |
| ARCH-CONFLICT-008 | SoX is not available on PATH | `sox --version` failed | Audio-first / InfiniteTalk postprocess cannot claim complete runtime readiness |
| ARCH-CONFLICT-009 | Java verification toolchain unavailable in audit shell | `java`, `javac`, Maven wrapper failed because `JAVA_HOME` is not defined | Backend tests/build are NOT_RUN, not PASS |

## Explicitly not found

The scan did not find a second Production queue, `ProductionTake.selected`, `ProductionTake.isSelected`, a Python ComfyUI executor/client, a second Shot table, an unbounded RepairRouter, or a learned Router. These are “not found” results, not proof that every runtime behavior is safe.

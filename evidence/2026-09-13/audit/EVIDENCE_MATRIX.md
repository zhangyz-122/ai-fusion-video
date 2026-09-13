# Evidence Matrix

| Asset / capability | Engineering | Evidence | Runtime | Golden | Production |
|---|---|---:|---|---|---|
| StoryboardItem | Existing | E2 | — | — | — |
| VideoTask / VideoItem | Existing | E2 | — | — | — |
| RedisTaskQueue / VideoGenerationConsumer | Existing | E2 | — | — | — |
| ComfyUI Workflow/Version/Renderer | Existing | E2 | E3 protocol smoke only | — | — |
| Legacy VideoComposeService | Existing | E2 | — | — | — |
| UniversalDirector_V1.1 | Artifact recovered | E2 | preflight only | — | — |
| WAN_I2V_STANDARD | Verified runtime artifact | E3 | PASS: formal input accepted; local Wan stack resolved; H.264 MP4 emitted | `RUN-WAN-I2V-STANDARD-001` | — |
| WAN_FLF_STANDARD | Verified runtime artifact | E3 | PASS: formal start/end inputs accepted; local Wan stack resolved; H.264 MP4 emitted | `RUN-WAN-FLF-STANDARD-001` | — |
| WAN_INFINITETALK | Verified smoke artifact | E3 | PASS: local wav2vec2 + Wan stack resolved; MP4 emitted | `RUN-WAN-INFINITETALK-001` | 3d77817e-5a43-42f3-97da-f26d881f834e |
| Current ComfyUI service | — | E3 observed | 0.33.0 / 4090 / API live | — | — |
| RT01 LoadImage→SaveImage | — | E3 observed | PASS, output file emitted | — | — |
| RT02 missing node | — | E3 observed failure | Structured 400 preflight | — | — |
| RT03 prompt resume query | — | E3 observed | Same prompt_id queried, no resubmit | — | — |
| RT04 missing input | — | E3 observed failure | Structured 400 preflight | — | — |
| VERTICAL-SLICE-001 | Implemented | E3 | PASS on local 4090/ComfyUI | — | Run #4: 3 VideoItems → 3 ProductionTakes → QC/select → MP4 |
| P0 Golden Episode | Fixture defined | E2 | NOT_RUN | 12-shot fixture and acceptance gates exist; execution runner not yet run | — |

Evidence scale: E0 claimed/not present, E1 source-verified, E2 artifact recovered, E3 locally observed, E4 golden-tested, E5 production-observed. A locally observed failure is evidence of failure behavior, not evidence that the requested capability works.

## Readiness breakdown

```text
Existing Platform Foundation % : 72%
P0 Engineering %               : 8%
P0 Verification %              : 0%
P1 Engineering %              : 12%
P1 Evidence %                  : 5%
P2 Engineering %              : 0%
P2 Evidence %                 : 0%
Overall Production Readiness  : 4%
```

Overall uses the agreed weights: Engineering 35%, Runtime Verification 20%, Golden Evidence 20%, Reliability/Recovery 10%, Observability/Cost 5%, Production Evidence 10%. Research documents and recovered artifacts are not counted as production readiness.

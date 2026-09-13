# RunningHub + Krea 2：万能导演台整合路线

日期：2026-09-13

## 最终判断

两条关联任务值得纳入正式平台，但应当合成一条内部生产链：

- RunningHub 研究成果进入“生产编排层”。
- Krea 2 进入“关键帧与一致性层”。
- ComfyUI / RunningHub Workflow 进入“执行层”。
- 用户只面对一个“万能导演台”入口。

不要新增第二个前端、第二套 Agent、第二套 MCP 或几十个需要用户手工切换的菜单。

## 统一架构

```text
用户：小说 / 剧本 / 参考图 / 生成要求
                    ↓
            万能导演台
                    ↓
        Story / Book Compiler
                    ↓
        Episode / Scene / Beat IR
                    ↓
      Asset Registry + World State
                    ↓
       Shot Graph + Reference Router
                    ↓
          Krea 2 Keyframe Profile
          START / END 关键帧
                    ↓
        ComfyUI Video Profile
          H3 / Wan / 其他底座
                    ↓
        Technical + Visual QC
                    ↓
        局部重拍 / 选片 / 回填
                    ↓
       Voice / BGM / SFX / 字幕
                    ↓
          Chapter / Episode Master
                    ↓
               Export
```

## 当前正式平台已有与待补

### 已有

- 单入口视频工作台和默认视频底座。
- 项目、剧本分集、场次、分镜和角色/场景/道具资产。
- ComfyUI Workflow、输入绑定、输出解析和 WorkflowProfile。
- ProductionRun、ProductionStep、候选 Take、技术 QC、修复路由和视频合成。
- AgentScope 内置 Skills：导演编排、资产圣经、提示词编译、参考一致性、视频 QC。
- AgentScope 状态、工作区和平台任务队列。

### 第一优先级：接入生产编排

1. Book/Chapter Compiler。
2. 章节文本 Hash 与语义 Export Fingerprint。
3. Chapter Dependency Graph。
4. Production Build / Job Journal / Checkpoint。
5. `WAITING_WORKFLOW`、`WAITING_ASSET`、`WAITING_MANUAL_QC`。
6. Chapter Master / Book Master Export Manifest。

### 第二优先级：接入 Krea 关键帧

1. 增加 `KEYFRAME_START_END` 生成模式。
2. 将角色、服装、场景、灯光、风格和道具映射到明确输入槽。
3. 保存 `shot_id`、`asset_ids`、`state_in`、`state_out` 和参考角色。
4. Krea 关键帧输出直接进入 H3/Wan 视频 Profile。
5. 增加关键帧前的元数据连续性检查。

### 第三优先级：整集后期

1. Qwen3-VL 或等效视觉 QC 的结构化结果。
2. 候选选片与失败原因码。
3. 固定声线对白、音频时间轴和对白/环境音/BGM 混音。
4. SRT/ASS 字幕。
5. 非破坏时间线和整集 MP4 导出。

## 为什么不加 MCP

平台已经能够直接调用 ComfyUI/WorkflowProfile。MCP 在这里不是必要中间层：

```text
推荐：万能导演 → 平台后端 → WorkflowProfile → ComfyUI
```

不推荐：

```text
万能导演 → MCP → ComfyUI
```

后者会重复处理鉴权、超时、任务状态和错误解析。只有未来接入平台外部服务时，才考虑 MCP，例如第三方 TTS、云端素材库或外部研究服务。

## WorkflowProfile 统一契约

Krea、H3、Wan、QC、TTS 和 Post 都只作为 Profile/Executor 注册：

```text
profile_id
purpose
workflow_api_json
input_bindings
required_slots
asset_bindings
output_bindings
result_parser
capability_flags
revision
workflow_hash
```

导演层只选择“能力”和“任务目标”，不把某个模型的私有参数写进剧情数据。

## 一个镜头的内部数据

```json
{
  "shot_id": "SHOT_001",
  "scene_id": "SCENE_001",
  "beat_id": "BEAT_003",
  "asset_ids": {
    "character": "LIN_WUXIA",
    "costume": "LIN_WUXIA_COSTUME_A",
    "prop": "BLACK_UMBRELLA_01",
    "scene": "INN_RAIN_NIGHT",
    "lighting": "RAIN_WARM_COLD_01",
    "style": "DRAMA_CG_01"
  },
  "state_in": {},
  "start_frame_request": {},
  "end_frame_request": {},
  "video_request": {
    "duration": 5,
    "dominant_action": "raises_head_and_looks_camera_left",
    "camera": "slow_push_in"
  },
  "state_out": {},
  "provider_request": {},
  "quality_gate": {}
}
```

## 验收门

### 接入门

- ComfyUI 能真实接受 API Format。
- 输入槽和输出文件能被平台正确绑定和解析。
- 任务重启后能查询或恢复，不产生重复扣费/重复 Job。

### 一致性门

- 同一角色、服装、场景和道具在 START/END 中可追踪。
- 连续镜头的 state_out → state_in 检查通过。
- 参考图的身份、服装、场景、姿态和风格职责没有混淆。

### 生产门

- 3～5 个真实镜头通过基本 Benchmark。
- 至少测试 5 秒、10 秒、多参考、首尾帧、失败重拍和候选选片。
- 有音频时确认音频轨、对白时间点、字幕和最终 MP4 可播放。

### 证据边界

- 文本结构测试不等于真实模型推理。
- 假视频/假配音跑通不等于真实模型质量通过。
- 工作流能运行不等于角色一致性和用户审美通过。
- 只有真实 ComfyUI 任务、输出解析、QC 和用户验收全部通过，Profile 才能进入默认生产路径。

## 最终产品形态

用户只需要：

1. 输入小说、剧本或创作意图。
2. 上传角色、服装、场景、道具等参考素材。
3. 选择集数、画幅、时长和质量目标。
4. 点击生成或继续修复。

平台内部自动完成：

```text
编译 → 建档 → 规划 → 关键帧 → 视频 → QC → 重拍 → 后期 → 导出
```

这才是“一个入口、多个内部阶段”的万能导演台。


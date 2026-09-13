# Krea 2 研究：关键帧与多参考导演系统

日期：2026-09-13

## 说明

本文整理关联任务《介绍Krea 2》和《RunningHub工作流研究》中形成的 Krea 2 方案。关联任务里提到的权重、节点和自测结果是候选工作流记录，必须在当前 ComfyUI 环境真实跑通后才能标记为生产可用。

## 定位

Krea 2 补的是万能导演台的“关键帧与一致性层”，核心职责不是生成最终视频，而是：

```text
Reference Router
        ↓
Keyframe Director
        ↓
START / END Frames
        ↓
H3 / Wan / Seedance Video Executor
```

它把一个镜头拆成两个可检查的边界状态，让视频模型负责“从 A 动到 B”，而不是同时猜人物、服装、场景、灯光和动作终点。

## 多参考的职责拆分

### V1：身份与画风

角色身份参考图 + 文字提示词，解决基础人物一致性。

### V1.1：四路导演参考

```text
① Character Identity
② Costume
③ Pose / Composition
④ Visual Style
```

参考图职责解耦：

```text
人物图  → 只负责身份
服装图  → 只负责穿搭
Pose 图  → 只负责动作结构与构图
Style 图 → 只负责美术方向
```

关联任务记录的起始权重为：

```text
Identity 0.82
Costume  0.75
Pose     0.40
Style    0.55
```

这些数字只作为 Benchmark 初始点，不能当成所有人物和画风的固定标准。

### V1.2：六路导演参考

```text
① Character Identity
② Costume
③ Pose / Composition
④ Environment
⑤ Lighting / Mood
⑥ Visual Style
```

当前推荐的工作逻辑是：身份和服装优先，环境、灯光和风格作为较弱约束，避免参考图之间互相拉扯导致换脸或场景覆盖。

### V1.2S：Concept Slider 实验台

Slider 适合视觉探索，不应该和多参考 conditioning 强行串成一条链。候选滑杆包括：

```text
Age
Realism
Fog Density
Color Saturation
Brightness
Fine Detail
```

正式关键帧使用多参考主工作流；视觉试验才使用 Slider 工作流。两个工作流应当作为同一平台 Profile 下的不同执行模式，而不是把所有节点堆到一个图里。

## V1.3：START / END 双关键帧

同一个镜头共享：

```text
Character Identity
Costume
Environment
Lighting
Visual Style
```

只让 START 和 END 的 Pose / Action 状态不同：

```text
START
林雾夏低头，右手握伞，身体基本正面。

END
林雾夏缓缓抬头，视线转向画面左侧，肩膀轻微跟随转动。
```

产物：

```text
SHOT_001_START.png
SHOT_001_END.png
```

关联任务建议 START/END 固定 Seed，并优先微调 END Pose 强度，再考虑修改 END Seed。该建议需通过实际 A/B Benchmark 验证。

## 道具作为一级资产

伞、剑、法器、手机、项链等不能只存在于 Prompt 中，应进入资产注册表：

```json
{
  "prop_id": "BLACK_UMBRELLA_01",
  "canonical_reference": "props/BLACK_UMBRELLA_01/canonical.png",
  "state": "open",
  "holder": "right_hand"
}
```

道具变体至少需要支持：

```text
open / closed / damaged
right_hand / left_hand / waist / back
```

道具参考应当只研究形状、比例、材质、颜色、结构细节和装饰，不应把人物脸、手势、服装和背景带进条件链。

## 跨镜头状态

每个镜头需要有 `state_in` 和 `state_out`：

```json
{
  "state_in": {
    "location": "under_inn_eaves",
    "screen_position": "center_right",
    "facing": "camera_left",
    "costume_state": "wet",
    "prop_state": {"BLACK_UMBRELLA_01": "open"},
    "prop_holder": {"BLACK_UMBRELLA_01": "right_hand"},
    "injury_state": "none",
    "weather": "rain",
    "time_of_day": "night"
  },
  "state_out": {}
}
```

下一镜头的 `state_in` 必须合理继承上一镜头的 `state_out`。在 GPU 生成前先做便宜的元数据连续性检查，拦截：

```text
prop_changed_hand
spatial_jump
screen_direction_flip
injury_state_jump
costume_state_jump
```

要区分连续镜头与正常剪切：切镜允许机位、景别和构图变化，但不能无理由改变人物、服装、道具、伤势和剧情状态。

## 跨镜头视觉 QC

复核时同时比较：

```text
上一镜头 END
下一镜头 START
角色标准图
服装标准图
道具标准图
场景标准图
```

检查维度：

- Identity：脸、发型、体型和人数。
- Costume：服装主轮廓、颜色和状态。
- Prop：同一道具、状态、持有手和位置。
- Scene：空间结构、天气、时间和灯光方向。
- Spatial Handoff：位置、视线和屏幕方向。
- Emotion：情绪和动作结果是否自然继承。

QC 失败时默认只重拍责任镜头，不破坏已经通过的前一镜头。

## V2 整集闭环

关联任务中形成的目标生产链：

```text
小说 / 剧本
      ↓
Episode / Sequence / Shot
      ↓
角色 / 服装 / 道具 / 场景 / 灯光 / 风格资产库
      ↓
跨镜头状态管理
      ↓
Krea 2 START / END Keyframe
      ↓
MiniMax H3
      ↓
技术 QC
      ↓
Qwen3-VL 监制
      ↓
自动重拍计划
      ↓
选片
      ↓
固定声线对白
      ↓
SRT / ASS
      ↓
环境音 + BGM + 对白混音
      ↓
整集 MP4
```

声音接口保持 provider-neutral：导演台只保存对白时间点、角色声线 ID 和音频交付契约，实际 WAV 可以来自 RunningHub、本地 TTS 或其他已注册的 WorkflowProfile。

## 接入正式平台的前置条件

1. 在当前 ComfyUI 安装并确认 Krea Reference 节点/Recipe 可用。
2. 用真实角色、服装、场景和 Pose 跑通一个 START/END 镜头。
3. 导出 ComfyUI API Format，不使用仅供界面保存的普通 Workflow JSON。
4. 登记为 `WorkflowProfile`，记录输入槽、输出、节点版本、工作流哈希和必需资产。
5. 先做 3～5 个真实镜头 Benchmark，再决定是否进入默认导演路径。

Krea 2 不能通过 MCP 代替这条接入链路；它应当作为 ComfyUI 的关键帧工作流 Profile，由平台后端直接调用。


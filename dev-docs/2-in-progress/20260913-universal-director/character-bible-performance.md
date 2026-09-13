# 角色圣经与 AI 演员表演资产

来源：基础情绪表情提示词、扩展角色提示词、角色设定模板升级、生成角色设定图、创建角色设定板、整理角色提示词。

证据级别：角色描述和提示词是 `SOURCE_REFERENCED`；能否在某个模型中稳定锁脸，必须经过本地 Golden Case 和真实生成验收。

## 一、统一角色资产标准

角色不再是“一张漂亮图片”，而是：

```text
Character ID
  ↓
Identity DNA
  ↓
Character Bible / Turnaround
  ↓
Costume / Prop / Scene / Voice Assets
  ↓
Expression + Motion + Gaze Assets
  ↓
Reference Pack
  ↓
Shot / Video / QC
```

固定身份与临时状态必须分开：

| 固定身份 | 剧情运行状态 |
|---|---|
| 年龄、脸型、眼型、鼻唇、发型、体型、肤色、核心服装、标志配饰 | 表情、姿势、天气、湿度、受伤、道具持有、临时灯光、镜头角度 |

后续换装应产生新的 Costume Asset/Revision，不应悄悄改变 Character DNA；“衣服被雨淋湿”通常是 World State，不是新角色。

## 二、角色母档固定槽位

每个角色至少有以下八个槽位：

```text
年龄/体态
→ Facial DNA
→ Hair DNA
→ 主色与 Silhouette
→ 主服装结构/材质
→ Signature Prop
→ Personality
→ Genre Function
```

每个角色应保存：

```json
{
  "character_id": "CHAR_A01",
  "identity_revision": 1,
  "face_dna": {},
  "hair_dna": {},
  "body_dna": {},
  "costume_ids": [],
  "prop_ids": [],
  "voice_asset_id": null,
  "anchor_assets": {
    "FACE_MASTER": "ASSET_...",
    "FRONT": "ASSET_...",
    "THREE_QUARTER_LEFT": "ASSET_...",
    "THREE_QUARTER_RIGHT": "ASSET_...",
    "PROFILE": "ASSET_...",
    "FULL_BODY": "ASSET_...",
    "COSTUME_MASTER": "ASSET_..."
  }
}
```

推荐资产编号：

```text
LW-XA-FACE-01
LW-XA-HAIR-01
LW-XA-TOP-01
LW-XA-SKIRT-01
LW-XA-BOOT-01
LW-XA-BAG-01
LW-XA-EARRING-01
```

## 三、现代女主角色库

### 六套基础母版

| ID | 方向 | 识别锚点 | 主色/题材 |
|---|---|---|---|
| A01 | 橙调甜酷机能少女 | 深棕高马尾、亮橙短夹克、银圆耳饰、腿环 | 橙/黑；都市、校园、动作 |
| A02 | 奶油卡其轻通勤 | 低马尾、奶油卡其短外套、金属腕表 | 奶油/卡其；都市、轻职场 |
| A03 | 军绿街潮双马尾 | 深绿挑染双马尾、军绿机能外套、多层项链 | 军绿/黑；街头、舞台 |
| A04 | 银灰青绿未来机能 | 高盘发、银灰半透外套、青绿内搭 | 银灰/青绿；未来、科幻 |
| A05 | 冰蓝短发轻运动 | 蓝灰波波头、冰蓝运动外套、清澈眼神 | 冰蓝/白；青春、运动 |
| A06 | 紫调辣妹夜色系 | 长卷紫发、紫色短皮衣、星形耳饰 | 紫/黑；夜色、时尚 |

### A07–A18 扩展母版

| ID | 方向 | 关键视觉锚点 |
|---|---|---|
| A07 | 黑金轻奢财阀千金 | 黑长直侧分、黑金配色、细金耳钉、腕表 |
| A08 | 白衬衫冷感精英律师 | 白衬衫、低盘发、银色腕表、黑皮手包 |
| A09 | 薄荷绿治愈系医生 | 薄荷绿、白外套、低马尾、听诊器 |
| A10 | 红棕复古港风记者 | 红棕卷发、砖红唇、复古相机、棕色记者包 |
| A11 | 灰蓝理工科研少女 | 灰蓝夹克、低丸子头、透明防护眼镜 |
| A12 | 深海蓝刑侦女警 | 深海蓝战术夹克、半束短发、证件夹 |
| A13 | 蜜桃粉甜系设计师 | 蜜桃粉、半扎卷发、卷尺吊坠 |
| A14 | 墨绿文艺摄影师 | 墨绿风衣、相机背带、旧银戒指 |
| A15 | 白蓝网球运动女主 | 白蓝运动装、高马尾、蓝护腕 |
| A16 | 酒红舞台系女歌手 | 酒红内层染、银色耳返、星芒项链 |
| A17 | 米白自然系咖啡店主 | 栗棕卷发、米咖色、皮革围裙扣 |
| A18 | 黑银极简都市黑客 | 银灰挑染、黑银短夹克、耳骨夹、智能腕带 |

### 古风/类型角色母版

来源任务还提出 12 类可复用方向：都市甜酷、清冷御姐、元气运动、朋克机能、温柔白月光、东方大小姐、女杀手、科研工程师、废土游侠、国风侠女、妖系反派、未来机甲姬。

古风示例角色包括：楚玉眠（皇室才女）、谢惊鸿（冷艳女将）、沈照梨（江南医女）、苏妄月（神秘国师）、陆绯烟（盛世贵妃）、裴青岚（侠女刺客）。重点不是照抄名字，而是保留“不同骨相 + 不同气质 + 不同服装轮廓 + 不同道具”的差异化方法。

## 四、Character Bible 设定板版式

推荐固定为一张可复用的专业角色资产板：

```text
顶部：六视图 Turnaround
正面 / 3/4左 / 左侧 / 背面 / 3/4右 / 右侧

中部：Hero Pose
标准站姿 / 轻微行走 / 低机位或半身电影肖像

脸部区：
正脸 / 3/4 / 侧脸 / 眼眉鼻唇耳 / 皮肤与妆容

发型区：
正侧背 / 发际线 / 固定点 / 长度 / 发束材质

服装区：
正背面 / 裁片 / 缝线 / 腰头 / 鞋 / 包 / 饰品

材质与色卡区：
皮肤 / 发丝 / 布料 / 皮革 / 金属 / 玉石 / 主辅色

DNA Lock 区：
脸型剪影 / 眼型 / 嘴型 / 发型 / 服装剪影 / 标志物
```

角色设定板的核心不是排版漂亮，而是为后续生图、I2V、首尾帧、表情、动作和换场景提供同一个 Reference Asset。

## 五、可复用角色母档模板

```text
【角色代号】{{CHARACTER_ID}}
{{年龄}}岁{{地域/种族}}女性，身高{{身高}}，体态{{体型}}。

面部固定：
{{脸型}}；{{眉形}}；{{眼型/瞳色/眼神}}；{{鼻型}}；{{唇形/唇色}}；{{皮肤}}

发型固定：
{{发色}}、{{长度}}、{{发型结构}}、{{刘海/碎发}}。

服装固定：
{{上装结构与材质}}；{{下装结构与材质}}；{{鞋}}；{{外套/包/饰品}}。

固定识别锚点：
{{锚点1}}、{{锚点2}}、{{锚点3}}、{{锚点4}}。

性格与表演倾向：
{{人格}}；默认表演幅度{{0.0–1.0}}。

不可变化：
年龄、脸型、五官比例、发型、发色、体型、核心服装、标志配饰。
临时变化必须通过 Scene/Shot State 或 Costume Revision 声明。
```

## 六、镜头提示词应只描述变化

```text
REFERENCE CHARACTER: {{CHARACTER_ID}}

Strictly preserve the exact same identity, facial geometry, hairstyle,
body proportions, costume and accessories from the Character Bible.
Do not redesign or reinterpret the character.

SCENE: {{场景}}
SHOT SIZE: {{景别}}
CAMERA ANGLE: {{机位}}
CHARACTER ACTION: {{明确动作}}

FACIAL PERFORMANCE:
眉毛：{{状态}}
眼睛：{{视线方向及张力}}
嘴唇：{{状态}}
下颌：{{状态}}
头部：{{角度}}
情绪强度：{{0.0–1.0}}

CAMERA MOVEMENT: {{静止/推轨/跟拍/摇移}}
LIGHTING: {{方向/色温/软硬/空气介质}}
ENVIRONMENT INTERACTION: 服装、头发、道具遵循重力、惯性和接触关系。
CONTINUITY: no face morphing, no hairstyle/clothing/accessory change,
no body proportion change, no prop disappearance, no random camera movement.
```

## 七、AI 演员表演系统

表演不应只保存“她很悲伤”，而应保存可观察动作：

```text
Character DNA
→ Personality Profile
→ Emotion State
→ Expression
→ Gaze
→ Head Motion
→ Hand Gesture
→ Body Pose
→ Breathing
→ Voice State
→ Transition Timeline
→ Model Adapter
```

建议首批资产规模：

```text
50 表情
30 眼神
30 头部微动作
40 手势
30 身体姿态
30 说话状态
20 呼吸状态
50 情绪转场
```

这些是建议的资产容量，不代表已经全部实现。

## 八、50种表情的工程分组

| 编号 | 分组 | 内容 |
|---|---|---|
| 01–10 | 基础情绪 | 温柔微笑、开怀大笑、害羞、惊喜、思考、疑惑、委屈、落寞、悲伤落泪、情绪崩溃 |
| 11–20 | 复杂情绪 | 强颜欢笑、苦笑、压抑怒意、暴怒、冷漠无波、轻蔑、冷笑、阴沉压抑、杀意隐忍、胜券在握 |
| 21–30 | 爱情线 | 深情、心动、吃醋、宠溺、不舍、清冷、隐忍痛、温婉、惊慌、坚定 |
| 31–40 | 剧情微表情 | 疲惫、发呆、紧张、恐惧、突然醒悟、被背叛后的冷静、黑化前兆、嘲讽、释然、得意 |
| 41–50 | 高级微表情 | 眼神闪躲、嘴角轻颤、眉间微动、强装镇定、内心挣扎、突然心软、强忍怒火、心碎瞬间、雨中悲伤、多重情绪交织 |

### 工程化表达示例

```text
温柔微笑：嘴角对称轻微上扬，苹果肌轻提，下眼睑微收，眼尾柔和弯曲，
眉头放松，视线稳定柔和，下颌放松。

压抑怒意：双眉内收压低，眼神定住，嘴唇抿直，下颌线和咬肌轻微发力，
鼻翼轻张但保持克制，不做爆发性大动作。

被背叛后的冷静：先出现短暂眼神震动，随后眉眼恢复平直，嘴唇缓慢抿紧，
泪意可存在但不落下，情绪被迅速封闭。

心碎瞬间：眼神短暂停顿并失焦，眉头内侧轻抬，嘴唇短暂微张，嘴角慢慢下沉，
下颌轻颤，眼眶逐渐湿润。
```

## 九、表情状态机与强度

```yaml
expression_state:
  id: EXPR_013
  name: restrained_anger
  intensity: 0.50
  entry: neutral_to_tension
  hold: fixed_gaze_and_tight_lips
  exit: release_or_coldness
  facial_actions:
    brows: inner_brows_down_and_in
    eyelids: lower_lid_tension
    gaze: fixed_on_target
    mouth: thin_closed_line
    jaw: subtle_clench
  transition:
    from: neutral
    to: restrained_anger
```

建议强度：`0.25` 几乎不可察、`0.50` 克制自然、`0.75` 影视可读、`1.00` 强烈戏剧化。表情与人格修正器分开：同样是愤怒，少女、大家闺秀、女将军、腹黑反派和病娇的眉眼、嘴部、身体重心和动作幅度不同。

## 十、角色资产验收

```text
CHARACTER_DNA_STATIC
  年龄/脸型/五官/发型/体型/服装/配饰一致

TURNAROUND
  正/侧/背/3/4 结构可对齐

EXPRESSION_ONLY
  只改变表情，不改变身份

COSTUME_REVISION
  换装有明确 revision，不污染角色母档

SHOT_CONTINUITY
  跨镜状态、道具持有和朝向可追踪
```

最终结果只有在真实模型、真实参考包和目标画幅下验收后，才可以把对应角色/工作流升级到 `VERIFIED`。


# 时空感知引擎旁支：TrafficLab 3D → 融光 Spatial Engine

来源：`代码拆解与场景改造`。

定位：这是综合平台的矿山/交通/视频空间智能支线，不属于 AI 漫剧万能导演台的默认生产链。保留本文件是为了让其他 AI 了解这条独立能力边界，避免把两个产品方向混为一谈。

## 一、TrafficLab 3D 的可复用链路

```text
RTSP / MP4 / GB28181
        ↓
Detection：YOLO / 自研模型
        ↓
Tracking：ByteTrack / BoT-SORT / ReID
        ↓
Spatial Projection：像素 → 相机/地图/地形坐标
        ↓
Kinematics：速度 / 航向 / 轨迹 / 停留 / 越界
        ↓
Digital Twin：视频 ↔ GIS/Cesium/三维矿山
```

原项目的“推理与可视化解耦”、轨迹保存、配置化检测器/Tracker、单应性和双屏同步值得参考；PyQt5 单体 UI、Google Maps、平面假设和直接把 PoC 速度当生产数据则不应照搬。

## 二、矿山版升级

平面 Homography 只适合单一平面，矿山边坡、采坑、盘山道路和沟谷应改成：

```text
bbox / foot point
→ camera intrinsics K + distortion
→ camera pose R / T
→ camera ray
→ DEM / Mesh / 3D Tiles 求交
→ 真实 XYZ
→ WGS84 / CGCS2000 / 矿区局部坐标
→ Cesium / Three.js / GIS
```

标准输出可以是：

```json
{
  "camera": "CAM-023",
  "track_id": 1873,
  "class": "haul_truck",
  "confidence": 0.94,
  "position": {"x": 38421.53, "y": 12783.16, "z": 1467.32},
  "speed": 18.6,
  "heading": 127.4,
  "road": "北帮运输道路",
  "zone": "B3作业区",
  "timestamp": "2026-09-12T14:18:32.124"
}
```

## 三、平台对象模型

在既有“协议 → 函数 → 标准化”架构上增加：

```text
SpatialObject
SpatialTrack
SpatialZone
SpatialRelation
SpatialEvent
```

推荐分层：

```text
Spatial Object Layer
        ↓
Geometry Layer
        ↓
Relation Layer
        ↓
Rule Layer
```

有 GNSS 的车辆可以使用 GNSS；没有 GNSS 的对象使用视觉反投影；两者都有时互相校验。道路中心线、DEM、Mesh、LiDAR 和 3D Tiles 都是空间约束源。

## 四、Spatial Rule DSL

规则不应写死对象 ID，而应由对象选择器、空间算子、时间算子、判定和联动动作组成。

第一批算子：

```text
INSIDE
DISTANCE
SPEED
DWELL
CROSS
PREDICTED_DISTANCE
```

配套：

```text
AND / OR / NOT / FOR / ENTER / EXIT
```

示例：

```yaml
rule_id: spatial_rule_001
name: 人员进入边坡危险区域
objects:
  subject:
    type: person
  zone:
    type: hazard_zone
    id: slope_zone_03
condition:
  operator: inside
  subject: subject
  target: zone
temporal:
  duration: 3s
actions:
  warning_level: orange
  event_type: person_in_hazard_zone
```

空间规则必须支持状态边沿，避免 `inside=true` 每 100ms 重复报警：

```text
ENTER / EXIT / CHANGE / RISE / FALL
```

## 五、3D Zone、动态 Zone 与预测

Zone 不只是二维 Polygon，还可以有：

```text
z_min / z_max
3D volume
risk_level
authorized_roles
effective_time
```

车辆可以生成按速度扩大的动态安全区：

```text
0–5 km/h   → 3m
5–20 km/h  → 8m
20–40 km/h → 15m
```

预测规则第一版不需要 AI，可用：

```text
P(t) = P0 + Vt
d(t) = |Pa(t) - Pb(t)|
```

得到最近距离和最近接近时间，再形成 TTC/碰撞预警。

## 六、统一事件输出

```json
{
  "event_id": "EVT-20260913-001983",
  "event_type": "person_vehicle_collision_risk",
  "severity": "orange",
  "subject_objects": ["person_213", "truck_017"],
  "zone_id": "ROAD-03",
  "metrics": {"distance": 7.2, "ttc": 2.8, "truck_speed": 7.1},
  "rule_id": "RULE-PVC-001",
  "status": "active"
}
```

生成后直接进入已有 Warning Engine：

```text
Spatial Rule Engine
→ SpatialEvent
→ 预警分级
→ 报警/处置/升级/恢复/闭环
```

不要让视频 AI、GIS、GNSS 各自建立互不兼容的报警系统。

## 七、PoC 边界

先做：

```text
1 摄像机 + 1 段道路 + 1 种矿卡 + DEM/Mesh + Cesium
```

验收：目标识别、Track ID 稳定、GIS 定位误差、速度误差、视频/GIS 延迟、双向点击联动。后续再加多摄像机接力、GNSS/UWB 融合、ReID、地图匹配、逆行/超速/越界和时空回放。


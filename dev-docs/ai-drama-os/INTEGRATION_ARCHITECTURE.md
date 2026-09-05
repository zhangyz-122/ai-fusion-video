# AI Drama OS × pingtai 融合集成架构

> 目标：pingtai 平台完全融合 ai-fusion-video 功能，统一认证、统一入口、统一数据引用。

## 1. 架构模式：Side-by-Side Service + Auth Bridge + Reverse Proxy

```text
┌─────────────────────────────────────────────────────┐
│                    浏览器                             │
│   http://localhost:8000                              │
│   /dashboard/*     → pingtai 前端 (Next.js :8000)    │
│   /video-app/*     → 融光前端 (Next.js :3001)        │
│   /api/*           → pingtai 后端 (:19044)           │
│   /fusion-api/*    → 融光后端 (:8080) [proxy]        │
└─────────────────────────────────────────────────────┘
         │                    │                │
         ▼                    ▼                ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
│ pingtai      │   │ 融光后端      │   │ 融光前端          │
│ NestJS       │   │ Spring Boot  │   │ Next.js          │
│ :19044       │   │ :8080        │   │ :3001            │
│ PostgreSQL   │   │ MySQL        │   │                  │
│ Redis (共享)  │   │ Redis (共享)  │   │                  │
└──────────────┘   └──────────────┘   └──────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────────────────────────────┐
│           Auth Bridge (pingtai 内)       │
│  · 用户映射表 (pingtai_user_id ↔ fusion_user)│
│  · Token 代理获取/刷新/缓存              │
│  · 请求转发时注入 fusion access_token    │
└─────────────────────────────────────────┘
```

## 2. 认证桥接

### 流程
1. 用户登录 pingtai → 获取 pingtai JWT
2. 首次访问视频功能 → pingtai 后端用映射的融光账号调 `/api/auth/login` → 获取 fusion token
3. fusion token 缓存在 Redis（TTL 跟随 token 有效期）
4. 后续请求 → pingtai 后端从缓存取 fusion token → 注入 Authorization header → 转发到融光后端

### 用户映射
```sql
CREATE TABLE video_user_mapping (
  pingtai_user_id BIGINT PRIMARY KEY,
  fusion_user_id BIGINT NOT NULL,
  fusion_username VARCHAR(100) NOT NULL,
  fusion_token TEXT,
  fusion_refresh_token TEXT,
  fusion_token_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 凭据管理
- 首次绑定时：用户输入融光账密 → pingtai 后端调融光 login → 存 refresh_token
- 后续自动刷新 access_token
- 管理员可为整个团队配置服务账号

## 3. 反向代理

### 前端代理（edge-proxy → 融光前端）
- pingtai edge-proxy (:8000) 将 `/video-app/*` 转发到融光前端 (:3001)
- 融光前端 Next.js 配置 `basePath: '/video-app'`

### API 代理（edge-proxy → 融光后端）
- `/fusion-api/*` 转发到融光后端 (:8080)
- edge-proxy 注入 fusion access_token（从 Auth Bridge Redis 获取）

## 4. 数据集成

- 项目关联：pingtai project.id ↔ 融光 project.id（映射表）
- 资产引用：pingtai 文件存储 URL 可传给融光（通过 API）
- 事件通知：融光生成完成 → webhook 通知 pingtai

## 5. 部署

两个服务共存在同一个 Docker 网络：

```yaml
services:
  pingtai-backend:
    ports: ["19044:19044"]
  pingtai-frontend:
    ports: ["8000:8000"]
  fusion-backend:
    ports: ["8080:8080"]
    networks: [pingtai-network]
  fusion-frontend:
    ports: ["3001:3000"]
    networks: [pingtai-network]
  pingtai-postgres:
    ports: ["5432:5432"]
  fusion-mysql:
    ports: ["3306:3306"]
  shared-redis:
    ports: ["6379:6379"]
```

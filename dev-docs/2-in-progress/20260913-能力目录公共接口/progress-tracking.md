# 进度

- [x] 后端目录接口 + VO + 服务（`GET /api/ai/capability/catalog?modelType=`）
- [x] 前端接入（lib/api/capability.ts + 工坊页单次取数）
- [x] 单元测试（CapabilityCatalogServiceTests：模型/待接入/已禁用映射、启用工作流去重、脱敏断言）+ 后端回归
- [x] 前端 tsc / eslint（改动文件零告警）/ 生产构建通过
- [x] 双镜像重建上线
- [x] 实测：uitest 目录接口返回 2 个启用模型 + 3 个 Klein 待接入项，响应无 apiConfigId/binding/hash/graph 字段；未登录 401；他人 run/剧本/分镜/资产守卫保持有效
- [ ] 浏览器视觉验收：应用内浏览器 webview 不可用，待面板恢复后补看工坊页布局（低风险：待接入区块复用原管理员区块标记结构）

## 追加（同日）：视频 Profile 选项接口 + 生产 Drawer 模型/Profile/预览

- `GET /api/ai/capability/video-profiles`：返回启用 Profile 的脱敏选项（id/code/name/purpose），单测断言不含 contract/dependency 字段。
- `ProductionRunDetail.takes` 改为 `ProductionTakeView`：叠加 VideoItem 的 videoUrl/coverUrl/status/errorMsg；单测断言映射。
- 前端 Drawer：启动面板新增视频模型选择（默认预选 default 模型）与 Profile 选择（可留空跟随默认），启动请求带 modelId/workflowProfileId；候选卡片用 `<video>` 真实预览（poster 封面），未生成/失败有明确占位与错误信息。
- 实测：video-profiles 返回 3 个 Profile；run 隔离保持有效；tsc/eslint/build 通过；54→57 后端测试通过。

## 追加（同日）：视频工坊（/generate/videos）

- 新建视频工坊页，复用能力目录接口（modelType=3）：已启用视频能力卡片（含默认标记）链接到 `/generate/video?modelId=`，待接入区块所有人可见；结构与图像工坊一致。
- `/generate/video` 页支持 `?modelId=` 定向（镜像生图页），GenerationWorkbench 的 initialModelId 通路复用。
- 侧边栏新增"视频工坊"入口（生视频旧入口保留）。
- 验证：tsc/eslint（改动文件零告警）/生产构建通过；前端镜像重建上线；带认证 cookie 实测工坊页与带参编辑器页均 200（浏览器视觉验收仍受 webview 不可用限制，待恢复补看）。

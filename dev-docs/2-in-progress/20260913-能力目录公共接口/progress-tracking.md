# 进度

- [x] 后端目录接口 + VO + 服务（`GET /api/ai/capability/catalog?modelType=`）
- [x] 前端接入（lib/api/capability.ts + 工坊页单次取数）
- [x] 单元测试（CapabilityCatalogServiceTests：模型/待接入/已禁用映射、启用工作流去重、脱敏断言）+ 后端回归
- [x] 前端 tsc / eslint（改动文件零告警）/ 生产构建通过
- [x] 双镜像重建上线
- [x] 实测：uitest 目录接口返回 2 个启用模型 + 3 个 Klein 待接入项，响应无 apiConfigId/binding/hash/graph 字段；未登录 401；他人 run/剧本/分镜/资产守卫保持有效
- [ ] 浏览器视觉验收：应用内浏览器 webview 不可用，待面板恢复后补看工坊页布局（低风险：待接入区块复用原管理员区块标记结构）

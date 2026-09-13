# E2E 冒烟测试集(Playwright)

针对融光平台(N04 六条创作路径)的端到端冒烟用例,对**已部署的真实系统**执行,
只做只读断言与"提交前一步"验证,不真实生成图片/视频、不修改业务数据。

## 前置条件

1. Node 24 + `corepack pnpm`(仓库已配置 `packageManager`)。
2. 被测系统已部署并可访问,默认 `http://localhost:8081`;
   可用环境变量 `E2E_BASE_URL`(或 `BASE_URL`)覆盖。
3. 测试账号(与部署环境一致):
   - `zhangyz / zhangyz`(项目属主,拥有含分镜内容的项目);
   - `uitest / Uitest#2026`(权限对照账号,仅能看到自己的项目)。
4. 首次运行需安装依赖与浏览器(仓库根 `ai-fusion-video-web/` 下):

```bash
corepack pnpm install
corepack pnpm exec playwright install chromium
```

## 运行

在 `ai-fusion-video-web/` 目录执行:

```bash
corepack pnpm exec playwright test -c e2e/playwright.config.ts
```

指定环境:

```bash
E2E_BASE_URL=http://localhost:8081 corepack pnpm exec playwright test -c e2e/playwright.config.ts
```

查看 HTML 报告以外的失败痕迹:`e2e/.artifacts/` 内含失败截图与 trace
(`trace: retain-on-failure`),可用 `corepack pnpm exec playwright show-trace <zip>` 打开。

## 用例清单

| # | 文件 | 用例 | 覆盖路径 | 断言终点 |
|---|------|------|----------|----------|
| 1 | `auth.spec.ts` | 错误密码登录提示错误且不进入系统 | 登录 | 提示"用户名或密码错误",停留登录页 |
| 2 | `auth.spec.ts` | 登录成功进入仪表盘并显示当前用户 | 登录 | 跳转 `/dashboard`,顶栏出现用户名 |
| 3 | `auth.spec.ts` | 退出登录返回登录页 | 登出 | 头像下拉退出后回到 `/login` |
| 4 | `generation.spec.ts` | 路径1 独立生图:图像创作编辑器填写提示词后生成按钮可点 | `/generate/images` → 编辑器 | 生成按钮 enabled,**不点击** |
| 5 | `generation.spec.ts` | 路径2 参考生图:直达编辑器并选中参考图生图模型 | `/generate/image?modelId=15` | 编辑器加载、模型已选中 |
| 6 | `generation.spec.ts` | 路径5 高清处理入口:生视频工作台(万能导演台)加载 | `/generate/videos` | 页面与提示词输入加载 |
| 7 | `project-flows.spec.ts` | 路径3 图生视频生产:分镜页打开镜头生产抽屉 | 项目概览 → 分镜页 | 生产抽屉打开、按钮可见,**不启动** |
| 8 | `project-flows.spec.ts` | 路径4 对白片段/自动分块:原文页转剧本面板完整 | 项目"写 · 原文"页 | 面板与表单完整,**不启动解析** |
| 9 | `project-flows.spec.ts` | 路径6 项目成片:项目概览进入分镜页可见分集合成入口 | 项目概览 → 分镜页 | 合成入口可见,**不点击** |
| 10 | `permission.spec.ts` | uitest 项目列表不包含 zhangyz 的项目 | 权限对照 | UI 列表只含自己的项目 |
| 11 | `permission.spec.ts` | uitest 通过 API 无法读取 zhangyz 的项目详情 | 权限对照 | API 不返回他属主项目数据 |

## 实现说明

- `playwright.config.ts`:单 worker 串行执行,失败重试 1 次,失败保留 trace/截图,
  viewport 1728x960(覆盖 2xl 断点,保证分镜/剧本页三栏布局)。
- 登录态:`helpers/auth.ts` 通过 `/api/auth/login` + `/api/auth/user-info` 获取会话,
  在导航**前**写入 `auth-token` cookie(proxy 中间件在请求阶段校验)并用
  `addInitScript` 注入 `localStorage["auth-storage"]`(zustand persist)。
- 数据发现:`helpers/discovery.ts` 通过只读 API 遍历项目,找到"有分镜镜头"的真实项目,
  避免硬编码项目 ID;发现失败会显式报错,提示冒烟前置数据缺失。
- 用例只读:所有生成/解析/合成入口都断言到"可提交"或"入口可见"为止,不产生真实任务。

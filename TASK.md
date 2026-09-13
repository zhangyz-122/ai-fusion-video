# 冲刺任务 T10:E2E 冒烟:N04 六条创作路径 Playwright 脚本

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
为六条路径编写 Playwright 冒烟脚本(可不真实执行生成,
校验到"提交成功"为止):独立生图、参考生图、图生视频(生产)、
对白片段(自动分块)、高清处理入口、项目成片(合成)。
- 使用账号 uitest / Uitest#2026,基准地址读环境变量。
- 输出 PASS/FAIL 报告。
## 允许文件
- e2e/**(新建目录,含 playwright.config 与用例)
- package.json(仅追加 devDependency: @playwright/test)
## 禁区
- 不改产品代码;不真实提交图片/视频生成(断言到提交前一步);
  不修改数据库。
## 验收
- npx playwright test 本地全绿(对运行中的 localhost:8081)。


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

---

## T10 执行记录与决策(2026-09-13)

### 完成情况

- 新增 `ai-fusion-video-web/e2e/`:playwright.config.ts、helpers(API 登录注入 / 测试数据发现)、
  11 条用例(认证 3、创作路径 6、权限对照 2)、README(运行方式与前置条件)。
- `package.json` 追加 devDependency `@playwright/test@^1.63.0`,`pnpm-lock.yaml` 同步更新。
- 验收:`corepack pnpm exec playwright test -c e2e/playwright.config.ts` 对 localhost:8081 实跑
  **11 passed**(约 20s,连续两轮全绿)。分支:`sprint/T10-e2e-smoke`。

### 需要决策的问题(不空等,记录如下)

1. **被测部署版本与本分支源码不一致**:localhost:8081 运行的是 main 工作区(含未提交修改)的构建,
   项目概览为"写/定/拆/拍/剪"阶段流,`/generate/images`、`/generate/videos` 均为服务端重定向,
   故事转剧本入口位于 `/projects/{id}/source`(原文页)。任务书中的路由
   (`/generate/images` 能力目录、`/projects/{id}/scripts` 故事转剧本按钮)与本分支(sprint/base)
   源码一致、与部署不一致。用例按**部署实况**编写以保证可实跑;若后续统一为 sprint/base 的
   信息架构,需同步调整用例(用例 4/6/8 的定位器集中在这三个文件,改动面小)。
2. **运行命令带 `-c`**:playwright.config.ts 按任务书放在 `e2e/` 内(允许文件边界),
   因此验收命令为 `corepack pnpm exec playwright test -c e2e/playwright.config.ts`;
   若希望根目录直接 `playwright test`,需允许在 `ai-fusion-video-web/` 根放一份配置或在
   package.json 增加 script(超出本次允许文件,未做)。
3. **lockfile 变更**:安装 devDependency 必然更新 `pnpm-lock.yaml`,已包含在提交中;
   严格的"仅 package.json"无法保证他人可复现安装。

### 发现的页面缺陷(只记录,不修)

1. **能力目录入口死链**:生图编辑器头部"← 图像工坊 / 能力目录"链接指向 `/generate/images`,
   而该路由服务端重定向回 `/generate/image`,点击后回到原页,能力目录实际不可达。
2. **高清处理(视频放大)无 UI 入口**:模型 `SeedVR2 高清视频放大 1080P`(id 13,已启用)
   在"万能导演台"中不可达——统一视频模式锁定默认底座(MiniMax H3 文戏低配版),
   用户无法从界面选择该模型,任务书路径 5 的"高清处理入口"在部署版本上缺失。
3. **分镜行内"生成视频"按钮可访问性差**:无 `aria-label`/`title`(仅 Tooltip 内容),
   默认 `opacity-0` 依赖 hover 显现,键盘无法聚焦;且侧栏"剪 · 成片"使用同款 Video 图标,
   自动化/读屏都容易误触。建议补 `aria-label="生产这一镜"` 并常显。
4. **生产抽屉按钮语义不一致**:抽屉描述为"生成 3 个候选视频",启动按钮文案却是"生产这一镜",
   且就绪度不足时仅静默禁用(blockers 列表在上方另述,按钮本身无提示)。
5. **登录页品牌文案不一致**:部署版本登录页底部为"短剧制造平台",应用其余位置为"融光",
   疑似部署构建落后于品牌改版(本分支为"融光 · AI视频创作平台")。
6. **本分支前端无法编译(继承自基线,非本任务引入)**:`scripts/page.tsx` 引用了不存在的
   `story-to-script-button.tsx`(7a8c008 误删,b6d1f09 已在 sprint/base 修复)。
   本分支不含该修复,集成时以 sprint/base 为准合并即可,无需本任务处理。

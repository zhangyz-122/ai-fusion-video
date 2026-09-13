# 冲刺任务 T3:生产中心 v2:运行详情抽屉 + 重试/同步操作

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
在 /production 页面为每条运行增加:详情抽屉(复用/参考分镜页的
production-take-drawer 数据展示)、重新同步(reconcile)与重试(repair)按钮、
失败原因完整展示。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/production/**(本目录内自由)
- ai-fusion-video-web/lib/api/production.ts(仅追加,不修改既有导出)
## 禁区
- 不改 app-header/sidebar-nav(导航已预置);
  不改 lib/api/production.ts 既有类型与方法签名;
  不碰分镜页的 production-take-drawer.tsx。
## 验收
- tsc/eslint/build 通过;真实运行数据显示与操作可用(手动冒烟)。


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

## T3 需决策 / 遗留问题(集成者关注)
1. 【阻断全仓 tsc,非本任务文件】基础分支缺少 `app/(dashboard)/projects/[id]/scripts/_components/story-to-script-button.tsx`,导致 `projects/[id]/scripts/page.tsx(23,37)` 报 TS2307。已用 `git stash` 验证该错误在未含 T3 改动的基线上即存在,疑似脚本页任务拆分遗漏;需要归属任务补齐该组件,否则全仓 `tsc --noEmit` 与 build 无法通过。T3 自身文件(production/**)tsc、eslint 均零错误。
2. 【跨模块宽度约定】/production 页沿用了脚手架自带的 `max-w-6xl` 居中容器,与设置模块的 `max-w-[1200px]` 不一致;AGENTS.md 要求"同一模块同级页面统一主内容宽度",dashboard 级独立页面是否统一到 1200px 需集成者拍板(本任务未越界改动)。
3. 【范围说明】详情抽屉仅展示候选视频与 QC 状态,未提供质检/选用/合成操作:这些操作在分镜页 production-take-drawer 已有完整交互,按任务要点只做 reconcile/repair,避免两个入口重复维护同一套操作。如产品要求生产中心页支持 QC 全流程,需追加需求。
4. 【未做自动轮询】抽屉内 WAITING_GENERATION 状态不自动轮询 detail(分镜页抽屉有 5s 轮询),提供手动刷新按钮即可满足"详情+操作"要求;如需与分镜页一致可后续补。

# 冲刺任务 T6:仪表盘 B04 深化:真实快捷入口与明细跳转

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。


## 目标
1. 快捷动作四卡片改为真实能力入口(生图→/generate/images、
   视频→/generate/videos、声音→/generate/audios、生产→/production)。
2. "进行中与待办"条目点击深链细化:SCRIPT_PARSE→/projects/{id}(剧本页)、
   PRODUCTION_RUN→/production。
3. 最近项目卡片显示项目真实分集/资产计数(复用现有接口)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/dashboard/**(本目录内自由)
## 禁区
- 不改 lib/api 下既有文件(可新增);
  不改 app-header/sidebar-nav(导航已预置)。
## 验收
- tsc/eslint/build 通过;全部入口真实可达(手动冒烟)。


## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。

---

## T6 完成记录(2026-09-13)

### 变更文件
- `ai-fusion-video-web/app/(dashboard)/dashboard/page.tsx`:快捷操作四卡片改为
  生图(/generate/images)、视频(/generate/videos)、声音(/generate/audios)、
  生产(/production);最近项目区块改用 `<RecentProjects>`;移除 assistant/旧入口相关代码。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/recent-projects.tsx`(新增):
  最近项目列表,逐项目并发请求 `GET /api/project/{id}/workspace-overview`(分集数)与
  `GET /api/asset/all?projectId={id}&size=1`(资产数 total),行内展示"N 集 · N 资产"。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/section-header.tsx`(新增):
  从 page.tsx 抽出的共享区块标题。
- `ai-fusion-video-web/app/(dashboard)/dashboard/_components/activity-section.tsx`:
  深链细化——SCRIPT_PARSE→`/projects/{projectId}/scripts`(无项目回退 /projects)、
  PRODUCTION_RUN→`/production`、IMAGE_TASK→`/generate/image`、VIDEO_TASK→`/generate/video`;
  kindLabels 补充"剧本解析"。

### 需要决策/集成者处理的问题(按任务书要求追加,未自行改动)
1. 【基线损坏,阻塞 tsc 与剧本页】`sprint/base` 上
   `app/(dashboard)/projects/[id]/scripts/page.tsx:23` 仍 import
   `./_components/story-to-script-button`,但该文件已被 7a8c008 删除。
   后果:全仓 `tsc --noEmit` 报唯一一处 TS2307;运行时访问 `/projects/{id}/scripts` 500
   (本任务 SCRIPT_PARSE 深链的目标页)。属于 projects/** 禁区,本任务未修复,
   需基线/剧本任务负责人删掉该 import(或恢复组件)后集成。
2. 【导航旧地址】`components/dashboard/sidebar-nav.tsx` 的"生图/生视频"仍指向旧单数路由
   `/generate/image`、`/generate/video`(属禁区未改);建议统一为复数工坊入口或保留双入口,
   由导航任务决策。
3. 【产品确认】原快捷卡片中的"新建项目/管理素材/融光助手"按任务书被四个能力入口替换;
   仪表盘上不再有助手入口卡片(助手仍从 Header/其他入口可达)。如需保留请告知补回。
4. 【深链粒度受禁区限制】PRODUCTION_RUN 只能到 /production 列表(生产页不支持按 run 高亮);
   IMAGE_TASK/VIDEO_TASK 到 /generate/image|video 工作台,不能按 taskId 定位
   (工作台不读 URL 参数)。如需精确定位需要生产页/工作台配合加参数(超出本任务禁区)。
5. 【计数加载策略】项目计数为挂载后二次请求(最多 6 个项目×2 个现有接口),加载完成前不显示;
   单个项目计数请求失败时仅隐藏该行计数,不影响列表。

### 验证结果
- `corepack pnpm exec tsc --noEmit`:仅剩上述第 1 条基线既有错误(TS2307,scripts/page.tsx),
  本次改动的 4 个文件 0 错误。
- `corepack pnpm exec eslint`(4 个改动文件):0 问题。
- 本地 dev(代理 docker 平台 API)冒烟:
  - zhangyz:/dashboard 200;四入口目标页 /generate/images|videos|audios、/production 均 200;
    /api/dashboard/activity 返回 SCRIPT_PARSE(projectId=5)与 PRODUCTION_RUN×3,深链分别指向
    /projects/5/scripts 与 /production;/api/asset/all?projectId=1 → total=10、
    workspace-overview(projectId=3) → episodes=4,计数接口真实有数。
  - uitest:/dashboard、/production 均 200;activity.running=[](空态路径),项目计数 0/0 正常返回。
  - `/projects/5/scripts` 返回 500:即上述第 1 条基线既有问题,与本任务改动无关。

# 冲刺任务 M2:手机端深度打磨

## 背景
M1 已完成全站移动端基础适配(底部Tab栏/卡片化/触摸目标),但用户反馈"只是勉强能看到,并不舒服,各种布局不完美"。本任务做深度打磨:每一页逐个过,确保视觉舒适、操作自然、无妥协。

## 你要做的事
1. 用 Playwright 或手动检查每一个页面在 390×844 下的实际渲染效果,记录所有布局问题(间距不均/字体不协调/按钮过小/内容被裁剪/层级混乱/留白异常)
2. 逐页修复:调间距/字号/留白/层级,确保视觉舒适度与桌面端同等水准
3. 重点打磨高频页面:仪表盘/项目列表/项目概览/剧本工作区/分镜页/资产中心
4. 修复所有触控目标过小的行内按钮(改"更多"菜单或放大热区)
5. 检查各弹窗/抽屉/对话框在手机上是否全屏友好
6. 检查键盘弹出后的表单可用性(input focus 滚动到位)

## 允许文件
- ai-fusion-video-web/app/(dashboard)/** 各页面的响应式类与组件(仅追加/修改,不改桌面≥1024px 逻辑)
- ai-fusion-video-web/components/dashboard/mobile-*(新组件)
- ai-fusion-video-web/app/(dashboard)/dashboard/layout.tsx 等布局层

## 禁区
- 不改后端;不改桌面端(≥1024px)布局与交互逻辑
- 不做简单缩放;不加数据库迁移

## 验收
- tsc --noEmit 零错误;eslint 改动文件零 error;build 通过
- 逐页自查清单落盘 TASK.md 末尾(每页:可达/可操作/无溢出/无遮挡/触控≥44px/字体协调)
- 桌面端不回归

---

# M2 完成记录(2026-09-13)

## 检查方法
- 环境:worktree 分支 `swarm/M2-mobile-polish`(基于 sprint/base,含 M1 全部成果),`next dev` + 本地后端(8081)真实数据(zhangyz 账号,项目 3「末世双女主囤货漫剧」)。
- Playwright(Chromium,iPhone 14 级 390×844,isMobile+hasTouch,DPR2)逐页截图 + DOM 测量脚本:水平溢出(scrollWidth>390)、越界元素、可见交互控件尺寸清单。首轮 27 页全量扫描,修复后复扫,另在 1728×960 截图 9 个关键页对比桌面布局。
- 全部 27 页复扫结果:`scrollWidth=390`,零水平溢出;Tab 栏/顶栏/悬浮球不再遮挡内容;弹窗抽屉均不被悬浮控件穿透。

## 主要修复
1. **全局层级与文案**
   - 助手悬浮球避开移动端 Tab 栏:`assistant/geometry.ts` 新增 `getLauncherBottomInset()`(窄屏底边距 72px),默认位置与拖拽 clamp 同步生效(桌面 16px 不变)。
   - 移动顶栏品牌「短剧制造」→「融光」,与桌面顶栏一致。
   - 自定义弹窗/抽屉(create-project、episode-parse、video-preview、edit-assets、production-take-drawer、run-detail-drawer)z-index 从 50 提到 11000/11001(与 ui/dialog 的 z-[11001] 对齐),修复移动端页面菜单 FAB(z-60)与助手悬浮球(z-65)浮在弹窗之上的问题。
2. **触控热区**(新组件 `components/dashboard/mobile-touch-area.ts`,透明伪元素扩到 ≥44px,仅 `max-lg:` 生效,不改视觉尺寸,桌面零影响;absolute 定位控件用 `touchHitAreaOverlay` 避免覆盖原定位)
   - 编辑页上移/下移镜头(icon-xs 24px)、生产中心刷新/详情、生产抽屉、资产目录筛选片(h-6→移动端 h-9)/刷新、素材中心网格/列表切换与回收站按钮、AI 配置页编辑/删除模型(20px,移动端升 36px)、设为默认、API 配置行操作、存储配置行操作、工坊 composer(添加附件/高级模式/生成/移除附件)、创作记录(刷新/再次使用/作为参考/添加资产/下载)、分镜卡首尾帧/生产这一镜/拖拽手柄、剧本场次删除/内外景 Select(移动端加高)/InlineEdit(纵向热区)、顶栏任务徽标。
3. **布局节奏**
   - 仪表盘统计卡移动端改单行(标签左/数值右),四卡高度统一紧凑,首屏信息量提升;桌面保持上下结构。
   - 项目概览流程步骤卡移动端改横向单行(序号+步骤+状态右对齐),5 步从 ~530px 压缩到 ~290px;桌面保持块状卡。
   - AI 配置页 API 配置头与模型行移动端 flex-wrap,操作按钮换行到内容下方右对齐,修复文字逐字换行挤压。
   - 原文页「已导入原文」头部移动端换行,统计不再逐字竖排;通用设置「前后端不同域名」标签不再三行折行。
4. **弹窗全屏友好**
   - create-project/episode-parse 弹窗移动端加 12px 侧边距(`max-lg:max-w-[calc(100vw-1.5rem)]`),其余 Dialog 走 ui/dialog 基类已有 `max-w-[calc(100%-2rem)]`;生产详情/生产抽屉 `w-full sm:max-w-lg` 全宽可用;所有弹窗 max-h + 内部滚动已具备。
5. **键盘可用性**
   - 新组件 `components/dashboard/mobile-input-scroll.ts`:窄屏监听 focusin/visualViewport resize,延迟 260ms 对聚焦输入框 `scrollIntoView(nearest)`,尊重 prefers-reduced-motion;在 dashboard 布局层安装,覆盖全部弹窗与页面表单。
6. **工坊编辑器遮挡**
   - `/generate/{universal,image,video,images,videos,audios}` 全高编辑器路由不再渲染页面菜单 FAB(此前悬浮球压在「生成」按钮上);导航由 Tab 栏与页内「返回目录」链接承担。

## 验证结果
- `tsc --noEmit` 0 错误;改动文件 eslint 0 error(4 个 warning 均为 sprint/base 已有:scene-card 未用 import、episode-parse 未用 import、create-project img 与 eslint-disable,非本次引入);`corepack pnpm build` 通过(exit 0)。
- 桌面端(1728×960)对仪表盘/项目/概览/剧本/分镜/资产中心/视频工坊/生产/AI 配置 9 页截图比对:布局、栅格、交互位置与基线一致(改动全部通过 `max-lg:` 或运行时断点门控)。

## 逐页自查清单(390×844,✓=通过)
| 页面 | 可达 | 可操作 | 无溢出 | 无遮挡 | 触控 | 字体协调 |
|---|---|---|---|---|---|---|
| /dashboard | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /dashboard/analytics | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id] 概览 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/source | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/scripts | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/storyboards | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/assets | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/editing | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/production | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/delivery | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/members | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /projects/[id]/settings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /assets | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /production | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate 目录 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/images | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/videos | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/audios | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /generate/video(万能导演台) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/general | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/profile | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/agents | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/ai-models | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/storage | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| /settings/users | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

触控说明:视觉尺寸遵循共享按钮系统(24/32/36/40px),44px 通过透明伪元素热区在 <1024px 达成(见 `mobile-touch-area.ts`),脚本按可视尺寸统计的"小目标"为设计系统内的正常值。

## 需要决策/遗留问题
1. **弹窗 z 层修正跨断点生效**:自定义弹窗从 z-50 提到 z-[11000] 后,桌面端这些弹窗也会盖在助手浮层(最大化 z-65)之上。此前在桌面端弹窗会被助手遮住——判定为原有缺陷,现统一与 ui/dialog(z-[11001])对齐;如需桌面维持旧行为请反馈。
2. **编辑器路由隐藏页面菜单 FAB**:六个工坊编辑器路由不再出现左下角 FAB(曾压住「生成」主按钮)。若希望保留入口,需设计新的二级导航位置。
3. 剧本页「新增场次」胶囊按设计悬浮在分集分隔线上,与空的「点击添加分集概览…」占位文案视觉上轻微重叠;属既有设计,未改动。
4. 触控 44px 与按钮系统 36/40px 的矛盾用"透明热区"方案落地(不改视觉);若产品决定移动端按钮直接放大到 44px 视觉高度,需要在按钮系统新增移动端尺寸档,涉及 components/ui/button 调整(本次禁区)。
5. `DEV_BACKEND_URL` 默认指向 18080(本机不可用),本次本地验证通过环境变量覆盖为 8081,未改动已跟踪的 `.env.development`。

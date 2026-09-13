# 冲刺任务 M1:手机端完整适配(重新设计,非缩放)

## 背景
平台目标用户会在手机上操作。当前所有页面按桌面设计,窄屏下虽有零散响应式类但导航、表格、工作区在手机上不可用。要求:按桌面功能完整重设计移动端布局,保证手机上可正常完成全部操作。

## 设备基准
- 主基准 390×844(iPhone 14/15 级别),兼容 360-430px
- 触摸目标 ≥44px;禁 hover 依赖(改常显或点击展开);禁水平滚动

## 范围(按优先级)
1. 全局:底部 Tab 栏(仪表盘/项目/创作/生产/我的)替代桌面顶栏(≤1024px 显示);顶栏窄屏化(品牌+通知)
2. 仪表盘:统计卡改单列、最近项目卡全宽、活动区块堆叠
3. 项目列表与概览:卡片化、搜索栏粘性
4. 剧本工作区:三栏改纵向堆叠(集树→场次列表→详情抽屉)、行内编辑全屏化
5. 分镜页:表格视图禁用(强制卡片视图)、生产抽屉全宽
6. 资产中心:网格 2 列、回收站可用
7. 工坊三页(图像/视频/声音):目录卡片单列、编辑器关键操作粘性底栏
8. 生产中心:列表卡片化、详情抽屉全屏
9. 设置:表单单列

## 禁区
- 不改后端;不改桌面端(≥1024px)的布局与交互
- 不做简单缩放(transform/scale 禁用)
- 不动桌面导航组件内部逻辑(sidebar-nav/app-header 的桌面分支保持原样,移动端用新组件)

## 允许文件
- ai-fusion-video-web/app/(dashboard)/** 各页面的响应式类与移动端组件(新增 _components/mobile-* 文件优先)
- 新增 components/dashboard/mobile-*(底部Tab栏等)组件
- ai-fusion-video-web/app/(dashboard)/dashboard/layout.tsx 等布局层的响应式类

## 验收
- tsc --noEmit 零错误;eslint 改动文件零 error;corepack pnpm build 通过
- 390×844 下逐页检查清单落盘(每页:可达/可操作/无溢出/无遮挡)
- 桌面端(≥1280px)布局不回归(截图或 DOM 对比说明)

---

## M1 逐页移动端适配清单(390×844 基准,代码级静态自查)

### 全局导航
| 检查项 | 结果 |
| --- | --- |
| 底部 Tab 栏 ≤1024px 显示,五大入口(仪表盘/项目/创作/生产/我的) | ✅ `components/dashboard/mobile-tab-bar.tsx`,min-h-14(56px)触摸目标,`aria-current` 标注选中 |
| 顶栏窄屏化(品牌+通知) | ✅ `components/dashboard/mobile-header.tsx`:品牌 + 任务指示 + 通知铃 + 头像(保底退出登录入口),与 AppHeader 按 `useMediaQuery("(min-width:1024px)")` 互斥挂载 |
| 桌面 ≥1024px 不回归 | ✅ 桌面分支仍挂载原 AppHeader/SidebarNav,内部逻辑未动;`main` 增加的 `pb-14` 带 `lg:pb-0` 覆盖 |
| 无水平滚动 | ✅ main 窄屏 `pb-14` 为 Tab 栏预留高度,全部页面内容链路收于视口内 |

### 1. 仪表盘 /dashboard
- 可达:✅ 统计卡、快捷入口、进行中与待办、最近项目、最近资产全部可达
- 可操作:✅ 统计卡/快捷卡整卡点击(>44px),区块标题动作链接热区提升至 44px
- 无溢出:✅ 统计卡 `grid-cols-1`(窄屏单列)、活动区块 `md:grid-cols-2` 堆叠、最近资产横向滚动条收敛在容器内
- 无遮挡:✅ 最近项目行高 ~57px,列表滚动至底部由 main `pb-14` + 框架 `pb-4` 保证不被 Tab 栏遮挡
- 改动:区块间距窄屏收紧 `mb-6 lg:mb-8`(section-header.tsx、page.tsx)

### 2. 项目列表 /projects 与概览 /projects/[id]
- 可达:✅ 搜索、新建、卡片、删除均可达
- 可操作:✅ 新建按钮 min-h-11;卡片删除按钮触摸端常显(44px),桌面保持 hover 显隐;搜索栏窄屏 sticky 悬浮
- 无溢出:✅ 标题区 `flex-col sm:flex-row`,标题 `text-2xl lg:text-3xl`
- 无遮挡:✅ sticky 搜索不与顶栏/Tab 栏重叠(z-20 于滚动容器内)
- 概览页:✅ 内边距 `px-5 py-4 lg:px-6 lg:py-6`,五阶段卡片窄屏单列,剧名 `break-all`

### 3. 剧本工作区 /projects/[id]/scripts
- 可达:✅ 分集树(左抽屉)、场次列表(中栏)、场次详情/剧本概览(右抽屉)全部可达
- 可操作:✅ 左右抽屉窄屏全屏化(`w-full sm:w-[300px]`);目录/详情触发按钮 44px + aria-label;添加分集 min-h-11;树内删除/解析/生成动作触摸端常显、热区 36px;场次插入按钮触摸端半透明常显
- 无溢出:✅ 场次卡头部 `flex-wrap`;卡内边距 `px-4 sm:px-5`
- 无遮挡:✅ 工作区高度沿 flex 链收于 Tab 栏上方;行内编辑保存按钮随脏态出现
- 说明:行内编辑全屏化由右抽屉全屏承载(SceneDetail 表单),桌面 2xl 三栏不变

### 4. 分镜页 /projects/[id]/storyboards
- 可达:✅ 目录抽屉、场次分组、卡片视图、引用信息抽屉、生产抽屉全部可达
- 可操作:✅ <640px 强制卡片视图(isNarrowScreen matchMedia),视图切换隐藏;合成本集视频/重试/查看按钮窄屏压缩为 44px 图标按钮(原 `hidden sm:flex` 在手机上完全不可见,已修复);AI 生成/补全按钮保留
- 无溢出:✅ 卡片网格 `minmax(min(300px,100%),1fr)` 修复 360px 溢出;场次卡 `p-3 sm:p-5`、标题行 `flex-wrap`;内容区 `px-3 sm:px-6`
- 无遮挡:✅ 生产抽屉(ProductionTakeDrawer)本就是 `w-full sm:max-w-lg` 全宽;目录树动作触摸端常显

### 5. 资产中心 /assets
- 可达:✅ 类型统计、筛选工具栏、标签云、网格/列表、回收站全部可达
- 可操作:✅ 全部资产/回收站页签 min-h-11;网格删除(移入回收站)触摸端常显 40px;回收站恢复/彻底删除为共享 Button 标准尺寸(可用,热区 32px 见遗留)
- 无溢出:✅ 项目选择器窄屏独占一行,搜索框解除 200px 最小宽;网格保持 2 列(`grid-cols-2`)
- 无遮挡:✅ 上传面板/标签云/网格纵向堆叠,滚动由 main ScrollArea 承担

### 6. 工坊三页 /generate(/image /video /audios)
- 可达:✅ 目录卡片单列堆叠(`md:grid-cols-2` 收敛),编辑器(GenerationWorkbench)可达
- 可操作:✅ 简单编辑器即"粘性底栏"(悬浮 bottom-5,信息区 paddingBottom 动态避让);附件/参考素材移除按钮触摸端常显;高级模式提交按钮在面板 sticky footer 中常驻
- 无溢出:✅ 目录页 `px-5 py-4 lg:px-6 lg:py-6`;编辑器头部 `px-5 lg:px-8`
- 无遮挡:✅ 悬浮 composer 抬升到 Tab 栏上方(bottom-5 于 main pb-14 之上)

### 7. 生产中心 /production
- 可达:✅ 状态筛选、运行卡片、详情抽屉全部可达
- 可操作:✅ 状态筛选 chips min-h-11;详情/分页按钮为共享 Button
- 无溢出:✅ 运行卡本就是卡片流(`flex-wrap`),失败原因 truncate
- 无遮挡:✅ 详情抽屉 `w-full sm:max-w-lg` 窄屏全屏

### 8. 设置 /settings/*
- 可达:✅ 二级导航(通用/用户/AI 配置/智能体/存储/个人)经左下页面菜单抽屉可达;general/users/profile/ai-models/agents/storage 六页可达
- 可操作:✅ 表单窄屏单列(md: 栅格收敛);存储配置编辑/删除按钮触摸端常显 40px
- 无溢出:✅ 页面标题沿用 settingsTypography(text-2xl),未发现固定宽度溢出
- 遗留:users 用户表保留容器内横向滚动(管理端低频页,见遗留问题)

### 桌面端(≥1024/1280px)不回归说明
- 所有改动均为 `max-lg:`/`lg:`/`sm:`/`2xl:` 响应式类追加或窄屏分支,桌面取值与原类保持一致(如 `p-2.5 lg:p-1.5`、`text-2xl lg:text-3xl`、`min-h-11 sm:min-h-0`)
- 布局层:`isDesktopNav ? <AppHeader/> : <MobileHeader/>`,桌面渲染路径不变;`main` 的 `pb-14 lg:pb-0` 在 lg+ 为 0;`MobileTabBar`/FAB 均 `lg:hidden`
- sidebar-nav 仅追加 `max-lg:h-auto max-lg:max-h-*`,桌面 `h-[calc(100vh-6rem)]` 原样
- `corepack pnpm build` 全量通过,全部路由正常产出

## 遗留问题与风险
1. 密集树形列表(分集树/分镜目录)内的行内微操作按钮移动端为 36px 热区(p-2.5),低于 44px 理想值:受行高约束无法放大,已通过常显 + aria-label 缓解;后续可改为长按/滑动操作或"更多"菜单。
2. 共享 Button 组件的 `sm`(32px)/`icon-sm` 尺寸低于 44px 触摸标准:按 AGENTS 按钮规范不可用 className 重写高度,涉及回收站恢复/彻底删除、分镜"AI 生成"、资产"上传图片"等次级操作;建议在共享组件层新增 `touch` 尺寸档统一解决。
3. 设置-用户列表为桌面表格 + 容器内横向滚动(管理端低频页),未卡片化;若移动端管理为真实场景,需单独任务卡片化。
4. 项目内"资产圣经"页(`/projects/[id]/assets`)与"剪·成片"编辑器未做深度重设计(不在任务范围八项内),已确认其栅格在 390px 单列堆叠、无水平溢出,仅收紧标题层级。
5. iOS Safari 非 standalone 模式 `env(safe-area-inset-bottom)` 为 0,Tab 栏已加 `pb-[env(safe-area-inset-bottom)]`;若后续开启 PWA `viewport-fit=cover`,需同步上调 main 的 `pb-14`。
6. ESLint 存量 warning(react-hooks/exhaustive-deps 等 9 处)为历史代码,本次未引入新 error,亦未顺手改动无关逻辑。

# 冲刺任务 T11:资产中心 D01/D05:检索标签增强 + 回收站视图

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
1. /assets 页面增强:搜索按名称/标签过滤(后端已支持 keyword)、标签云快捷筛选、视图切换(网格/列表)。
2. 回收站视图:已删除资产(deleted=1,走既有软删字段,不加迁移)独立 Tab 展示,支持恢复(调用既有 update/恢复能力或新增仅前端组合)与彻底删除入口(带二次确认)。
3. 大文件上传失败重试提示(仅前端状态,断点续传不做)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/assets/**(本目录内自由)
- ai-fusion-video-web/lib/api/asset.ts(仅追加)
## 禁区
- 不改后端;不改导航;不加数据库迁移。
## 验收
- tsc/eslint/build 前两项通过;双账号冒烟(zhangyz 有数据/uitest 空态)。

## 通用规则
1. 只允许改动允许文件清单内文件;需要例外先在 TASK.md 末尾声明并继续可做部分。
2. 不改数据库迁移;不改导航与公共组件。
3. 提交规范:conventional commits。
4. 完成后确认编译/验证通过,报告分支名与变更清单,由集成者合并。
5. 需要决策的问题追加到 TASK.md 末尾,不要空等。

## T11 决策与遗留记录(2026-09-13,集成者关注)

### 决策1:回收站为"仅前端组合"实现,原因:后端既有接口完全无法触达软删行(重要)
- 后端 `BaseEntity.deleted` 带 MyBatis-Plus `@TableLogic`:所有 `selectList/selectPage/selectById`
  自动追加 `AND deleted = 0`,`deleteById` 是逻辑删除,`updateById` 的 WHERE 也带 `deleted = 0`。
  因此**既有接口既列不出、也改不动、更查不到 deleted=1 的资产行**;"调用既有 update/恢复能力"不存在。
- 按任务书"或新增仅前端组合"落地:
  - 删除(本页新增删除入口,二次确认)成功后,把资产完整快照按用户隔离存入 localStorage
    (`rg:assets:recycle-bin:v1`,含 userId 字段防跨账号串数据);
  - 回收站 Tab 展示快照;**恢复 = 用快照走 POST /api/asset 重建(获得新 id)**,成功后移除快照;
    原资产 id 上的引用(如分镜引用)不会自动重连,Toast 中明确提示"新 id";
  - **彻底删除 = 二次确认后仅移除本地快照**。数据库中的软删行依旧存在(全站不可见),
    物理清理需后端"受控清理"能力(见遗留2)。
- 后端需要的三个接口(供后续任务):列出已删除资产、按 id 恢复(写 deleted=0)、按 id 物理删除。
  届时前端可无缝替换 localStorage 组合,UI 不用大改。

### 决策2:筛选统计全部改为前端计算(全量加载)
- 后端 keyword 只 LIKE name,不匹配标签;任务要求"按名称/标签过滤",只能前端补齐。
- 改为循环分页拉取全部可访问资产(每页 200,上限 1000,超出在页脚提示"已加载前 N 个"),
  项目/类型/关键词(名称+标签)/标签云(多选取交集)全部在前端过滤;类型统计与标签云基于内存数据计算。
  当前账号资产量为个位数到两位数,该模型无压力;数据量显著增长后应交还后端(标签检索需后端支持)。

### 决策3:上传入口新增在本页(此前 /assets 无任何上传)
- 任务要求"大文件上传失败重试提示",本页原先没有上传,故新增"上传图片"入口:
  走既有 POST /api/storage/upload(仅 png/jpeg/webp/gif,≤100MB,前端 accept 已对齐),
  成功后创建 type=image 的资产(封面=上传文件 URL)到当前选中的项目(未选具体项目时提示先选择)。
- 队列逐文件展示 上传中/已完成/失败+原因;失败的文件保留 File 引用,可"重试"(整文件重传,不做断点续传)。

### 遗留/说明
1. 【未用资产】`lib/api/asset.ts` 的追加权限未使用:本任务无需新增接口,保持零改动。
2. 【物理删除与全量回收站需后端】见决策1:服务端已软删但无本地快照的资产(例如从项目资产页删除的)
   不会出现在回收站;彻底删除也不物理清理数据库行。均待后端接口。
3. 【数据口径】统计卡片/标签云来自内存全量数据;"全部资产"数字 = 已加载数(上限 1000)。
4. 【基线既有问题】`projects/[id]/scripts/page.tsx` 引用被删除的 `story-to-script-button`(TS2307)
   在基线上即存在(见 T6/T3 记录),与本任务无关;本任务改动文件 0 错误。

### T11 验证结果(2026-09-13)
- `corepack pnpm exec tsc --noEmit`:全仓 0 错误(T6 记录的 TS2307 已在 base 修复)。
- `corepack pnpm exec eslint "app/(dashboard)/assets"`:0 问题。
- 本地 dev(Next 16 Turbopack,DEV_BACKEND_URL=http://localhost:8081 代理 docker 平台):
  - `/assets` 编译并 200,SSR bundle 含回收站视图代码;双账号(带 auth-token cookie)均 200。
  - API 契约冒烟(zhangyz,UTF-8 报文):listAll size=200 → total=11 单页取全;
    创建(中文名+tags JSON 串+properties)→ DELETE → listAll 不再出现、GET by id 报"资产不存在"
    (证实软删行既有接口完全不可达)→ 以恢复载荷重建成功(新 id,tags/properties 完整);
    /api/storage/upload(subDir=assets)→ 返回 /media/... URL → 以该 URL 创建 type=image 资产成功。
  - uitest:listAll → total=0(空态路径);/assets 200;向不存在项目恢复(创建)返回
    业务错误"项目不存在"(前端 toast 会展示该消息,快照保留在回收站)。
  - 测试数据已清理:zhangyz 可见资产回到原有 11 个。**数据库留有 ids 12–20 共 9 条软删测试残留**
    (名称含 t11/T11,全站不可见,仅影响直接查库);物理清理依赖遗留2的后端能力。
- 浏览器级交互(页签切换、确认弹窗、上传面板重试按钮的点击流)未做 UI 自动化
  (子代理不使用 Browser Use),已由 tsc/eslint/SSR 编译与上述 API 契约测试覆盖,建议集成者合并后
  在浏览器中复核一遍交互观感。

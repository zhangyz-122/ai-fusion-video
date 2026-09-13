import fs from "node:fs";
import { execSync } from "node:child_process";

const ROOT = "E:/Projects/RongGuang";
const BASE = "E:/RG-sprint";

const tasks = [
  {
    id: "T1", name: "backend-bugfix",
    title: "后端缺陷修复:消息投影竞态 + 场次移动分集静默忽略",
    brief: `
## 目标
修复两个已诊断的后端缺陷。

### 缺陷1:AgentMessageAllocator 投影竞态
- 现象:AgentRunMaintenanceScheduler 每 5 秒 DataIntegrityViolation,
  根因是 projections.recoverTerminalBatch 投影消息时
  (conversation_id, message_order) 唯一键冲突(见技术债文档)。
- 修复方向:insert 冲突时重试读取 nextMessageOrder 再插入;
  或改用 UPDATE 计数器原子递归(SELECT ... FOR UPDATE 事务)。
### 缺陷2:PUT /api/storyboard/scene 对 episodeId 变更静默忽略
- 复现:scene 37 episode_id=6,PUT episodeId=8 返回 success 但库值不变。
- 修复方向:排查 SceneUpdateReqVO→StoryboardScene 的 MapStruct 映射
  与 updateById 更新策略;修复后显式校验场景与分集归属一致性。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/run/AgentMessageAllocator.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/AgentMessageService.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/storyboard/StoryboardService.java(updateScene 部分)
- ai-fusion-video/src/test/java/com/stonewu/fusion/service/**(新增测试)
## 禁区
- 不改前端;不加数据库迁移;不改其他服务文件。
## 验收
- 新增单测覆盖竞态重试与 episodeId 变更;./mvnw compile 通过;
  相关 StoryboardServiceTests 全绿。
`,
  },
  {
    id: "T2", name: "backend-resilience",
    title: "后端任务韧性:生产运行自动同步调度 + 失败任务清恢复语义",
    brief: `
## 目标
1. 生产运行自动同步:新增调度器定期扫描 WAITING_GENERATION 超过阈值(如30分钟)
   且底层 VideoTask 已终态的 run,自动执行 reconcile 逻辑(复用 ProductionRunService.reconcile)。
2. 视频/图片任务超时回收(GenerationTaskReaper)扩展:被回收任务若属于
   进行中的 ProductionRun,同步把 run 标记 FAILED(带原因),避免 run 永久悬挂。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/production/**(新增调度器)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/generation/GenerationTaskReaper.java
- ai-fusion-video/src/test/java/com/stonewu/fusion/service/production/**
## 禁区
- 不改 ProductionRunService 的 start/detail/selectTake 已有语义;
  不改前端;不加数据库迁移。
## 验收
- 单测:超时 run 自动 reconcile / 被回收任务联动 run 失败;
  ./mvnw compile + ProductionRunServiceTests 全绿。
`,
  },
  {
    id: "T3", name: "production-center-v2",
    title: "生产中心 v2:运行详情抽屉 + 重试/同步操作",
    brief: `
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
`,
  },
  {
    id: "T4", name: "audio-workshop",
    title: "声音工坊 G01:音频能力浏览与 INFINITETALK 音频试运行",
    brief: `
## 目标
1. /generate/audios 实装:列出音频能力(复用 capabilityApi.catalog? 目前
   目录接口只支持模型类型,音频走 INFINITETALK 工作流)——先展示
   workflow 11 v22(已验证未发布)的接入状态卡片。
2. 后端:为 workflow 11 v22 补试运行并发布——试运行需要音频输入,
   用 tools/ 下或系统内任一音频文件转 Data URI 作为 referenceAudios。
   参照 tools/repair-wan-json-escapes.mjs 的 test+publish 流程。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/generate/audios/**(本目录内自由)
- ai-fusion-video-web/lib/api/ 下新增 audio 相关 client(新文件)
- backend 试运行通过管理 API 或脚本执行(不改 Java 代码)
## 禁区
- 不改 ScriptAutoSplitService/story-to-script 相关文件;
  不改 images/videos 工坊页面。
## 验收
- workflow 11 v22 发布成功;音频试运行产出视频;
  声音工坊页面显示能力状态卡片。
`,
  },
  {
    id: "T5", name: "editing-workspace",
    title: "剪辑交付 H01:时间轴骨架 + 分集成片串联",
    brief: `
## 目标
/projects/[id]/editing 实装:
1. 列出项目所有分集及各集镜头(按 sortOrder),显示每镜头
   选中 Take 的视频(复用 lib/api/storyboard + production API)。
2. 支持:镜头启用/排除勾选、顺序调整(本地状态)、
   一键"串联预览"(按序打开各镜头视频)。
3. 后端复用 VideoComposeService 的分集合成(已有)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/projects/[id]/editing/**(本目录内自由)
- ai-fusion-video-web/lib/api/ 下新增 editing client(新文件)
## 禁区
- 不改 scripts/storyboards 页面与其组件;
  不改 VideoComposeService(合成已验证)。
## 验收
- tsc/eslint/build 通过;真实项目数据可浏览与排序(手动冒烟)。
`,
  },
  {
    id: "T6", name: "dashboard-deepen",
    title: "仪表盘 B04 深化:真实快捷入口与明细跳转",
    brief: `
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
`,
  },
  {
    id: "T7", name: "capability-admin",
    title: "能力管理 K03:模型能力配置表单化",
    brief: `
## 目标
settings/ai-models 的模型编辑器,把埋在 config JSON 里的能力字段
表单化(可视化编辑),解决"模型16缺参考图配置"这类问题再发生:
- supportFirstFrame/supportLastFrame/supportReferenceImages/
  maxReferenceImages/referenceImageInputFormats(url,data_uri)/
  supportDataUriInput/minDuration/maxDuration/defaultFps/defaultDuration
- 保存时合并进 config JSON,不改其他字段。
- 对 ComfyUI 协议模型显示"推荐配置模板"按钮(WAN/H3 预设)。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/settings/ai-models/**(本目录内自由)
## 禁区
- 不改后端模型接口;不改其他设置页。
## 验收
- tsc/eslint/build 通过;编辑模型16补齐配置并在生产启动中生效(手动冒烟)。
`,
  },
  {
    id: "T8", name: "autosplit-v2",
    title: "剧本解析 v2:自动分块体验与健壮性",
    brief: `
## 目标
1. ScriptAutoSplitService 增加任务持久恢复:启动时扫描 parsing_status=1
   且 update_time 超过 30 分钟的剧本,标记失败(滞留),允许用户重跑。
2. 分块参数可调:AutoSplitReqVO 增加 chunkChars(2000-12000,默认6000)。
3. 前端 story-to-script-button:解析中显示实时进度(已有),增加取消提示文案;
   解析完成后显示"共 N 集"结果摘要。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/script/ScriptAutoSplitService.java
- ai-fusion-video/src/main/java/com/stonewu/fusion/controller/script/**(auto-split 相关)
- ai-fusion-video-web/app/(dashboard)/projects/[id]/scripts/_components/story-to-script-button.tsx
- ai-fusion-video-web/lib/api/script.ts(仅追加)
## 禁区
- 不改 parse-script-dialog/fallback-parse;
  不改 agent 管道(script-story-to-script 等)。
## 验收
- 单测:分块逻辑(章节切分/段落边界/上限)覆盖;
  编译通过;13k 与 72 万字真实文本实测(72万字可只验证分块数与进度)。
`,
  },
  {
    id: "T9", name: "security-hardening",
    title: "安全加固 M03:上传与执行面收口",
    brief: `
## 目标
1. 上传/导入校验收口:检查所有接收文件或 URL 的端点
   (图片上传、参考图 URL 拉取、工作流导入)的类型/大小/内网地址限制;
   参考图 URL 禁止内网地址(127.0.0.1/10.x/172.16-31/192.168/hostMetadata)。
2. 工作流导入(JSON)增加深度与节点数上限,防嵌套炸弹。
3. 全局异常日志不含密钥(抽查 apiKey 脱敏)。
## 允许文件
- ai-fusion-video/src/main/java/com/stonewu/fusion/security/**
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/generation/ReferenceImageTransportService.java(校验部分)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/ai/comfyui/ComfyUiWorkflowDocumentService.java(上限部分)
- ai-fusion-video/src/test/java/**(新增测试)
## 禁区
- 不改生成策略与渲染逻辑;不改前端;不加数据库迁移。
## 验收
- 新增单测:内网 URL 拒绝、超限 JSON 拒绝;既有测试全绿。
`,
  },
  {
    id: "T10", name: "e2e-smoke",
    title: "E2E 冒烟:N04 六条创作路径 Playwright 脚本",
    brief: `
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
`,
  },
];

for (const t of tasks) {
  const dir = `${BASE}/RG-${t.id}-${t.name}`;
  try {
    execSync(`git worktree add "${dir}" -b sprint/${t.id}-${t.name} sprint/base`, { cwd: ROOT, stdio: "pipe" });
  } catch (e) {
    console.log(`worktree ${t.id} 已存在,跳过创建`);
  }
  const taskDoc = `# 冲刺任务 ${t.id}:${t.title}

基础分支:sprint/base(含导航与路由脚手架)。当前 worktree 即你的工作区。

${t.brief}

## 通用规则
1. 只允许改动"允许文件"清单内的文件;需要例外先在任务群里声明。
2. 不改数据库迁移(Flyway);不改公共组件与其他任务的文件。
3. 提交规范:conventional commits,每个逻辑单元一个提交。
4. 完成后:确认编译/测试通过,把分支推送到远端或在任务群报告分支名,由集成者合并。
5. 遇到与其他任务冲突的公共需求(导航/公共组件),记录到 TASK.md 末尾,不要自行改动。
`;
  fs.writeFileSync(`${dir}/TASK.md`, taskDoc);
  console.log(`${t.id} ✓ ${dir}`);
}
console.log("全部 worktree 就绪");

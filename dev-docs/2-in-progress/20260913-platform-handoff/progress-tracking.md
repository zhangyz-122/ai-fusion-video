# 当前进度

2026-09-13：项目迁入 E:\Projects\RongGuang。复制后原始文件 SHA256 比对零差异，Git HEAD 和未提交状态保持一致。排除可重建的 node_modules、.next、target；本地前端依赖需要重新安装，Docker 现有镜像可继续使用。

已有：Production 后端/Drawer、QC、修复执行器，以及 WAN 短片运行证据。历史记录描述 Run #4/#5 单镜头闭环已完成。

未完成：前端模型/Profile选择与视频预览；WAN 经平台的完整生产验收；Golden runner 与 12 镜头实跑；Redis 重启和 Legacy/Mixed 回归。

本次迁移没有新增生成任务，没有修改现有业务数据。旧目录暂作备份，后续仅在 E 盘开发。

2026-09-13：迁移后环境可复现性验证通过。

- 未提交改动已按功能分组提交为 7 个 commit（363b54f..4d47215，179 个文件，+20466/-512），工作树干净；`.env` 保持未跟踪，未 push 远端。
- 工具链定位：JDK 21 = `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`（mvnw 需显式 `JAVA_HOME`）；Node 24 + corepack pnpm@10.32.1；Maven 本地仓库缓存 273M 完好。
- 后端 `./mvnw compile` 通过（19s）；选定测试集 71/71 通过（Flyway 命名、Production Run/Repair/QC、WorkflowProfile、ComfyUI 策略/渲染、Consumer、Storyboard/Compose、Agent 工具）。
- 前端 `corepack pnpm install`（2m3s）与 `corepack pnpm build` 生产构建（含 TypeScript）通过。
- Docker 栈全健康：mysql/redis/backend(healthy)/frontend/nginx，页面 http://localhost:8081 可用。
- 遗留：宿主机无法直连容器内 MySQL（compose 未暴露端口），Flyway validate 只能经 backend 容器间接印证；中间提交不保证可独立构建，`4d47215` 为完整状态。

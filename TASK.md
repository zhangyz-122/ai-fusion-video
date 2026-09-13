# 冲刺任务 T12:系统状态页 M05:真实健康/存储/队列数据

基础分支:sprint/base(含全部已合并成果)。当前 worktree 即你的工作区。

## 目标
settings/general 增补'系统状态'区块,全部真实数据:
1. 健康检查:后端 /actuator/health 透传端点(新增,仅追加)。
2. 存储占用:媒体目录大小统计(新增只读端点,后端遍历 /app/data/media 统计)。
3. 队列深度:Redis 视频队列长度(经既有 RedisTaskQueue 读取,新增只读端点)。
4. 前端区块展示 + 30s 轮询。
## 允许文件
- ai-fusion-video-web/app/(dashboard)/settings/general/**(本目录内自由)
- ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/**(仅追加端点)
- ai-fusion-video/src/main/java/com/stonewu/fusion/service/system/**(新增服务,仅追加)
## 禁区
- 不改既有端点与安全配置;不加迁移;不改其他设置页。
## 验收
- 编译通过;真实数据展示(对照 docker 与宿主实际值);uitest 视角不泄露敏感信息。

## 通用规则
1. 只允许改动允许文件清单内文件;需要例外先在 TASK.md 末尾声明并继续可做部分。
2. 不改数据库迁移;不改导航与公共组件。
3. 提交规范:conventional commits。
4. 完成后确认编译/验证通过,报告分支名与变更清单,由集成者合并。
5. 需要决策的问题追加到 TASK.md 末尾,不要空等。

---

## T12 执行记录与决策(2026-09-13)

### 变更文件
- 后端新增(全部为追加,未改既有代码):
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/SystemStatusController.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/service/system/SystemStatusService.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/SystemHealthRespVO.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/MediaStorageStatsRespVO.java`
  - `ai-fusion-video/src/main/java/com/stonewu/fusion/controller/system/vo/VideoQueueStatusRespVO.java`
- 后端测试(例外声明见下):
  - `ai-fusion-video/src/test/java/com/stonewu/fusion/service/system/SystemStatusServiceTests.java`
- 前端(`settings/general/` 目录内):
  - `ai-fusion-video-web/app/(dashboard)/settings/general/_components/system-status-api.ts`(类型/请求/格式化)
  - `ai-fusion-video-web/app/(dashboard)/settings/general/_components/system-status-section.tsx`(状态区块 + 30s 轮询)
  - `ai-fusion-video-web/app/(dashboard)/settings/general/page.tsx`(仅追加 import 与 isAdmin 挂载)

### 决策与例外声明
1. 健康检查未做 /actuator/health 透传:`pom.xml` 不在允许文件清单,无法追加 `spring-boot-starter-actuator`(SecurityConfig 虽放行 /actuator/** 但依赖缺失该路径本就 404)。改为 `SystemStatusService` 内原生探测:数据库用独立 JdbcTemplate 执行 `SELECT 1`(3s queryTimeout),后端状态由端点可响应自证。如集成阶段要求真 actuator,需补依赖后替换 getHealth() 实现。
2. 测试文件例外:允许清单未含 `src/test/**`,按验收"编译通过(+单测如可行)"新增 `SystemStatusServiceTests`,不改任何既有测试。
3. `SystemStatusService` 不使用 `@Cacheable`:状态页 30 秒轮询必须返回实时探测结果,缓存会使健康/队列数据失真;本服务只读、无 create/update/delete,不涉及缓存失效语义。
4. 管理员实测数据受限:任务禁止 docker/compose,运行中的 8081 平台为旧镜像(新端点 404 已实测确认);本地后端启动依赖 MySQL 43306 / Redis 46379,宿主未监听且不允许经 docker 暴露。已完成:后端编译 + 7 个单测、前端 tsc/eslint、next dev SSR 冒烟、8081 登录链路验证。**集成者合并重建镜像后需补一次实测**:①health 的 database=UP;②storage 三分类文件数/字节与容器内 `find /app/data/media/{images,videos} -type f` 对比(composed 含在 videos 下,注意口径);③队列深度与 `redis-cli LLEN fv:taskqueue:video_generation:model:*` 对比。
5. 存储统计口径:与 WebMvcConfig 的 /media/** 映射一致——优先取数据库默认"本地存储"配置的 basePath,否则回退 `app.storage.local-base-path`;images 递归(含 images/video-frames);composed=videos/composed 递归;videos=videos 递归但排除 composed,避免重复计数;默认存储为 S3 时返回 storageType=s3 并照常统计本地目录。basePath 仅出现在管理员专用端点中,前端区块仅管理员可见,非管理员视角无泄露。
6. 队列口径:`listRegisteredQueuesByPrefix("video_generation")` 读取全部已注册视频队列(video_generation 与 video_generation:model:*),逐队列只读 LLEN/并发计数/最大并发;Redis 不可用时降级为 available=false + 通用文案,不暴露异常细节与内部地址。

### 验证结果
- 后端:`./mvnw compile` 通过;`./mvnw test -Dtest=SystemStatusServiceTests` → Tests run: 7, Failures: 0, Errors: 0。
- 前端:`tsc --noEmit` 通过;eslint(page.tsx / system-status-section.tsx / system-status-api.ts)0 错误 0 警告;`next dev` 访问 /settings/general 返回 200、SSR 无报错。
- 平台 8081:zhangyz 登录成功;`/api/system/status/{health,storage,video-queue}` 当前均 404(旧镜像,待集成重建后生效)。

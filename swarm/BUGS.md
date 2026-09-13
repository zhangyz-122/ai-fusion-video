# BUGS
- [已修] AgentMessageAllocator 竞态(主矛盾,T1);updateScene episodeId 静默忽略(T1)
- [已修] 模型16缺参考图配置(T7表单化+实测);WAN JSON 非法转义(修复脚本);输出绑定单元素数组(修复脚本)
- [开放]投影幂等化未彻底(SW-T04);ProjectServiceTests 存量失败(SW-T10);僵尸会话(SW-T09)
- [开放]T10 六条UI缺陷(SW-T02);场次对白 dialogues=null 口径(对白在描述内,T14兼容,口径待决策)

## SW-T06 新增(2026-09-14,QA 实测,全部可复现,脚本见 swarm/qa/)

### SW-T06-01|P2|Redis 停机时认证层整体失效,系统状态页的 Redis 降级展示实际不可达
- 现象:`docker stop fusion-redis` 后,所有携带合法 token 的请求(含 /api/system/status/{health,storage,video-queue})均返回 401"未登录或登录已过期";登录接口返回 500"系统内部错误"。恢复 Redis 后一切正常(available=true,降级逻辑本身正确)。
- 触发:会话存储在 Redis(TokenService 基于 StringRedisTemplate);token 校验读 Redis 失败 → 401;登录写 Redis 失败 → 500。
- 影响:代码里 video-queue 的优雅降级(available=false + 通用文案)在真实 Redis 故障中永远无法被管理员看到——状态页先被 401 登出,重新登录又 500;Redis 故障在所有人视角表现为"平台挂了",运维定位成本高。
- 建议:①登录与 token 校验路径捕获 Redis 连接异常,登录返回 503 + 明确文案;②评估 token 校验对 Redis 读失败的短时降级(需安全评估);③降级展示可达性依赖①,需 Supervisor/Architect 决策。

### SW-T06-02|P3|越权与资源不存在返回 HTTP 500 而非 403/404(ProjectAccessGuard 全局模式)
- 现象:uitest 访问 zhangyz 的分集 SRT(subtitle.srt)或生产运行详情(/api/production/runs/5)→ HTTP 500 {"code":500,"msg":"无权访问该项目内容"};访问不存在的分集 → HTTP 500 "剧本分集不存在: 999999"。
- 触发:ProjectAccessGuard/getById 校验抛 BusinessException(默认 code=500),GlobalExceptionHandler 按 code 映射 HTTP 状态。
- 影响:权限拒绝、资源不存在混入 5xx,告警噪音大、语义错误;正常越权请求不应产生服务端错误。
- 建议:权限类用 code=403、不存在用 code=404(或为防枚举统一 404),响应文案不变,前端无感。

### SW-T06-03|P3|查询参数类型不匹配返回 500 而非 400
- 现象:GET /api/script/episode/{id}/subtitle.srt?secondsPerLine=abc → HTTP 500"系统内部错误"。
- 触发:MethodArgumentTypeMismatchException 无专用 @ExceptionHandler,落入通用 500。
- 影响:客户端参数错误被记为服务端错误,监控误报。
- 建议:GlobalExceptionHandler 增加 MethodArgumentTypeMismatchException → 400。

### SW-T06-04|P4|观察项:分块聚合块长可超 chunkChars 2 字符(仅尾部空白,无内容丢失)
- 现象:splitIntoChunks 硬切时,恰好命中 target 的单片段块长度 = chunkChars+2(尾部 "\n\n");与 javadoc"任何块都不超过 chunkChars"有 2 字符偏差。
- 影响:无实际内容丢失——convertChunk 的 inputLimit 截断恰好只削去尾部空白分隔符;仅文档口径问题。
- 建议:聚合阈值改 target-2 或修 javadoc;已用特征化断言记录于 AutoSplitChunkCharsBoundaryTests。

### SW-T06 观察项(不计缺陷,实测取证)
- 生产列表 pageNo=0 被钳制为第 1 页(HTTP 200 内容一致);pageNo=999 → 空列表且 total 保留;pageSize=0/-1 → 空列表无 5xx;pageSize=1000 无上限(当前 total=5 无风险,数据增长后建议 @Min/@Max 或 VO 层校验)。
- status 过滤大小写不敏感(MySQL collation,'selected' 命中 'SELECTED'),前端只发大写,无实际危害。
- 存储统计三分类加和与 totalFiles/totalBytes 完全一致(97=43+51+3),口径正确。

## SW-T06 Round2 新增(2026-09-14,集成版回归实测,脚本见 swarm/qa/)

### 回归确认(Round 1 缺陷修复验证)
- SW-T06-02 已修复 ✓:越权→403(项目/分集/SRT/生产运行/回收站恢复),不存在→404(分集/SRT/生产运行/资产)。
- SW-T06-03 已修复 ✓:query/path 参数类型错误→400(secondsPerLine=abc、episode/abc、runs/xyz)。
- 字幕导出回归 11/11 全绿(真实分集 16 cues 序号/时间轴/时长/内容溯源;空对白 400;越界 400)。
- E2E 11/11 全绿(适配版 b627420 对集成部署实跑,17.7s)。
- 回收站全链路 13/13 全绿(建→删→回收站可见→恢复保原 id→项目内可见→物理删;uitest 恢复/彻底删除他人资产均 403,回收站列表按可访问项目隔离,uitest total=0;分页 size=1 生效)。

### SW-T06-R2-01|P1|生产滞留运行调度器每分钟失败:MP in() 传入 Iterable 被绑定为单参数
- 现象:后端日志每分钟 WARN "[ProductionScheduler] 滞留运行扫描失败"(实测 15/15 分钟全失败)。SQL `SELECT id,status FROM afv_video_task WHERE deleted=false AND (id IN (?))` 以单参数绑定 `HashMap$Values` → TypeException/NotSerializableException。
- 触发:`ProductionRunReconcileScheduler.loadTaskStatuses(Iterable<Long> videoTaskIds)` 调用 `.in(VideoTask::getId, videoTaskIds)`;MyBatis-Plus 的 in() 只接受 Collection,Iterable 被当作单个值。只要存在滞留运行即必然触发(当前平台持续存在)。
- 影响:滞留运行自动同步(30min)完全失效,调度形同虚设;每分钟一条错误日志。
- 建议:`loadTaskStatuses` 入参改 `Collection<Long>` 或调用处 `new ArrayList<>(videoTaskIds)`(一行修复)。

### SW-T06-R2-02|P2|文本模型不可达时自动分块永久挂起:无读超时 + 滞留恢复被内存表跳过 + 2 线程池可被占死
- 现象:默认文本模型为 Ollama qwen2.5:14b(当前不可达,半开挂起而非快速拒绝)。脚本 5 解析卡在 "正在解析分块 69/170" 超 40 分钟(script.update_time=06:07:44 后不再变化);另实测默认模型任务 10 分钟无终态。滞留恢复调度(5 分钟)因 runningScriptIds 含该剧本而跳过——线程挂死但活着,恢复永远不触发。
- 影响:用户视角解析永远停格无失败提示;自动分块线程池仅 2 线程,两次挂起即令全部后续解析排队饿死;与 SW-T09 僵尸会话同根源。
- 建议:①模型调用链设置读超时(如 120s);②滞留恢复对超阈值无进度任务即使线程在跑也标记失败/中断(任务级看门狗);③默认文本模型改指向可用模型。实测期间全量 170 集完整性抽查被该挂起任务阻塞,已用"已写分集=原文无丢字前缀"替代校验(PASS),Round 1 已对稳定 170 集数据集全量验证过。

### SW-T06-R2-03|P3|B2 语义修复遗漏三处,客户端错误仍返回 500
- 现象(异常矩阵实测 13/18):① GET /api/project/999999 → 500"项目不存在: 999999"(管理员与非管理员一致;根因 ProjectService.getById 默认 code=500,canAccessProject(Long,Long) 内部同样命中);② PUT /api/asset/recycle-bin/999999/restore、③ DELETE /api/asset/recycle-bin/999999 → 500"回收站中不存在该资产: 999999"(AssetService.getDeletedById 默认 code)。
- 影响:与 B2 已统一的 403/404 语义不一致;5xx 误报。
- 建议:getById/getDeletedById 改 `BusinessException(404, msg)`,文案不变。

### SW-T06-R2-04|P3|JSON 请求体类型错误/不可读 → 500,应 400
- 现象:POST /api/script/{id}/auto-split,body `{"chunkChars":"abc"}` → 500"系统内部错误"(异常未启动任务)。B2 只覆盖了 query/path 的 MethodArgumentTypeMismatchException,Body 的 HttpMessageNotReadableException 未映射。
- 建议:GlobalExceptionHandler 增加 HttpMessageNotReadableException → 400。

### SW-T06-R2-05|P4|HTTP 方法不支持 → 500 + ERROR 堆栈,应 405
- 现象:以 GET 访问 PUT 端点(/api/asset/recycle-bin/21/restore)、以 POST 访问 GET 端点(/api/asset/recycle-bin)→ 均 500"系统内部错误",后端打整段 HttpRequestMethodNotSupportedException 的 ERROR 级堆栈。
- 建议:映射该方法异常 → 405,日志降为 WARN。

### SW-T06 Round2 观察项(不计缺陷)
- 解析进行中删除项目:后台任务继续对已删剧本写分集(实测删除临时项目 9 时其挂起任务仍在运行),与 SW-T09 僵尸会话同类。
- B1 开发遗留数据:回收站中存在软删资产 id=21 "t05-recycle-fullchain-test"(zhangyz 项目5),建议开发清理。
- 并发解析期间分集列表呈"擦除-重写"中间态(实测 68/170,已验证为原文无丢字前缀且编号连续),期间 totalEpisodes 仍显示 170,前端可读到中间态;可接受但集成者应知悉。

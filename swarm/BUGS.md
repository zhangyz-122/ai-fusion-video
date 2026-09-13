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

# 技术债与已知限制

## 当前限制

- 已安装并验证 Temurin JDK 21；当前 Maven 编译与关键单测通过，但未执行数据库级 Flyway validate。
- 当前 ComfyUI 服务未加载共享目录中的 Wan 模型，因此三条 Wan Workflow 仍处于真实预检阻断状态。
- `ProductionStep` 已保存 `executionRef` 及 WorkflowProfile/WorkflowVersion 快照；RuntimeRevision 和 ModelStack 快照仍待补齐。
- 幂等键依赖数据库唯一索引；并发冲突的友好重试响应需要在接入统一异常处理后补齐。

## 兼容性约束

- 不创建第二套视频队列。
- 不修改或删除 Legacy `generatedVideoUrl`。
- 不在 ProductionTake 上增加第二个 selected 真相。
- 不对 FLF、InfiniteTalk 或 Wan 模型做静默降级。

# 开发规范与契约

技术栈基线见 [`tech-stack.md`](tech-stack.md)。已确认的目标架构决策索引见 [`../decisions/README.md`](../decisions/README.md)，其中 Accepted 表示目标决策已确认，不表示代码已实现；开放项仍以各 ADR 的 PENDING 列表为准。

当前 API 实现事实来源包括后端 Controller/DTO 和前端 `src/api/registry.ts`；修改接口时同步检查前后端调用、错误处理和状态标记。ADR-010 的统一 envelope、traceId、业务异常、安全和限流均为目标要求，当前接口尚未实现这些规范。不能将 registry 的待开发项描述为已上线接口。

架构状态约束：Document 表示业务资源生命周期、DocumentVersion 表示特定内容/索引产物、ProcessingTask 表示执行尝试；具体字段/状态枚举仍 PENDING。向量写入/检索、Chat SSE、异步文件处理等当前实现边界见 [`../architecture/README.md`](../architecture/README.md)。

本地命令和联调方式分别见 `frontend/README.md`、`spring-ai-qdrant-rag/README.md`。新增开发规范时记录适用范围及依据。

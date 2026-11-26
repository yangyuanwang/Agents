# 需求状态与架构基线

本文区分 Accepted 目标决策、当前已实现能力和 PENDING 事项。13 项决策索引见 [`../decisions/README.md`](../decisions/README.md)，当前架构与实际代码边界见 [`../architecture/README.md`](../architecture/README.md)。

## 已确认的目标架构需求（尚不表示已实现）

- 用户是全局身份，可加入多个租户；TenantMember 持有 OWNER/ADMIN/MEMBER 角色。
- 使用 Spring Security + JWT；租户和知识库权限由服务端根据可信身份及数据关系校验，不信任客户端 tenantId/userId。
- OWNER/ADMIN 管理所属租户 KB；MEMBER 通过 KnowledgeBaseMember 显式授权。KB 具体角色矩阵未定。
- 同 Embedding Profile 的多个租户/KB 共用 Qdrant Collection，强制 Payload Filter；不同向量空间不混用，Collection 按 Profile 管理。
- 首版本地文件系统、存储接口抽象、租户内 SHA-256 去重、逻辑删除和异步物理清理；支持 PDF、DOCX、TXT、Markdown，不向 API 暴露本地路径。
- MySQL 保存完整 Chunk 文本；使用 DocumentVersion，新版完成并验证后切换；Chunk ID 与 Qdrant Point ID 相同，Point ID 应使用 Qdrant 支持的格式（建议 UUID）。
- Embedding 模型迁移使用新 Collection，按 KB 整体切换 Profile，验证后切换并暂留旧 Collection 回滚；使用固定集评估质量/延迟。
- ChatSession 绑定 tenant/user/KB，每次访问重验权限；ChatMessage 角色 USER/ASSISTANT/SYSTEM/TOOL；Citation 独立关联来源；采用指定五种 SSE 事件名并缓冲批量持久化。
- 文档处理采用 MySQL task table + Spring Boot polling Worker、lease/锁、幂等和可恢复；暂不引入 RabbitMQ/Kafka；新版失败保留旧有效版本。
- API 统一 envelope/traceId/异常和状态码，列表默认 20 最大 100，审计重要操作，默认认证、服务端权限校验、上传限制、限流和最小 Actuator 暴露。
- Docker Compose 单机首版、DEV/TEST/PROD 隔离、Actuator + Micrometer 起步、联合备份与恢复演练。
- 数据按类型保留；文档逻辑删除后立即停止检索并异步清理；Chat 可由用户主动删除；审计单独保留；首版配置化租户配额，暂不计费。
- 初期容量目标：100 租户、1000 用户、20 路 Chat、5 个 Worker、普通 API P95 500ms；这些是规划目标，不是实测结果。

## 当前代码已实现

- `POST /api/vector/add` 单条向量写入，metadata 有 tenantId/category。
- `POST /api/vector/search` Qdrant 相似度检索，按请求 tenantId filter。
- Actuator health/info 查询；前端本地页面、检索展示和 localStorage 会话。

当前无登录/JWT/授权、MySQL 业务表、文档/版本/任务/切片模型、文件上传/解析、服务端 Chat/SSE、Profile 管理或生命周期清理。实现明细见架构文档，不能把 UI 或 ADR 目标写成已交付。

## 继续 PENDING 的事项

- Access/Refresh Token 生命周期/刷新/撤销。
- KB_ADMIN/EDITOR/VIEWER 权限矩阵；系统级管理员身份和权限。
- SHA-256 去重后的物理复用、引用计数和物理删除时机。
- 文档任务超时、取消、人工重试和补偿。
- SSE 断线恢复、续传和重复事件处理。
- Chat 消息/引用删除、软删除和保留策略。
- 租户配额默认值及超限处理。
- Embedding/LLM 供应商选择。
- Embedding 迁移增量同步、一致性切换与失败回滚。
- ChunkVector/ChunkIndex 独立建模形态；Document/Version/Task 具体状态枚举；引用快照和 Profile 路由字段等未决实现细节。

PENDING 事项不阻止 13 项已确认决策标记 Accepted，也不允许在实现时静默填补。

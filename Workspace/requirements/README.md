# 需求与功能范围现状

本文根据现有页面、API 契约和后端源码记录代码状态，不等同于经产品确认的需求规格。当前未发现完整 PRD、角色定义或验收标准。

## 已实现（源码依据）

- 单条向量片段写入 `POST /api/vector/add`，输入 id/content/tenantId/category。
- Qdrant 相似度检索 `POST /api/vector/search`，topK 1..100，按请求 tenantId 做 metadata filter。
- Actuator health/info 查询；health details 当前配置为 always。
- 前端管理壳、Dashboard 状态查询、向量写入/检索展示，以及对真实检索结果执行客户端命中率/MRR 计算。
- 侧栏、tenantId 输入和 Chat 会话记录的部分本地状态；会话 localStorage 不等于服务端持久化。

接口和步骤依据见 [`../architecture/README.md`](../architecture/README.md)。

## 前端已规划/有页面，后端未实现

Chat LLM 对话、服务端会话历史及 SSE；知识库 CRUD；文档上传/解析/切片/状态/删除；按文档查询和删除切片；服务端评估及历史；Dashboard 聚合统计；用户/租户 CRUD、认证与授权。页面、类型或 API 函数存在不代表后端能力或正式需求已确认。

## 规划中的完整 RAG 流程

文档上传 → 解析 → 切片 → Embedding → Qdrant 存储 → 用户提问 → Query Embedding → 向量检索 → Metadata Filter → Prompt 组装 → LLM 生成 → SSE 返回。

当前已具备向量写入/检索及 tenantId filter；写入/检索中的 Embedding 由 Spring AI VectorStore 调用。文档管道、Prompt、服务端 Chat/LLM 入口和 SSE producer 尚未实现。

## 待确认需求

- 用户角色、认证方式、授权边界及租户分配方式。
- 知识库创建规则、成员权限及知识库与租户关系。
- 支持文件格式/大小、解析错误与重试、切片参数和任务状态。
- 原件存储、保留期限、删除与向量清理规则。
- Chat 上下文、topK/阈值、引用格式、会话持久化、SSE 事件/错误协议。
- 评估数据集、指标口径、LLM 评价、历史保留策略。
- Dashboard 指标定义、分页排序、审计要求。
- 生产是否公开健康检查详情。

在需求确认前，以上保持“待确认”，不可仅凭 UI 或预留 DTO 推断。

## 架构评审建议（待确认）

- 身份建议支持用户可加入多个 Tenant，通过 TenantMembership 选择活动租户并执行服务端授权；系统管理员、租户管理员、普通成员分权。认证协议、是否允许多租户成员及角色矩阵需要负责人确认。
- 知识库归属于一个 Tenant；KnowledgeBaseMember 记录成员授权。共享 Qdrant Collection + 强制 tenant/kb Payload Filter 作为初期候选，需 ADR 确认。
- MySQL 建议保存关系型业务事实与处理状态；文件原件存对象/文件存储；Qdrant 保存向量及检索所需 Payload。Chunk 文本建议保留在 MySQL 以支持重建与追溯，数据规模/敏感性可再评估。
- 文档重新处理采用版本化新建、校验后切换、异步清理旧向量；跨 MySQL/Qdrant 不依赖分布式事务，需幂等补偿任务。具体保留与删除策略待确认。
- ChatSession/ChatMessage 按用户和租户持久化；Message 引用关联 chunk/document 并保留检索 rank/score，具体引用快照内容及 SSE 事件协议待确认。

以上只是架构建议。数据库字段和关系草案及 Collection 方案细节在 [`../database/README.md`](../database/README.md)，需负责人确认后才能成为数据库设计基准。

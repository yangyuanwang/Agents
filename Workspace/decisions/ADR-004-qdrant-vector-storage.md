# ADR-004：Qdrant Collection 与 Embedding Profile

## 状态

Accepted

## 决策背景

向量检索需同时处理多租户/知识库隔离和多个 embedding 向量空间。当前实现只有固定 `knowledge_base` Collection，且只写 tenantId/category metadata。

## 决策内容

- 使用共享 Collection + Payload Filter。
- 同一 Embedding Profile 下多个租户和知识库共用 Collection。
- 通过 tenantId、knowledgeBaseId 等 Payload 字段过滤，过滤由服务端强制注入。
- 引入 Embedding Profile。
- 不同 Embedding Profile 的向量空间不得随意混用。
- Collection 按 Embedding Profile 管理。
- 不得只根据向量维度判断两个模型兼容。

## 备选方案

- 每租户一个 Collection。
- 每知识库一个 Collection。
- 不区分 Profile、将向量混存在同一 Collection。

## 决策理由

**分析补充：** 共享 Collection 控制集合数量；Profile 边界防止不兼容向量混合；Payload 过滤支持共享检索。Qdrant filter 不承担 MySQL 授权职责。

## 影响及约束

每个 point 需携带完成租户/KB/document 范围查询所需的 metadata；具体字段与索引尚待数据库/实现设计。每个 Profile 管理其 Collection。按 KB 整体切换 Profile 时，逻辑上必须能确定该 KB 当前有效 Profile；这是检索路由约束，不预设具体表/字段。

## 相关决策

ADR-003、ADR-006、ADR-007、ADR-009、ADR-012。

## 尚未解决的问题

- PENDING：Profile 字段定义、模型版本识别和 Collection 命名规则。
- PENDING：Embedding 迁移期间增量同步、双写/回放与一致性切换。
- PENDING：Payload 字段索引、过滤性能和清理实现细节。

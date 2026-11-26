# ADR-008：Chat 会话、消息、引用与 SSE

## 状态

Accepted

## 决策背景

前端已有 Chat UI 与本地 Conversation 类型，但没有服务端 Chat API、消息持久化或 SSE producer。需要确定服务端会话归属和生成输出的基本契约。

## 决策内容

- ChatSession 绑定 `tenant_id`、`user_id`、`knowledge_base_id`。
- 每次访问会话都重新校验当前权限。
- ChatMessage 支持 `USER`、`ASSISTANT`、`SYSTEM`、`TOOL`。
- Citation 独立存储并关联 `message_id`、`document_id`、`document_version_id`、`chunk_id`、`score`、`rank`。
- SSE 事件名为 `message_start`、`delta`、`citation`、`done`、`error`。
- 生成期间缓冲并批量持久化；完成时确保完整落库。

## 备选方案

- 会话只存在浏览器 localStorage：无法服务端鉴权、跨端读取或可靠保留。
- 将引用仅内嵌回答文本：难以结构化追踪来源。

## 决策理由

**分析补充：** 会话绑定身份/租户/KB 支持资源授权；独立引用关系保留来源结构。SSE 事件名称是已确认协议基线。

## 影响及约束

Session 读取时按当前 TenantMember/KB 权限重新授权。消息生成状态、delta event ID、重连/续传、引用快照和删除语义需继续定义；不能将本地前端存储描述为服务端实现。文档删除后历史 Citation 不能赋予重新下载原件权限。

## 相关决策

ADR-002、ADR-003、ADR-006、ADR-010、ADR-012。

## 尚未解决的问题

- PENDING：SSE 顺序之外的字段、断线恢复、续传 cursor、重复事件和取消行为。
- PENDING：消息生成中的持久化状态、部分答案处理与具体批量策略。
- PENDING：Chat/引用软删除、保留周期、快照字段以及删除后展示/原件访问规则。

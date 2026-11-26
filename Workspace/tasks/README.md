# 架构落地阶段与任务路线

本计划以 13 份 Accepted ADR 为目标基线；Accepted 表示已确认，不表示实施完成。未决事项必须按 ADR 标为 PENDING，不能由开发自行填补。当前代码实现状态见 [`../architecture/README.md`](../architecture/README.md)。

## 阶段 0：决策与架构基线（文档已完成）

- ADR-001 至 ADR-013 已正式整理为 Accepted。
- 一致性分析 C1-C5 已协调为实现约束或保留 PENDING，详见 [`../architecture/consistency-analysis.md`](../architecture/consistency-analysis.md)。
- 数据库文档仍是逻辑模型草案，不是最终 ER/DDL。

## 阶段 1：基础工程与认证授权

- 实现 Spring Security + JWT、User 和 TenantMember 认证/会话链路。
- 实现租户与 KB 授权上下文，所有业务 API 依据可信身份校验，拒绝信任请求 tenantId/userId。
- 落实统一 API envelope、traceId、错误体系、审计、限流和 Actuator 最小端点。
- 前置：Token 生命周期、系统管理员边界、KB role matrix 等 PENDING 安全细节；由负责人确认后实现。

## 阶段 2：MySQL 业务模型与知识库管理

- 在逻辑实体评审基础上确定 ER、字段、索引、主键、迁移工具和结构基准。
- 实现 Tenant/KB/KB member 元数据 API 与权限。
- 明确 KB 当前 active Embedding Profile 的解析和切换路由。
- 前置：系统级/知识库权限边界、ChunkVector 持久化形态等相关 PENDING。

## 阶段 3：文件、文档版本和任务

- 实现本地文件适配器及受控对象 key；文档 API/版本状态和 MySQL ProcessingTask。
- 实现 Worker 定时领取、lease/恢复、幂等及受控重试。
- 处理 SHA-256 复用、引用关系、物理清理时机前需关闭 ADR-005 PENDING。
- 区分 Document 资源状态、DocumentVersion 产物状态和 ProcessingTask 执行状态；具体枚举转移需评审。

## 阶段 4：解析、切片和 Qdrant 索引

- 实现 PDF/DOCX/TXT/Markdown 解析、Chunk 文本持久化和 Embedding Profile index。
- 按 Profile 写入 Collection，记录 point/index 状态；新版验证后切 active，失败保留旧版本。
- 使 Document 逻辑删除后检索立即过滤，再异步清理 Qdrant/文件并重试对账。
- 前置：ChunkVector 逻辑持久化形态、Profile 字段及迁移期间增量同步 PENDING。

## 阶段 5：Chat/RAG、Citation 与 SSE

- 会话和消息持久化，访问时重验 tenant/user/KB 权限。
- 实现 query embedding、profile 路由、强制 metadata filter、删除状态门禁、Prompt 与 ChatModel。
- 实现已确认事件名 `message_start`、`delta`、`citation`、`done`、`error`，缓冲并批量落库。
- 前置：SSE 断线恢复/事件去重、Chat 与 Citation 保留策略、具体模型供应商等 PENDING。

## 阶段 6：生命周期、配额与治理

- 文档删除异步清理、消息/引用和审计按各自保留策略清理。
- 实现租户资源配额和超限处理；相关默认值 PENDING。
- 增加清理失败重试、周期对账、审计与安全操作追踪。

## 阶段 7：部署与备份恢复

- Compose DEV/TEST/PROD 部署隔离；Actuator + Micrometer。
- 联合备份 MySQL、原始文件和各 Profile Qdrant Collection；进行定期恢复演练与一致性检查。
- 前置：MySQL 版本、秘密管理、RPO/RTO 与备份保留 PENDING。

## 阶段 8：容量压测和验收

- 验证 100 租户、1000 用户、20 Chat、5 Worker 和普通 API P95 500ms 规划目标。
- 分开测 API、租户公平性、Worker、Embedding provider、LLM Chat 并发；避免批量 embedding 挤占 Chat 资源。
- 定义负载数据、API 范围、统计窗口、模型供应商限额及 SSE 首 token/完成延迟口径后再验收。目标当前未压测验证。

## 依赖关系与约束

认证/租户权限先于任何真实多租户业务；数据库迁移前先完成逻辑模型和 PENDING 取舍；文件/任务为解析和切片前置；Chunk/Profile 映射和增量机制先于安全迁移；有检索索引及权限门禁后再完成 Chat/RAG；数据生命周期和联合备份需覆盖新增存储。不得在本阶段执行数据库设计、建表或迁移。

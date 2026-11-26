# Agents 项目工作空间

`Workspace/` 是 `Agents/` 根目录下前后端工程的统一知识库，不是独立应用。当前工程包括 Vue 前端 `frontend/` 和 Spring Boot 后端 `spring-ai-qdrant-rag/`。实现现状以源码和配置为准。

## 文档导航

- [`requirements/README.md`](requirements/README.md)：能力状态、规划和待确认需求。
- [`architecture/README.md`](architecture/README.md)：架构、接口、RAG 流程和安全现状。
- [`architecture/consistency-analysis.md`](architecture/consistency-analysis.md)：13 项决策的一致性分析、冲突和 PENDING 清单。
- [`database/README.md`](database/README.md)：MySQL/Qdrant 现状及待确认的业务关系。
- [`development/README.md`](development/README.md)、[`development/tech-stack.md`](development/tech-stack.md)：开发约定与技术栈基线。
- [`integration/frontend-backend-contract.md`](integration/frontend-backend-contract.md)：前端工程规范与前后端接口契约；后续所有业务接口开发必须遵循，涵盖 registry 状态、DTO 转换、统一请求响应、SSE 与真实接口验证。
- [`decisions/README.md`](decisions/README.md)：ADR-001 至 ADR-013 索引和决策记录约定。
- [`tasks/README.md`](tasks/README.md)：建议开发阶段与优先级任务。

协作规则见根目录 [`AGENTS.md`](../AGENTS.md)。每项任务先读根规则与本文件，再按需选择相关文档。

## 当前概况

- 前端 Vue 管理界面包含 Dashboard、对话、知识库、文档/切片、评估、用户和租户页面；页面存在不等于后端已支持。
- 后端 Spring Boot 3.5.7 + Spring AI 1.1.0，目前实现向量写入、带 tenantId metadata filter 的相似度检索及 Actuator health/info。
- Qdrant 由 Docker Compose 提供，HTTP 6333、gRPC 6334，命名卷持久化。
- MySQL 只有 JDBC 依赖和连接配置；未发现业务实体、Repository、DDL 或迁移。
- 登录认证/授权是 Accepted 目标架构但当前代码未实现；权限细节中的开放项见 ADR。文档处理和 Chat/RAG 主流程也尚未实现。

## 维护原则

区分已实现、已规划、待确认。数据库结构变化同步更新结构基准和迁移说明；API、业务流程及架构变化同步更新相关文档。未经授权不执行数据库初始化或清理。

# 技术栈与现状基线

状态：根据当前仓库配置整理；版本以实际配置为准。检查日期：2026-11-25。

## 前端（`frontend/`）

- Vue 3.5、TypeScript 5.7、Vite 6、Vue Router 4、Pinia 2、Element Plus 2、Axios 1；版本范围取自 `frontend/package.json`。
- Vite 开发服务器默认 `127.0.0.1:5173`，将 `/api`、`/actuator` 代理到 `http://localhost:8080`（可用 `VITE_PROXY_TARGET` 覆盖）。
- 路由包含 Dashboard、Chat、Knowledge、Document/Chunks、Evaluation、Users、Tenants 等页面。前端 README 与 `src/api/registry.ts` 将未实现接口标为待开发；页面存在不代表后端功能已实现。
- `npm run build` 包含类型检查和生产构建。

## 后端（`spring-ai-qdrant-rag/`）

- Java 17；Spring Boot 3.5.7；Maven；Spring AI BOM 1.1.0。
- Spring AI 官方 Qdrant VectorStore starter；默认 Collection `knowledge_base`，gRPC 默认端口 6334；Qdrant HTTP 6333、gRPC 6334，Compose 命名卷持久化。
- Embedding/Chat provider 配置相互独立；当前提供 Ollama 与 OpenAI profile。默认 Ollama，默认 embedding 模型 `nomic-embed-text`，默认 chat 模型 `llama3.2`；OpenAI API Key 从 `OPENAI_API_KEY` 读取。
- 当前实现 `POST /api/vector/add`、`POST /api/vector/search`。添加接口写入 id/content/tenantId/category；搜索使用 Qdrant metadata filter 强制限制 tenantId。
- Actuator 暴露 health/info。持久层采用 Spring JDBC/Hikari、MyBatis-Plus 3.5.18、MySQL Connector/J、Flyway；本机 MySQL 8.0.29 的 rag schema 已初始化。Entity/Mapper 是物理表基础映射，不代表业务 Repository 或授权流程已实现。

## 运行依赖与未确认项

- Qdrant Compose 已配置；是否要将 MySQL 纳入本地 Compose、目标 MySQL 版本及部署形态：**待确认**。
- Spring Security + JWT 与基础租户授权模型已作为目标架构 Accepted（ADR-001 至 ADR-003），但当前代码未实现；系统管理员边界和 KB 细粒度权限矩阵仍 **待确认**。
- 知识库/文档数据库具体结构及字段仍 **待确认**，详见 `Workspace/database/README.md`。
- RAG 文档解析、切分、Chat 生成、评估等功能在前端有规划或占位，后端当前接口尚未全部实现。
- 技术信息证据：`frontend/package.json`、`frontend/vite.config.ts`、`frontend/README.md`、`frontend/src/api/registry.ts`、`spring-ai-qdrant-rag/pom.xml`、`spring-ai-qdrant-rag/src/main/resources/application.yml`、`spring-ai-qdrant-rag/docker-compose.yml`、后端 `README.md` 和现有 Java 源码。

## 已确认但尚未实现的目标技术约束

以下源自 Accepted ADR，不代表当前依赖或代码已包含：

- 认证目标：Spring Security + JWT；当前 `pom.xml` 尚未包含 Spring Security 依赖，后续实现不得视为现状。
- 向量存储目标：Qdrant shared Collection + Payload Filter，Collection 按 Embedding Profile 管理；目前配置仍固定一个 `knowledge_base`。
- 文件处理目标：首版本地文件系统 + 可替换的存储接口；当前没有文件存储模块。
- 任务目标：MySQL task table + Spring Boot polling Worker；当前没有业务表/任务实现，也未决定引入 Broker。
- API 目标：统一 `{code,message,data,traceId}`、业务异常、限流/审计等；当前响应实现不同且不完整。
- 运维目标：Compose 单机首版、DEV/TEST/PROD 隔离、Actuator + Micrometer 和联合备份；当前 Compose 只定义 Qdrant。

已确认目标见 [`../decisions/README.md`](../decisions/README.md)，未决项见各 ADR 的 PENDING 部分。不得通过本节推断安装了新依赖、创建了 schema 或完成了部署。


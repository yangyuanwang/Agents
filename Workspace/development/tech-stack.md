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
- Actuator 暴露 health/info。后端带 JDBC/MySQL 驱动及连接参数占位，但当前无业务表模型，不能据此推定已建立 MySQL 架构。

## 运行依赖与未确认项

- Qdrant Compose 已配置；是否要将 MySQL 纳入本地 Compose、目标 MySQL 版本及部署形态：**待确认**。
- 产品需求和角色权限、正式身份认证、知识库/文档数据模型：**待确认**。
- RAG 文档解析、切分、Chat 生成、评估等功能在前端有规划或占位，后端当前接口尚未全部实现。
- 技术信息证据：`frontend/package.json`、`frontend/vite.config.ts`、`frontend/README.md`、`frontend/src/api/registry.ts`、`spring-ai-qdrant-rag/pom.xml`、`spring-ai-qdrant-rag/src/main/resources/application.yml`、`spring-ai-qdrant-rag/docker-compose.yml`、后端 `README.md` 和现有 Java 源码。

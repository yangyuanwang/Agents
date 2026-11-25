# Spring AI + Qdrant RAG 实战（第一阶段）

基于 Spring Boot **3.5.7**、Spring AI **1.1.0**、Java 17、Maven 与 Qdrant。Spring AI 1.1.x 面向 Spring Boot 3.x；Spring AI 2.x 需要 Spring Boot 4.x，因此此项目通过 BOM 统一管理 Spring AI 版本。Qdrant 使用官方 `spring-ai-starter-vector-store-qdrant` 集成及 `VectorStore` 抽象。Chat 与 embedding 供应商分别配置；当前示例包含 Ollama/OpenAI profile。

## 架构

`VectorController` 接收 DTO，`VectorService` 组装 Spring AI `Document` 并调用 `VectorStore`，Spring AI 将内容交给 EmbeddingModel 生成向量并写入 Qdrant。查询时由 EmbeddingModel 向量化 query，Qdrant 执行近邻搜索及 Payload 条件过滤。当前阶段不实现 RAG Prompt/Chat 问答。

## Qdrant 数据模型

- **Collection**：`knowledge_base`，逻辑上类似向量集合/索引；首次启动由 Spring AI 初始化。
- **Point**：每条 Document 对应一个 Qdrant 点。
- **Vector**：EmbeddingModel 对文本产生的浮点数组；Qdrant 使用 cosine 相似度。
- **Payload**：Document 文本及 metadata（`tenantId`、`category`）。Payload 的租户条件用于服务端过滤。

Embedding 模型把文本映射到高维向量空间，语义相近的文本通常距离更近。写入与查询必须使用同一个 embedding 模型及维度。

## 启动 Qdrant

```bash
docker compose up -d
docker compose ps
```

HTTP API：<http://localhost:6333>；Web Console：<http://localhost:6333/dashboard>；gRPC：`localhost:6334`。数据保存在 Docker volume `qdrant_data`。检查：`curl http://localhost:6333/readyz`、`curl http://localhost:6333/collections`。

## 启动应用

需要 JDK 17 和 Maven。默认使用 Ollama profile：

```powershell
ollama pull nomic-embed-text
ollama pull llama3.2
$env:JAVA_HOME='D:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE='ollama'
mvn spring-boot:run
```

OpenAI 可单独切换：设置 `SPRING_PROFILES_ACTIVE=openai`、`OPENAI_API_KEY`，并可用 `OPENAI_EMBEDDING_MODEL` / `OPENAI_CHAT_MODEL` 覆盖模型。API Key 仅从环境变量读取。Embedding 与 Chat 配置独立，替换其他供应商时使用其 Spring AI starter、对应 provider 配置和独立 `EMBEDDING_PROVIDER` / `CHAT_PROVIDER`，Qdrant VectorStore 无需变更。MySQL JDBC 驱动与连接占位配置为后续业务阶段预留；本阶段不要求 MySQL 在线，也未实现业务表。

## 接口

添加：

```bash
curl -X POST http://localhost:8080/api/vector/add -H "Content-Type: application/json" -d '{"id":"doc-1","content":"退款申请需要在购买后七天内提交。","tenantId":"tenant-a","category":"售后"}'
```

检索（租户过滤始终强制启用）：

```bash
curl -X POST http://localhost:8080/api/vector/search -H "Content-Type: application/json" -d '{"query":"如何申请退款？","tenantId":"tenant-a","topK":5}'
```

多类别和租户隔离测试：

```bash
curl -X POST localhost:8080/api/vector/add -H 'Content-Type: application/json' -d '{"id":"a-1","content":"退款七天内申请","tenantId":"tenant-a","category":"售后"}'
curl -X POST localhost:8080/api/vector/add -H 'Content-Type: application/json' -d '{"id":"a-2","content":"报销需要发票","tenantId":"tenant-a","category":"财务"}'
curl -X POST localhost:8080/api/vector/add -H 'Content-Type: application/json' -d '{"id":"b-1","content":"退款需填写表格","tenantId":"tenant-b","category":"售后"}'
curl -X POST localhost:8080/api/vector/search -H 'Content-Type: application/json' -d '{"query":"退款流程","tenantId":"tenant-a","topK":10}'
curl -X POST localhost:8080/api/vector/search -H 'Content-Type: application/json' -d '{"query":"退款流程","tenantId":"tenant-b","topK":10}'
```

两次检索结果分别只包含对应 tenantId 的数据。查询结果的 `score` 由 Qdrant 返回；metadata 中包含 tenantId/category。第一阶段不暴露任意过滤表达式，以免调用方绕过租户边界。

## 检索与过滤原理

1. 文本由 EmbeddingModel 转为与 Collection 维度匹配的向量。
2. VectorStore 将向量近邻查询委托给 Qdrant，按相似度取 topK。
3. `FilterExpression` 将 `tenantId == '...'` 编译为 Qdrant Payload filter，在数据库端过滤候选结果。
4. Qdrant 返回 Document 内容、metadata 和相似度分数。

## 常见问题

- **连接失败**：确认容器健康、应用使用 `localhost:6334`（gRPC），Docker 内应用则用服务名 `qdrant`。
- **embedding 连接失败**：确认 Ollama 已启动且已拉取模型，或 OpenAI Key 有效。
- **维度不匹配**：更换 embedding 模型/维度后，使用新 Collection 名称或清空旧 Collection 后重建；不要混用不同模型的向量。
- **搜索为空**：检查写入是否成功、租户 ID 是否一致、collection 名是否一致。
- **端口冲突**：调整 compose 映射及 `QDRANT_GRPC_PORT`/`QDRANT_HOST`。

## 范围

已完成 Qdrant 持久化环境、Spring AI Qdrant VectorStore、Embedding 写入、相似度搜索、Payload metadata filter、强制 tenantId 隔离、异常处理与接口示例。尚未实现 RAG 文档解析/切分、Chat 问答链、认证授权、MySQL 业务模型及自动化集成测试。

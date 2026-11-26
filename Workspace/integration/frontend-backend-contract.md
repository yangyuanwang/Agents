# 前端工程规范与前后端接口契约

> 依据：本次读取的 `frontend/README.md`、`frontend/src/api/`、`types/`、`stores/`、`router/`、`views/`、`components/`、`utils/`，以及当前后端 Controller、DTO、Service、异常处理器和 `application.yml`。状态判断以 `frontend/src/api/registry.ts` 为前端唯一接口状态来源，并用后端当前源码核实。本文描述当前工作树观察结果；Mapper/Entity 存在不代表对应 HTTP API 已实现。

## 1. 前端现状与目录职责

前端使用 Vue 3、TypeScript、Pinia、Axios、Vue Router、Element Plus。README 给出 Vite 开发代理 `/api`、`/actuator` 到后端的配置方式。路由使用 hash history。

- `src/api/`：Axios 封装、接口 registry 和按领域拆分的 API 函数。除向量与 Actuator 外，部分模块函数是预留调用，不能据此认定后端已有接口。
- `src/types/`：`api.ts` 定义接口状态、通用错误和 registry 类型；`vector.ts` 对应当前向量 DTO；`domain.ts` 多数是待开发业务能力的前端契约。
- `src/stores/`：`app` 保存侧栏展开状态；`tenant` 保存 tenantId/category 输入状态；`chat` 保存本地会话；`system` 共用健康状态、info、延迟和检查时间。
- `src/router/`：声明页面路径及 `meta.status`，供侧边栏显示接入状态；未发现路由守卫或认证授权控制。
- `src/views/`：Dashboard、Chat、Knowledge、Document、Chunk、Evaluation、User、Tenant 和 NotFound 页面。
- `src/components/`：PageHeader、PendingNotice、EndpointTable、StatusLight、StatCard、SearchResultList、MarkdownView 等共用显示组件。
- `src/utils/`：SSE 解析/读取与格式化辅助函数。

页面存在不等于业务后端接口存在。页面中的空列表、待开发标记和真实请求错误状态应保持区分。

## 2. 页面与接口对应关系

| 页面 | 路径 | 实际接入或预留接口 | 页面现状 |
| --- | --- | --- | --- |
| Dashboard | `/dashboard` | Health、Info；预留 dashboard.stats；执行真实 vector.search 自检 | 健康信息真实；统计待开发 |
| AI 对话 | `/chat` | vector.search 引用检索；预留 chat.stream、conversation CRUD | 生成接口待开发；会话保存在浏览器 localStorage |
| 知识库 | `/knowledge` | knowledge-bases CRUD 预留 | 待开发，无假数据 |
| 文档 | `/document` | vector.add 手工写入片段；documents 上传/列表/删除预留 | 文档处理待开发；单片段写入已可用 |
| 切片 | `/document/chunks` | vector.search；按文档分页、删除切片预留 | 展示真实检索结果；不能视作完整切片目录 |
| 评估 | `/evaluation` | vector.search 真实检索 + 前端命中判定；evaluations 预留 | 检索命中率/MRR可由真实检索算出；LLM评测及历史待开发；用例存本地 |
| 用户 | `/system/users` | users CRUD 预留 | 待开发 |
| 租户 | `/system/tenants` | tenants CRUD 预留；vector.search tenantId 过滤实验 | 租户实体管理待开发；默认 tenantId 只是输入初值 |

## 3. 已实现接口清单

以 `registry.ts` 的 `available` 项为准，当前共 4 项：

| Key | 方法与路径 | 前端调用 | 当前后端证据/行为 |
| --- | --- | --- | --- |
| `vector.add` | `POST /api/vector/add` | `api/vector.ts` | `VectorController` 接收 `VectorAddRequest`，成功返回 201 和 `{id,status:"stored"}`；Service 写入 Qdrant |
| `vector.search` | `POST /api/vector/search` | `api/vector.ts` | 接收 `VectorSearchRequest`，返回 `VectorSearchResult[]`；Service 以 tenantId 构造 Qdrant metadata filter |
| `system.health` | `GET /actuator/health` | `api/system.ts`、`system` Store | `application.yml` 暴露 health；配置 show-details=always。前端按 `status` 判在线状态 |
| `system.info` | `GET /actuator/info` | `api/system.ts`、`system` Store | `application.yml` 暴露 info 并开启 env 信息；Info 请求失败不影响健康判定 |

需注意：registry 中 `vector.add` 的注释称四字段均 `@NotBlank`，实际 DTO 确认如此。后端验证失败与统一异常处理的具体状态码/错误体应以 `ApiExceptionHandler` 实际实现为准，不应把“未实现接口”处理成参数错误。

## 4. 待开发接口清单

以下均在 `registry.ts` 标为 `pending`，路径/方法是前端预留契约，不证明后端已有 Controller：

| Key | 前端预留方法与路径 | 页面 |
| --- | --- | --- |
| `chat.stream` | POST `/api/chat/stream`（SSE） | AI 对话 |
| `conversation.list` | GET `/api/conversations` | AI 对话 |
| `knowledge.list/create/update/delete` | GET/POST `/api/knowledge-bases`、PUT/DELETE `/api/knowledge-bases/{id}` | 知识库、Dashboard |
| `document.upload/list/delete` | POST `/api/documents/upload`、GET `/api/documents`、DELETE `/api/documents/{id}` | 文档、Dashboard |
| `chunk.list/delete` | GET `/api/documents/{id}/chunks`、DELETE `/api/chunks/{id}` | 切片 |
| `evaluation.run/list` | POST/GET `/api/evaluations` | 评估 |
| `dashboard.stats` | GET `/api/dashboard/stats` | Dashboard |
| `tenant.list/create/delete` | GET/POST `/api/tenants`、DELETE `/api/tenants/{id}` | 租户 |
| `user.list/create/delete` | GET/POST `/api/users`、DELETE `/api/users/{id}` | 用户 |

API 源码还预留了若干 registry 未列出的调用，例如 conversation 详情/删除、document 状态查询、knowledge update 等。registry 是状态事实来源；在将这些调用标为可用前，先核对实际后端接口并补全 registry，避免漏报状态。

## 5. 请求与响应数据规范

- 普通 API 通过 `request<T>` 调用 Axios，base URL 默认 `/api`，API 函数路径通常不含 `/api` 前缀；Actuator 使用独立 `/actuator` base URL。
- 请求与响应字段采用 camelCase。普通请求 JSON 序列化；multipart 上传预留函数创建 FormData，手动指定 multipart Content-Type 并设置 120 秒超时。
- `request<T>` 直接返回 `response.data`，当前没有统一 `{data: ...}` 信封解包。后端返回 DTO/数组时前端按其结构消费。
- Axios 响应拦截器把错误收敛为 `ApiError`。HTTP 404、405、415、501 被当作接口待开发；网络错误、超时和其他状态仍作为故障。`silent` 可关闭全局消息，由页面自行处理。
- 前端类型泛型在运行时不校验 JSON。后端新增/变更字段、分页封装或统一信封时，须同时调整 API 调用和 DTO 类型。
- 前端预期错误字段为 `{timestamp?, error?, message?}`。读取 `ApiExceptionHandler` 源码确认的字段和状态码优先于注释中的示例。

## 6. DTO 契约

当前已实现 DTO：

| DTO | 字段 | 约束/响应约定 |
| --- | --- | --- |
| VectorAddRequest | `id`, `content`, `tenantId`, `category` | 四项 `@NotBlank`；JSON camelCase |
| VectorAddResponse（Controller 内部响应记录） | `id`, `status` | 写入成功为 `stored`，HTTP 201 |
| VectorSearchRequest | `query`, `tenantId`, `topK` | query/tenantId 非空白，topK 1–100 |
| VectorSearchResult | `id`, `content`, `score`, `metadata` | score 为数值；metadata 为键值对象；返回数组 |

`types/vector.ts` 与上述字段相符。Health 类型只声明可选 `status/groups/components`，允许 Spring Boot 输出实际健康细节。

`types/domain.ts` 中 KnowledgeBase、RagDocument、Conversation、EvaluationCase/Result、Tenant、AppUser 是前端预留结构，不是后端 DTO 现状。比如文档状态枚举、知识库计数字段、用户 role 等必须在后端 DTO 确定后协商，不可把数据库 Entity 直接序列化暴露，也不应假定可选字段已实现。建议后端控制器通过专用 DTO 映射 Entity，并与此处前端契约逐项对齐。

## 7. SSE 交互规范

`utils/sse.ts` 使用 `fetch` + `ReadableStream`，因为生成请求需要 POST JSON，不能使用只能 GET 的 EventSource。请求设置 `Content-Type: application/json`、`Accept: text/event-stream`，支持 AbortSignal 取消。

解析器按空行拆事件，读取 `event:` 与一个或多个 `data:` 行（多行以换行拼接），默认事件名为 `message`，忽略注释行。成功响应应提供流式 body；若 Content-Type 非 `text/event-stream` 会报错。404/405/415/501 识别为待开发。

`api/chat.ts` 预留请求体 `{conversationId, message, tenantId, topK}`。当前前端只把 SSE 事件作为 `{event,data}` 字符串交给页面处理，尚未形成已确认的事件名、JSON data schema、错误事件或完成事件类型。后端实现前应与前端明确 token、引用、错误、结束事件的名称与载荷；流结束应能明确完成，取消应停止生成。不要将 `application.yml` 注释里的建议事件格式误认为已经实现的协议。

## 8. 前后端接口对接流程

1. 先在 `frontend/src/api/registry.ts` 更新唯一状态清单和契约说明；只有后端实际可调用后才标为 `available`。
2. 后端新增 Controller 时定义请求/响应 DTO、校验、状态码、分页/错误语义；Entity 不直接作为 API 模型。
3. 同步前端 `types/` 与 `api/` 函数；复用 `http.ts` 的 baseURL、异常处理和类型，流式接口复用 SSE 工具。
4. 页面复用现有 Store 与公共组件；接口未实现时保持 PendingNotice/待开发，不填充虚构业务数据。
5. 用真实后端响应核对字段、空值、错误体、状态码、分页和权限行为，再调整 `registry.ts` 与路由状态。
6. API 或业务流程变化时同步 Workspace 对应文档。当前无登录/授权路由守卫；不得以 UI 隐藏代替后端访问控制。

## 9. 后续业务模块建议开发顺序

以下是基于页面依赖的建议，不是已批准的产品路线：

1. 先稳定基础 DTO、错误响应和健康信息契约；完成知识库与租户标识之间的 API 语义确认。
2. 知识库与文档元数据/处理状态接口，再实现文档上传、处理、列表和删除。
3. 按文档分页列出切片及删除/停用能力，让切片管理不再依赖相似度检索结果。
4. 对话生成 SSE，再接会话持久化与历史；确认引用结构与结束/错误事件。
5. 评估任务与历史；Dashboard 统计依赖前述真实业务数据。
6. 用户、租户 CRUD 与认证授权需按已确认安全决策实施，不能仅凭当前前端页面推导权限模型。

## 10. 后端实现时的兼容注意事项

### KnowledgeBase API（已实现并通过真实 MySQL HTTP 集成测试）

- `POST /api/knowledge-bases`：请求 `{tenantId, createdBy, name, description}`；201 返回 `{id,tenantId,name,description,status,createdBy,createdAt,updatedAt}`。
- `GET /api/knowledge-bases?tenantId={uuid}&page=1&size=20`：page 从 1 开始，size 1–100，按 `updatedAt DESC`；返回 `{items,total,page,size}`。
- `GET /api/knowledge-bases/{id}`：返回知识库 DTO；不存在或已逻辑删除为 404。
- `PUT /api/knowledge-bases/{id}`：请求 `{name,description}`，返回知识库 DTO；不存在或已逻辑删除为 404。
- `DELETE /api/knowledge-bases/{id}`：逻辑删除，返回 204；重复删除/不存在为 404，关联记录不物理删除。
- 创建请求的 `tenantId`、`createdBy` 及列表 `tenantId` 是认证接入前的开发期临时上下文，并非可信身份或生产级租户授权。
- 保留唯一索引 `(tenant_id,name)`，因此逻辑删除后同租户仍不可重用同名知识库；需产品决策后续处理。

- 保持 `/api` 路径前缀、HTTP method 与 camelCase 字段一致；注意前端 Axios baseURL 已是 `/api`，API 函数通常只写相对路径。
- 保持 vector.add 的 201、响应 `{id,status}` 和四字段校验；search 返回数组且 topK 范围 1–100。租户过滤当前发生于 Qdrant 查询侧，不能只在前端过滤。
- 前端将 404/405/415/501 统一判为“待开发”。后端真实业务错误不应滥用这些状态码，否则 UI 会误报为待开发；参数校验和服务错误需保持统一错误体。
- Spring Health 默认只承诺 status；当前配置开放 details。不要让页面依赖不稳定的内部组件字段，后端配置变化需同步校验 Dashboard。
- 查询分页 DTO/响应目前没有统一规范，新增分页接口前必须约定 page/size 的起始下标、排序及响应结构；不要让前端数组泛型假设与分页 envelope 冲突。
- 文档上传预留字段为 `file`, `knowledgeBaseId`, `tenantId`, `category`，20MB 是页面侧约束，后端 multipart 配置当前也有大小限制；实际字段名、失败状态和异步处理响应仍需明确。
- Chat SSE 请求体字段与响应事件载荷目前未定；须兼容 POST JSON、`text/event-stream`、AbortSignal 断连以及明确终止语义。
- `domain.ts` 是预留契约，不是数据库 Schema。Entity 与前端字段之间应由后端 DTO 转换，时间、枚举、可空字段与删除语义需逐项定稿。
- 当前路由 `meta.status` 是页面接入提示，不提供认证授权；不要把默认 tenantId `default` 解释为真实租户或安全边界。
- 由于另一个会话正在开发持久化基础设施，本次只确认 HTTP 业务接口仍以 Controller 为准；新出现的 Mapper/Entity 不自动改变 registry 状态。

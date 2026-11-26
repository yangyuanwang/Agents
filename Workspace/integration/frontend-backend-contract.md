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
| 知识库 | `/knowledge` | knowledge-bases CRUD；列表使用 `tenantId` 查询分页 DTO | 列表真实请求；创建 API 请求字段尚未与后端对齐 |
| 文档 | `/document` | knowledge-bases 列表、files 上传、documents CRUD、versions | 使用真实知识库选择、文档列表/分页/详情/修改/删除和版本接口；保留 vector.add |
| 切片 | `/document/chunks` | vector.search；按文档分页、删除切片预留 | 展示真实检索结果；不能视作完整切片目录 |
| 评估 | `/evaluation` | vector.search 真实检索 + 前端命中判定；evaluations 预留 | 检索命中率/MRR可由真实检索算出；LLM评测及历史待开发；用例存本地 |
| 用户 | `/system/users` | users CRUD 预留 | 待开发 |
| 租户 | `/system/tenants` | tenants CRUD 预留；vector.search tenantId 过滤实验 | 租户实体管理待开发；当前开发身份由共享 Store 接收真实 UUID，初始为空 |

## 3. 已实现接口清单

`registry.ts` 当前登记的可用接口共 18 项：

| Key | 方法与路径 | 前端调用 | 当前后端证据/行为 |
| --- | --- | --- | --- |
| `vector.add` | `POST /api/vector/add` | `api/vector.ts` | `VectorController` 接收 `VectorAddRequest`，成功返回 201 和 `{id,status:"stored"}`；Service 写入 Qdrant |
| `vector.search` | `POST /api/vector/search` | `api/vector.ts` | 接收 `VectorSearchRequest`，返回 `VectorSearchResult[]`；Service 以 tenantId 构造 Qdrant metadata filter |
| `system.health` | `GET /actuator/health` | `api/system.ts`、`system` Store | `application.yml` 暴露 health；配置 show-details=always。前端按 `status` 判在线状态 |
| `system.info` | `GET /actuator/info` | `api/system.ts`、`system` Store | `application.yml` 暴露 info 并开启 env 信息；Info 请求失败不影响健康判定 |
| `knowledge.list/create/update/delete` | `/api/knowledge-bases` | `api/knowledge.ts`、Knowledge 页面、Document 页面 | 见第 10 节。列表与创建都使用共享 Pinia Store 中的开发期 tenant/creator UUID |
| `file.upload`（registry key 仍为 `document.upload`） | `POST /api/files/upload` | `api/document.ts`、Document 页面 | multipart `knowledgeBaseId`、`file`；成功 201 返回 FileObject DTO。返回标识字段为 `id`，前端将它传为版本请求的 `fileObjectId` |
| `document.list/create/detail/update/delete` | `/api/documents` | `api/document.ts`、Document 页面 | 列表分页信封 `{items,total,page,size}`；详情/修改/删除带 `knowledgeBaseId` 查询参数 |
| `document.version.create/list/detail/activate` | `/api/documents/{id}/versions`、`/active-version` | `api/document.ts`、Document 页面 | 版本 DTO 字段见第 10 节；创建状态 `UPLOADED`；只有 `COMPLETED` 可切换活动版本 |

需注意：registry 中 `vector.add` 的注释称四字段均 `@NotBlank`，实际 DTO 确认如此。后端验证失败与统一异常处理的具体状态码/错误体应以 `ApiExceptionHandler` 实际实现为准，不应把“未实现接口”处理成参数错误。

## 4. 待开发接口清单

以下均在 `registry.ts` 标为 `pending`，路径/方法是前端预留契约，不证明后端已有 Controller：

| Key | 前端预留方法与路径 | 页面 |
| --- | --- | --- |
| `chat.stream` | POST `/api/chat/stream`（SSE） | AI 对话 |
| `conversation.list` | GET `/api/conversations` | AI 对话 |
| `chunk.list/delete` | GET `/api/documents/{id}/chunks`、DELETE `/api/chunks/{id}` | 切片 |
| `evaluation.run/list` | POST/GET `/api/evaluations` | 评估 |
| `dashboard.stats` | GET `/api/dashboard/stats` | Dashboard |
| `tenant.list/create/delete` | GET/POST `/api/tenants`、DELETE `/api/tenants/{id}` | 租户 |
| `user.list/create/delete` | GET/POST `/api/users`、DELETE `/api/users/{id}` | 用户 |

文档与知识库接口已从待开发清单移至已实现清单；其余表项仍为前端预留接口。

## 5. 请求与响应数据规范

- 普通 API 通过 `request<T>` 调用 Axios，base URL 默认 `/api`，API 函数路径通常不含 `/api` 前缀；Actuator 使用独立 `/actuator` base URL。
- 请求与响应字段采用 camelCase。普通请求 JSON 序列化；文件上传创建 FormData 并设置 120 秒超时，由浏览器/Axios 处理 multipart boundary。
- `request<T>` 直接返回 `response.data`，当前没有统一 `{data: ...}` 信封解包。后端返回 DTO/数组时前端按其结构消费。
- Axios 响应拦截器把错误收敛为 `ApiError`。HTTP 404、405、415、501 被当作接口待开发；网络错误、超时和其他状态仍作为故障。注意：业务资源不存在也使用 404，当前前端对所有 404 都视为待开发。`silent` 可关闭全局消息，由页面自行处理。
- 前端类型泛型在运行时不校验 JSON。后端新增/变更字段、分页封装或统一信封时，须同时调整 API 调用和 DTO 类型。
- 前端预期错误字段为 `{timestamp?, error?, message?}`。读取 `ApiExceptionHandler` 源码确认的字段和状态码优先于注释中的示例。
- 参数 DTO 校验、类型不匹配、约束违反和无法解析 JSON 返回 400 `{timestamp,error:"请求参数校验失败"}`；`ResponseStatusException` 按业务状态返回 `{timestamp,error:<reason>}`；未知路由 404 返回 `{timestamp,error:"接口尚未实现"}`；未捕获异常返回 500 `{timestamp,error:"服务处理失败",message}`。

## 6. DTO 契约

当前已实现 DTO：

| DTO | 字段 | 约束/响应约定 |
| --- | --- | --- |
| VectorAddRequest | `id`, `content`, `tenantId`, `category` | 四项 `@NotBlank`；JSON camelCase |
| VectorAddResponse（Controller 内部响应记录） | `id`, `status` | 写入成功为 `stored`，HTTP 201 |
| VectorSearchRequest | `query`, `tenantId`, `topK` | query/tenantId 非空白，topK 1–100 |
| VectorSearchResult | `id`, `content`, `score`, `metadata` | score 为数值；metadata 为键值对象；返回数组 |

`types/vector.ts` 与上述字段相符。Health 类型只声明可选 `status/groups/components`，允许 Spring Boot 输出实际健康细节。

KnowledgeBase、Document、DocumentVersion、FileObject 的实际 HTTP DTO 见第 10 节。`Conversation`、`EvaluationCase/Result`、`Tenant`、`AppUser` 仍是前端本地/预留类型，不代表后端已有同名接口。前端泛型不会运行时校验响应 JSON。

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

1. 基础 DTO、错误响应和健康信息已实现；知识库接口已实现，认证前 tenantId 仍为开发输入。
2. KnowledgeBase、FileObject、Document、DocumentVersion 元数据接口已实现；文件解析、处理 Worker 和文档向量化未因此实现。
3. 按文档分页列出切片及删除/停用能力仍待开发，让切片管理不再依赖相似度检索结果。
4. 对话生成 SSE，再接会话持久化与历史；确认引用结构与结束/错误事件。
5. 评估任务与历史；Dashboard 统计依赖前述真实业务数据。
6. 用户、租户 CRUD 与认证授权需按已确认安全决策实施，不能仅凭当前前端页面推导权限模型。

## 10. 当前后端接口实现与兼容注意事项

### KnowledgeBase API（已实现并通过真实 MySQL HTTP 集成测试）

- `POST /api/knowledge-bases`：请求 `{tenantId, createdBy, name, description}`；201 返回 `{id,tenantId,name,description,status,createdBy,createdAt,updatedAt}`。
- `GET /api/knowledge-bases?tenantId={uuid}&page=1&size=20`：page 从 1 开始，size 1–100，按 `updatedAt DESC`；返回 `{items,total,page,size}`。
- `GET /api/knowledge-bases/{id}`：返回知识库 DTO；不存在或已逻辑删除为 404。
- `PUT /api/knowledge-bases/{id}`：请求 `{name,description}`，返回知识库 DTO；不存在或已逻辑删除为 404。
- `DELETE /api/knowledge-bases/{id}`：逻辑删除，返回 204；重复删除/不存在为 404，关联记录不物理删除。
- 创建请求的 `tenantId`、`createdBy` 及列表 `tenantId` 来自共享 Pinia Store，是认证接入前的开发期临时上下文，并非可信身份或生产级租户授权。Store 只接受 UUID；没有有效值时不自动调用列表/创建接口。
- 保留唯一索引 `(tenant_id,name)`，因此逻辑删除后同租户仍不可重用同名知识库；需产品决策后续处理。

### FileObject API（当前 Controller / DTO）

- `POST /api/files/upload`：`multipart/form-data`，必需 part `file` 与请求参数 `knowledgeBaseId`；缺少 part/参数或提交空文件均为 400，超过 `app.file.max-size-bytes` 为 413，不支持扩展名为 415，知识库不存在为 404。默认最大 20 MiB，允许扩展名 `pdf,docx,txt,md`，均可由配置覆盖。
- 成功状态 201，响应 `{id,tenantId,sha256,byteSize,mediaType,storageStatus,createdAt}`。`id` 是 FileObject ID；创建 DocumentVersion 时作为 `fileObjectId` 传入。上传本身不创建 Document 或版本。
- `GET /api/files/{id}?knowledgeBaseId={uuid}`：返回同一 FileObject DTO；当前实现仅返回已通过某 DocumentVersion 关联至该 KnowledgeBase 的文件，未关联或不存在为 404。

### Document API（当前 Controller / DTO）

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/documents` | JSON `{knowledgeBaseId,displayName}`；ID 必填，displayName 非空且最多 255 字符 | 201 `DocumentResponse` |
| `GET /api/documents?knowledgeBaseId={uuid}&page=1&size=20&keyword=` | knowledgeBaseId 必填；page 默认 1 且 ≥1；size 默认 20 且 1–100；keyword 可选 | 200 `{items,total,page,size}`，按更新时间倒序 |
| `GET /api/documents/{id}?knowledgeBaseId={uuid}` | path ID 与 knowledgeBaseId | 200 `DocumentResponse` |
| `PUT /api/documents/{id}?knowledgeBaseId={uuid}` | JSON `{displayName}`；非空且最多 255 字符 | 200 更新后的 `DocumentResponse` |
| `DELETE /api/documents/{id}?knowledgeBaseId={uuid}` | path ID 与 knowledgeBaseId | 204；逻辑删除，已删除/不存在/知识库不匹配为 404 |

`DocumentResponse` 字段：`{id,tenantId,knowledgeBaseId,displayName,status,activeVersionId,versionCount,activeVersion,createdAt,updatedAt}`。当前创建状态为 `ACTIVE`；删除后设置 `DELETED`，但普通查询不再返回该记录。列表页响应的活动版本可为空。

### DocumentVersion API（当前 Controller / DTO）

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/documents/{id}/versions` | JSON `{fileObjectId,originalFilename}`；fileObjectId 必填，originalFilename 可空且最多 255 字符 | 201 `DocumentVersionResponse` |
| `GET /api/documents/{id}/versions` | 无 | 200 版本 DTO 数组，按 versionNo 倒序 |
| `GET /api/documents/{id}/versions/{versionId}` | 无 | 200 单个版本 DTO |
| `PUT /api/documents/{id}/active-version` | JSON `{versionId}`，必填 | 200 更新后的 `DocumentResponse` |

`DocumentVersionResponse` 字段：`{id,documentId,versionNo,fileObjectId,sha256,mediaType,byteSize,originalFilename,status,createdAt,activatedAt}`。版本号由后端生成；新版本初始状态为 `UPLOADED`。只有 `COMPLETED` 版本允许设为活动版本，否则返回 409。`activatedAt` 为空时序列化为 JSON `null`（Jackson null inclusion 配置若变化，前端类型应同步）。

### 当前前端字段差异

- 知识库创建前端现在发送 `{tenantId,createdBy,name,description}`，其中租户和创建者均来自共享 Pinia Store；页面需要先由开发者提供数据库中真实存在的 UUID。`tenantId`、`createdBy` 仍只是开发上下文，不是认证授权信息。
- Store 不再默认使用 `default`。当前开发库查询到的租户/用户记录均带有 `doc-version-it-*` 集成测试标识，没有专用开发身份；因此 Store 初始为空，不能把测试 fixture 设成永久默认身份。租户 CRUD/发现接口仍不存在。
- KnowledgeBase `description` 前端类型为 `string | null`，与后端允许空值一致；创建/编辑空描述时发送 `null`。
- `DocumentVersionResponse.originalFilename` 前端类型为 `string | null`，与后端允许 null 一致。
- 前端版本状态类型包含 `ACTIVE | UPLOADED | PROCESSING | COMPLETED | FAILED`；当前服务创建文档的状态是 `ACTIVE`，新版本是 `UPLOADED`，激活校验 `COMPLETED`。DTO 使用 String，未实现解析 Worker 的其他状态实际产生行为尚未验证。
- `ApiExceptionHandler` 将 `MissingServletRequestParameterException`、`MissingServletRequestPartException`、`MethodArgumentNotValidException`、`ConstraintViolationException`、`HttpMessageNotReadableException` 统一映射为 HTTP 400 `{timestamp,error:"请求参数校验失败"}`；数据库与系统异常仍走通用 500，不伪装成参数错误。

- 保持 `/api` 路径前缀、HTTP method 与 camelCase 字段一致；注意前端 Axios baseURL 已是 `/api`，API 函数通常只写相对路径。
- 保持 vector.add 的 201、响应 `{id,status}` 和四字段校验；search 返回数组且 topK 范围 1–100。租户过滤当前发生于 Qdrant 查询侧，不能只在前端过滤。
- 前端将 404/405/415/501 统一判为“待开发”。后端真实业务错误不应滥用这些状态码，否则 UI 会误报为待开发；参数校验和服务错误需保持统一错误体。
- Spring Health 默认只承诺 status；当前配置开放 details。不要让页面依赖不稳定的内部组件字段，后端配置变化需同步校验 Dashboard。
- KnowledgeBase 与 Document 列表均使用 page 从 1 开始的 `{items,total,page,size}`；其他分页接口仍需在开发前明确结构。
- 文件上传正式字段是 `file` part 和 `knowledgeBaseId` 请求参数；不接收前端 tenantId/category。最大大小、允许扩展名由 `app.file.*` 配置控制。
- Chat SSE 请求体字段与响应事件载荷目前未定；须兼容 POST JSON、`text/event-stream`、AbortSignal 断连以及明确终止语义。
- `domain.ts` 是预留契约，不是数据库 Schema。Entity 与前端字段之间应由后端 DTO 转换，时间、枚举、可空字段与删除语义需逐项定稿。
- 当前路由 `meta.status` 是页面接入提示，不提供认证授权；租户/创建者 UUID 的开发输入不是安全边界。
- Mapper/Entity 存在本身不构成 HTTP API；接口是否可用仍以 Controller、DTO、前端实际调用和验证结果为准。

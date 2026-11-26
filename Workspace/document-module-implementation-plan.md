# Document 模块第一阶段实施方案

> 本文是实现前检查结果与建议，不代表功能已实现。本轮只新增此文档；未修改 Java、Vue、SQL、配置、ADR、接口契约或 registry。

## 1. 当前实现现状

- 后端已有 Spring Boot 3.5.7、MyBatis-Plus、MySQL/Flyway、事务及 BINARY(16) UUID TypeHandler。KnowledgeBase 有 Controller/DTO/Service，并通过真实 MySQL HTTP 集成测试；VectorController/VectorService 对 Qdrant 提供 add/search。
- Document、DocumentVersion、FileObject、ProcessingTask、Chunk、ChunkVector 均有 Entity/Mapper；对应 mapper 是 MyBatis-Plus `BaseMapper`。未发现这些领域的 XML、自定义 SQL、Controller、Service、业务 DTO 或 Worker。
- 未发现文件写入适配器、SHA-256 业务服务、文件解析器、DocumentVersion 流程、引用计数、孤儿文件清理器或文件下载授权接口。仅有数据库 schema、Entity/Mapper 和前端预留 API 不等于业务能力已实现。
- `VectorService` 只写入调用方传入的向量文本并用 tenantId metadata filter 搜索；它不处理 Document/Version/FileObject，也不提供上传后的向量入库。

## 2. 数据库结构与关系（以 `Workspace/database/schema-mysql.sql` 为准）

### Document

- `id BINARY(16)` 主键；`tenant_id BINARY(16)`、`knowledge_base_id BINARY(16)`、`display_name VARCHAR(255)`、`status VARCHAR(24)`、可空 `active_version_id`、`created_by`、时间字段和可空 `deleted_at`。
- 唯一键 `(tenant_id,id)`、`(knowledge_base_id,id)`；索引 `(knowledge_base_id,status,created_at)` 与 `(tenant_id,status,deleted_at)`。
- 复合 FK `(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`，creator FK → User。活动版本 FK `(id,active_version_id)` → DocumentVersion `(document_id,id)`，建表后 ALTER 添加以处理循环引用。
- 无独立 `is_deleted`；当前物理字段用 `deleted_at` 表达文档逻辑删除。实现必须以非空时间作为门禁，不得假设 MyBatis-Plus 已配置 Document 逻辑删除。

### DocumentVersion

- `(document_id,version_no)` 唯一；`(document_id,id)`、`(tenant_id,id)` 唯一。包含 tenant、source_file_id、source_sha256、media_type、byte_size、原始文件名、状态、创建者和时间/处理元数据。
- 复合 FK `(tenant_id,document_id)` → Document `(tenant_id,id)`；`(tenant_id,source_file_id)` → FileObject `(tenant_id,id)`；creator FK → User。所有引用采用 RESTRICT。
- 版本号字段为 `INT UNSIGNED`，数据库保证同一文档版本号唯一，但并发分配仍须事务锁定 Document 或采用唯一冲突重试。
- Document 当前版本由 `active_version_id` 指向同文档版本；schema FK 防止跨文档指向。ADR-006 要求新版本完成校验后再切换当前版本，失败保留旧版本。

### FileObject

- `id BINARY(16)` 主键；tenant、storage_key、`sha256 BINARY(32)`、byte_size、media_type、storage_status、创建/更新/清理时间。
- 唯一键 `(tenant_id,id)`、`(tenant_id,sha256)`、`storage_key`；tenant FK。唯一 hash 键即同租户并发去重仲裁点，不支持跨租户复用。
- 不存在物化引用计数。物理设计明确所有保留的 DocumentVersion（含历史版）都是有效引用；新增引用和清理 claim 锁同一 FileObject 行，只有 AVAILABLE 可加引用，DELETING 禁止新增，清理持锁复查引用后才可转 DELETING。
- **映射缺陷：** `FileObjectEntity.sha256` 当前为 UUID，而数据库列是 32 字节。`UuidBinaryTypeHandler` 只编码/解码 16 字节；FileObject Mapper 不能用当前类型可靠读写 SHA-256。DocumentVersionEntity.sourceSha256 同样映射 UUID 对 BINARY(32)。开发前必须修正为明确的 32-byte 类型/handler 与参数绑定，并加真实 MySQL 往返测试；不应把 SHA-256 截断为 UUID。

### ProcessingTask、Chunk、ChunkVector

- ProcessingTask 有 UUID 主键、tenant、resource_type/resource_id、task_type/status/stage、lease/heartbeat/attempt/checkpoint/error 等字段；tenant 与可空 creator 外键。资源是多态列，无 DocumentVersion 专属 FK。schema 有领取索引 `(status,available_at,lease_expires_at,priority)` 与租户状态索引。表结构支持后续任务，但没有 Worker/领取 SQL/状态转移实现。
- Chunk 以 DocumentVersion 为父资源，含 chunk_index、LONGTEXT 内容、hash/token_count/metadata；版本内 index 唯一，FK RESTRICT。
- ChunkVector 复合主键 `(chunk_id,embedding_profile_id)`，保存索引状态/时间/错误；分别 FK 到 Chunk/Profile，RESTRICT。现有独立 Qdrant API 不会自动维护这些表。

### KnowledgeBase 与 KnowledgeBaseMember

- KnowledgeBase 使用 `(tenant_id,id)` 唯一复合键供 Document FK，tenant/name 唯一；KB creator、tenant、embedding profile 外键均 RESTRICT。当前 schema 还有 `is_deleted` V2 字段及 `deleted_at`。KB 逻辑删除不会自动删除 Document 或 KB member。
- KB member 的复合 FK `(tenant_id,knowledge_base_id)` → KB `(tenant_id,id)`，`(tenant_id,user_id)` → TenantMember；KB/User 唯一键与 creator FK。此关系可用于服务端授权查询，但权限实现必须依 ADR-003。
- 所有涉及资源删除的 FK 都是 RESTRICT；Document/Version/FileObject 不可按简单物理级联删除。

## 3. 可复用能力与缺失能力

可复用：KnowledgeBase 的 DTO/Controller/Service 分层、MyBatis-Plus 分页插件、统一 ApiExceptionHandler、UUID(16) handler、Flyway/事务、现有 Document/FileObject/DocumentVersion Mapper，以及 `frontend/src/api/document.ts` 预留 multipart 路径和字段。Spring Web multipart 依赖及 20MB 单文件配置已存在（请求上限 25MB）。

尚无：Document REST/业务服务、文档权限查询、文档逻辑删除、文件存储 SPI/本地存储实现、路径安全、格式与签名校验、SHA-256 32-byte mapping、FileObject 去重仲裁、引用和清理协议实现、版本分配/切换/历史查询、上传补偿、后台物理清理、审计及对应自动化集成测试。

前端 `DocumentView.vue` 提供文件选择 UI（`.pdf,.doc,.docx,.txt,.md`，20MB 客户端检查，最多选五个），但上传目前调用 pending 的 `/documents/upload`，传 `file/knowledgeBaseId/tenantId/category`；knowledgeBaseId 当前硬编码 `default`，tenantId 来自前端 store。列表/删除也是真实发请求的预留调用，没有对应后端 Controller。状态/Chunks 页面存在，但文档处理和文档级 chunk 浏览未接入。`registry.ts` 的 document.upload/list/delete 均 pending；Vector add 是独立可用功能，不是上传实现。

依赖中没有专用文件解析或对象存储库；第一阶段不应引入解析器。Spring Multipart 已能接收文件，本地文件 IO 可用 JDK 实现并封装存储接口。

## 4. 第一阶段范围与边界

### A. Document 基础管理

- 新建、分页、详情、修改展示元数据、逻辑删除；列表必须限定 KB，状态筛选/租户限制按权限上下文执行。
- Document 创建必须验证目标 KnowledgeBase 存在、未逻辑删除、tenant 匹配及调用人拥有权限。认证未接入期间不能信任请求 tenantId/userId；开发测试上下文必须明确标记为临时，不能宣传为生产隔离。
- 删除只写 Document `deleted_at` 与状态/更新时间（具体状态枚举须遵循现有状态决策）；不物理删 DocumentVersion、FileObject、Chunk、向量。ADR-012 要求立即停止检索，并由可重试后台任务完成物理清理；此阶段没有 Worker 时，删除接口只能建立清理意图/明确暂缓后台清理，不能声称清理闭环完成。

### B. 上传和存储

- 保持 ADR-005：本地文件系统 + 存储抽象；文件名不作为物理路径；服务端基于内容计算 SHA-256；首版允许 PDF/DOCX/TXT/Markdown，校验扩展名及实际内容类型，限制体积。
- tenant 内 SHA-256 唯一复用，不跨租户去重；FileObject 与版本通过 FK 形成引用。历史版本仍引用文件。
- 当前 `document.ts` 字段和目标业务存在差异：`category` 不对应当前 Document/FileObject/Version 字段；`tenantId` 不应作为授权依据；还缺 displayName/documentId/new-version 语义。API 对接时应根据实际业务定稿这些字段，不应沿用 `default` KB。

### C. 版本管理

- 首次上传创建 Document、FileObject 与 DocumentVersion(version_no=1)，Document 初始 active_version_id 为空；文件和 DB 均持久成功后创建 UPLOADED 版本。当前没有解析/处理，本阶段不能宣称它可检索或已完成。
- 新版本先锁 Document 并验证非删除、授权；读取 MAX(version_no)+1 于锁内生成，唯一键作为最终保护；建立 FileObject 引用及新版本。失败不更新旧 active_version_id；后续处理验证成功才激活并将旧版本 retired，符合 ADR-006。
- `status` 的准确状态映射仍需遵循 ADR-009 阶段和实际业务职责：Document 是业务资源生命周期，DocumentVersion 是版本产物状态，ProcessingTask 是执行尝试。不要把全部处理阶段塞入单一 Document status。

## 5. 推荐接口设计（候选，待实现及契约确认）

沿用 `/api` 前缀、camelCase、裸 DTO/分页 DTO、`{timestamp,error,message?}` 错误体、`page` 从 1 开始/默认 20/最大 100 的 ADR-010 规则；无需另造统一响应 envelope。认证尚未完成时任何 tenant/user 入参仅是临时开发上下文。

| 方法/路径 | 请求 | 成功响应建议 |
|---|---|---|
| `POST /api/documents/upload` | multipart: `file`, `knowledgeBaseId`, 可选已有 `documentId`（新版本）；上传用户/tenant 从可信身份取得，未有认证时由明确的临时开发上下文补齐；展示名可选 | 201 Document DTO + 首个 Version DTO/状态。具体是否一次请求同时创建 Document 与 Version需落实；兼容现有页面时须删除 `category` 或明确存储字段映射 |
| `GET /api/documents` | `knowledgeBaseId` 必填；`page`、`size`；可选 `status` | `{items,total,page,size}`，Document DTO 不暴露 storage path |
| `GET /api/documents/{id}` | 路径 id | Document DTO + 明确的当前版本摘要，不返回文件物理路径 |
| `PUT /api/documents/{id}` | `{displayName}`（仅基本信息） | 更新后 Document DTO |
| `DELETE /api/documents/{id}` | 路径 id | 204；写逻辑删除和清理意图，不同步删除文件/Qdrant |
| `GET /api/documents/{id}/versions` | page/size 或完整历史（规模需定） | 按版本号倒序的版本 DTO；不暴露本地路径 |

首个上传端点沿用现有 `/api/documents/upload`，列表、删除路径也遵循前端预留路径。详情、更新和版本路径需在开发时补入契约/registry。成功状态码和版本响应字段应在首次实现时固定并用真实 HTTP 测试验证。

## 6. 文件上传、hash 去重与一致性方案

1. 校验 KB 权限、文件大小、允许格式、文件名长度及内容；文件名只作展示元数据。流式落到服务端 staging 目录同时计算 SHA-256(32 bytes)，不将完整大文件全量读入内存。
2. 为同租户 hash 取得 DB 唯一仲裁：先查 FileObject；并发冲突由 `(tenant_id,sha256)` 唯一键裁决，冲突方重查并复用 AVAILABLE 对象。必须避免把 32-byte hash 放进 UUID 属性。
3. 参照 physical-design 已确认协议，在短事务内锁定同一 FileObject 行；仅 AVAILABLE 可新建版本引用。DELETE/清理状态不允许被复用。未确认状态下不得自行创建额外引用计数。
4. 文件系统与 MySQL 无共同事务。先以不可预测 storage_key 写 staging，原子 move 到最终受控目录，再在 DB 事务登记/关联 FileObject + Document + Version；若事务失败，补偿删除本次新写的孤儿文件。hash 命中既有对象时不删共享文件。原子 move 不支持的跨盘路径需返回失败或经批准采用分阶段复制；不能暴露路径。
5. 进程崩溃仍可能留下 staging/最终 orphan；定时 reconciler 需基于状态和 DB 引用安全清理。ADR-005/012 与 schema 有清理状态/时间，但没有完整失败状态、退避/补偿字段策略，必须由清理阶段实现并可对账。不得在 DB 事务提交前删已复用对象。
6. 清理任务与新引用使用同一行锁。清理事务重查所有保留 DocumentVersion 引用，无引用才原子设 DELETING；文件删除成功再写 DELETED/deleted_at；失败留可重试状态和同一 storage_key。重复物理删除视为幂等成功。文件不存在也视为成功，数据库写回失败时按同一 key 重试。

## 7. 版本、事务与删除语义

- 首次创建时 Doc.active_version_id 为 NULL，避免循环外键阻碍插入；插入版本成功后保持待处理/上传态。只有后续处理和校验通过的版本才切换 active_version_id。
- Document 行 `SELECT ... FOR UPDATE` 串行化同一文档版本号分配；版本唯一索引兜底。保存新版本及文件引用于一事务；出错 rollback 不更改旧版本和 active_version_id，并按共享引用规则决定是否补偿新 staging 文件。
- Document 逻辑删除只写 `deleted_at`（及确定的生命周期状态）并原子写入清理意图/任务；关联版本、文件对象、chunk/vector 保留。权限/检索查询必须 join 当前未删除 Document/活动版本作为权威门禁，Qdrant filter 不能代替此检查。
- DocumentVersion 当前无删除字段。ADR-006 将版本作为追溯、重建和引用事实，citation FK 可引用版本、chunk，FileObject 清理规则也把历史版本视作引用。因此不建议业务物理删除版本；是否允许按保留期做受限清理，需 ADR/产品规则确认并满足引用 FK。

## 8. 权限和租户隔离

- 正式实现遵循 ADR-003：使用认证主体得 userId，查询 TenantMember；OWNER/ADMIN 有租户内 KB 管理权限；MEMBER 必须有 KnowledgeBaseMember 授权。每次以 KB id + tenant 进行服务端归属检查，并在列表、上传、详情、修改、删除、版本访问执行一致授权。
- 不把 `tenantId`、`createdBy` 从浏览器表单视为可信身份。JWT/Spring Security 尚无实现。开发阶段若以临时 header/config/request 参数便于验收，必须限定仅 dev profile、显著标记且不可部署为生产安全措施；不得擅自修改 ADR 身份策略。
- KnowledgeBase 当前尚未构成认证授权闭环；Document 首阶段上线前需正式认证中间件及成员授权服务，或开发阶段仅用于隔离测试环境。

## 9. 前端对接

- `DocumentView.vue` 已有 Element Plus 文件选择 UI、20MB 前端校验、上传/刷新/删除调用及独立向量写入区域；上传 UI 当前支持多文件选择但只取第一个文件发送，KB id 硬编码 `default`，tenant id 来自 store。需改为真实可选 KB 与逐文件上传/进度/结果反馈，避免伪造成功。
- `api/document.ts` 预留 upload/list/delete/status/chunks 函数；registry 的 document.upload/list/delete 与 chunk.list/delete 为 pending。第一阶段只实现 Document upload/list/detail/update/delete 与 version history 后，同步 TypeScript DTO/API、registry 状态和 Workspace contract；Chunk endpoints 保持 pending。页面状态仍可标记 partial，不能因上传可用而暗示解析完成。

## 10. 集成测试计划

使用专用 MySQL `rag` 集成环境和唯一测试标识，创建依赖的 User/Tenant/TenantMember/KB/KBMember，不用 H2、Mock Mapper 或内存文件替代。

1. Document CRUD、按 tenant/KB 列表分页、跨 tenant/未授权拒绝、逻辑删除后不可见及原始行存在。
2. Multipart 上传：允许/拒绝扩展名与 MIME、超大小、空文件、hash 与实际文件字节一致、storage path 不出现在响应。
3. 同 hash 同租户并发上传只产生一个 FileObject；跨租户相同内容不复用；DB 唯一冲突竞争后重查 AVAILABLE。
4. FileObject SHA-256 的 BINARY(32) round-trip；重复 upload/rollback 时物理文件和 DB 引用正确，失败清理不删既有对象。
5. 首版本号 1、后续版本递增、并发同文档新版本唯一；失败创建不改变旧当前版本；切换版本验证同文档 FK。
6. 有活动/历史版本引用时 cleanup 不得进入 DELETING；无引用时状态锁竞争、文件删除和重试幂等；Document 删除保留版本及源文件引用。
7. 事务使用 TransactionTemplate/故障注入在实际 DB 验证 rollback；文件系统测试用隔离临时目录并清理。
8. API 参数校验、错误体、状态码和 pagination envelope，最后运行既有 MySQL/Qdrant 回归。

## 11. 建议开发顺序

1. 修复并测试两处 BINARY(32) SHA-256 Entity/TypeHandler 映射；固定 byte[]/十六进制输入边界，不动 schema。
2. 实现基于配置根目录的本地 Storage SPI、路径安全、staging/原子 move、hash/类型/大小校验和单元/文件系统测试。
3. 实现 KB/tenant/user/成员授权查询（认证接入前明确开发边界）和 Document CRUD/分页/逻辑删除，先通过真实 MySQL HTTP tests。
4. 实现 FileObject 唯一键仲裁、行锁、并发去重、补偿/孤儿处理；验证并发真实 MySQL。
5. 实现 DocumentVersion 首版/递增、历史读取、状态及 active_version 切换约束；验证失败 rollback 与旧版本保留。
6. 接通 multipart Controller/API/前端类型和 registry；完成上传闭环测试。只创建 UPLOADED 版本，不做解析/Chunk/Embedding。
7. 明确并实现清理 claim/retry/对账；否则标注为待实现，不宣称文件生命周期完整。

## 12. 首阶段目标代码范围

后端预期新增 DocumentController、DocumentService、Document/Version request/response/page DTO、授权查询服务（或与既有 KB service 的窄接口）、FileStorage SPI/LocalFileStorage、upload validation/hash utilities，以及 FileObject/DocumentVersion 需要的 mapper 查询。修正 FileObjectEntity/DocumentVersionEntity 的 SHA-256 映射及相应 TypeHandler/测试。无须新增表，除非以下待定事项确认后确实发现结构不足，再走新增 Flyway migration 和基准文档更新。

前端预计修改 `src/api/document.ts`、`src/types/domain.ts`（Document/Version DTO）、`src/views/document/DocumentView.vue`，可能增加 KB 选择/API 复用；同步 `registry.ts` 仅针对通过验收接口。测试新增真实 MySQL integration tests、文件系统测试和授权/并发/补偿覆盖。KnowledgeBase CRUD 不应重构。

## 13. 结构充分性与待确认事项

现有表关系总体足以表达 Document、多个不可变版本、同租户 hash 去重、活动版本和后续 chunk/vector 产物；Document 与 KB 的租户复合 FK、版本与源文件的租户复合 FK 有助于防错配。**不需要为基础上传与版本管理先行重建数据库。** 但有三个实施风险：

1. Entity 将两个 BINARY(32) 哈希字段错误映射为 UUID(16)，必须只修代码类型映射并验收；确认项目 UUID handler 仅用于 BINARY(16)。
2. `ProcessingTask.resource_id` 是无 FK 的多态引用；这是 schema 设计事实，不可误称数据库保证任务目标存在。
3. FileObject 没有 ref_count（设计明确不物化），清理依赖行锁 + 查询所有历史版本引用；`storage_status` 失败状态、重试退避及人工补偿字段/策略 PENDING。

尚需决策（不在本方案替用户裁定）：

- ADR-005 标记 PENDING 的失败状态/重试退避/人工补偿及存储备份恢复策略；上传最大大小最终值、真实 MIME/杀毒要求。
- 首个 multipart 请求是否同时创建 Document 与 Version、是否支持 `documentId` 表示新版本；`category` 的去留/语义；文件名规范化及 displayName 默认规则。
- 文件已在 staging/最终目录但数据库写失败时的 orphan reconciliation 时限和调度；失败清理状态是否需要新增列。
- KB/Document/Version 具体状态枚举及上传后是否建立 ProcessingTask（处理 Worker 超出本轮，但任务表可表达）。
- 版本是否可由用户主动删除；历史版本/Document 删除后的保留期限和物理清理边界。当前 FK 与 ADR 不支持无条件物理删除。
- 新版本 READY/校验成功的定义及是否首阶段只有 UPLOADED、不切换 active_version；ADR-006 要求验证后才切换。
- 正式认证/JWT 接入时间及开发期身份输入方式；生产 API 必须符合 ADR-003，客户端 tenantId 不得作为授权依据。
- 文档列表是否包括删除记录、版本历史分页/筛选、最大页大小按 ADR-010 默认 20 最大 100。

本轮未修改任何业务代码，也未执行文档上传业务，因为本轮授权范围仅为检查与方案。

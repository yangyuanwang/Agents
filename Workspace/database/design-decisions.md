# 数据库设计决策汇总

状态：**决策索引与确认记录。**
本文件记录当前数据库设计中已确认的约束及尚未解决的实现事项。具体字段、类型、键、索引和关系以
`physical-design.md` 为准；业务实体与逻辑关系以 `logical-model.md`
为准。ADR-001 至 ADR-013 的正式状态不由本文改变。

## 1. 已确认的架构决策

### 1.1 身份、租户与权限

-   User 全局唯一；TenantMember 记录
    tenant_id/user_id/role/status；用户可加入多个租户。
-   TenantMember `(user_id,tenant_id)` 唯一。
-   Tenant 角色 OWNER/ADMIN/MEMBER；OWNER/ADMIN
    自动管理租户下知识库，MEMBER 需显式 KB 授权。
-   身份来自可信 JWT/服务端上下文，不信任前端传入 userId/tenantId。
-   每次敏感访问重新执行当前权限校验；数据库 FK 不替代动态授权。

### 1.2 认证

-   Spring Security + JWT；用户名或邮箱 + 密码。
-   Access/Refresh Token 时效、轮换撤销、密码哈希算法参数仍 PENDING。
-   密码/token 不写日志。

### 1.3 Qdrant 与 EmbeddingProfile

-   Qdrant 使用共享 Collection + Payload Filter；服务端强制 tenantId +
    knowledgeBaseId。
-   不可变 EmbeddingProfile 对应单 Collection；配置变化创建新
    Profile/Collection；同 Profile 多 Collection Generation 不支持。
-   Chunk ID 与对应 Profile Collection 中 Qdrant Point ID 相同；MySQL
    不冗余保存 point_id。
-   EmbeddingProfile 归属规则：SYSTEM 预置 + TENANT 自建；首版仅开放
    SYSTEM 创建；SYSTEM 可被多个租户 KB 引用；TENANT 仅同租户 KB
    可引用。
-   Profile 增加 scope、owner_tenant_id；密钥只保存服务端 Secret
    引用，不存明文；Collection 名称服务端生成和管理。
-   Profile SYSTEM/TENANT OR 规则的数据库约束表达方式仍
    PENDING，服务层授权检查必须执行。

### 1.4 文件与文档版本

-   首版本地文件系统 + Storage 抽象；未来可替换 MinIO/S3；API
    不暴露本地物理路径。
-   支持 PDF、DOCX、TXT、Markdown。
-   同租户 SHA-256 相同内容复用同一 FileObject/物理文件；跨租户不复用。
-   不物化 ref_count。
-   所有仍保留的 DocumentVersion（含非活动历史版本）均为有效引用。
-   上传新增引用与清理 claim 锁同一 FileObject 行；仅 AVAILABLE
    允许新增引用。
-   清理事务锁行后重新检查所有有效引用；无引用才原子切换
    DELETING；DELETING 禁止新增引用。
-   清理失败保留原 hash 唯一行，不允许同 SHA 新建绕过。
-   物理文件不存在视为删除成功；删除成功但 DB 写回失败时重试同一
    storage_key。
-   清理完成后新上传重新仲裁，不复用正在删除对象。
-   Document
    逻辑删除立即停止检索，但不会自动使仍保留的历史版本引用失效。

### 1.5 ChunkVector

-   ChunkVector 是独立实体/独立表。
-   自然联合主键 `(chunk_id,embedding_profile_id)`。
-   不保存独立 `point_id`，由 chunk_id 按统一 UUID 编码派生。
-   状态扫描索引 `(embedding_profile_id,index_status,updated_at)`。
-   `updated_at` 字段存在已确认；更新时间语义 PENDING。

### 1.6 DocumentVersion、Chunk、Citation

-   DocumentVersion 冗余保存创建时确定且不可独立修改的 tenant_id。
-   DocumentVersion `(document_id,id)` 唯一；Document
    `(id,active_version_id)` 通过复合 FK 指向 DocumentVersion
    `(document_id,id)`；active_version_id 可 NULL。
-   KnowledgeBaseMember tenant_id NOT NULL；两个租户复合 FK 及
    `(knowledge_base_id,user_id)` 唯一。
-   Chunk 与 DocumentVersion、Citation 与 Document/Version/Chunk
    的复合来源链约束见 `physical-design.md`。
-   Chat/Citation 不为增加 FK 而盲目添加 scope 冗余字段。
-   Citation 保留必要历史快照；快照字段、脱敏、保留期限
    PENDING。源文件和 Chunk 原文访问始终重新授权。

### 1.7 任务、事务与异步

-   ProcessingTask 使用 MySQL 任务表 + Spring Worker +
    定时扫描；首版无消息中间件。
-   Task status 与 stage 分离；lease 过期可重领；幂等恢复。
-   ProcessingTask 保留 resource_type + resource_id
    多态引用；资源存在、类型和租户归属由服务层校验。
-   MySQL 与
    Qdrant/文件系统无共同事务；状态推进依赖幂等、补偿、重试和对账。
-   新版本失败时旧版本继续检索；新版本成功并校验后才切换活动版本。

### 1.8 API、安全、运维与容量

-   统一响应 code/message/data/traceId；HTTP status 正确；统一异常处理。
-   普通列表默认 20、最大 100。
-   重要管理操作审计；默认认证、服务端权限、上传限制、限流；Actuator
    收敛。
-   Docker Compose 单机部署；DEV/TEST/PROD 独立。
-   Actuator + Micrometer；后续按需 Prometheus/Grafana。
-   MySQL/文件/Qdrant 联合备份恢复校验。
-   首阶段容量目标：100 租户、1000 用户、20 路 Chat 并发、5 Worker
    并发；P95 500ms 是规划目标，不是已验证性能。

## 2. 已确认的物理关系决策

1.  KnowledgeBaseMember:
    -   `(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`
    -   `(tenant_id,user_id)` → TenantMember `(tenant_id,user_id)`
    -   `UNIQUE(knowledge_base_id,user_id)`
2.  Document/DocumentVersion:
    -   `UNIQUE(document_id,id)` on DocumentVersion
    -   `(id,active_version_id)` → DocumentVersion `(document_id,id)` on
        Document
    -   active_version_id nullable; 循环 FK 后置添加
3.  DocumentVersion:
    -   `(tenant_id,document_id)` → Document `(tenant_id,id)`
    -   `(tenant_id,source_file_id)` → FileObject `(tenant_id,id)`
4.  Chunk/Citation:
    -   `UNIQUE(document_version_id,id)` on Chunk
    -   Citation `(document_id,document_version_id)` → DocumentVersion
        `(document_id,id)`
    -   Citation `(document_version_id,chunk_id)` → Chunk
        `(document_version_id,id)`
    -   Citation `message_id` → ChatMessage `id`
5.  FileObject:
    -   `(tenant_id,sha256)` 唯一
    -   不物化 ref_count；有效引用以所有仍保留 DocumentVersion 为准
    -   上传引用创建与清理 claim 使用同一行锁/状态仲裁

## 3. 仍然 PENDING 的事项

### 3.1 认证与授权

-   Access/Refresh Token 时效、轮换、撤销与登出策略。
-   密码哈希算法/参数。
-   KB 角色枚举、权限矩阵、系统管理员边界。
-   动态授权细节与审计要求。

### 3.2 数据库实现

-   MySQL 版本、迁移工具、MyBatis/JDBC/JPA 的最终数据访问方式。
-   UUID 版本与 BINARY(16) 字节序实现约定。
-   CHECK、复合 FK、索引长度、ON DELETE/ON UPDATE 在目标 MySQL
    的能力和最终行为。
-   所有字段长度、字符集、排序规则、时间精度及具体状态枚举。
-   Document 活动版本循环 FK 的迁移创建顺序（设计上后置添加已确定，DDL
    语法仍需验证）。
-   Profile SYSTEM/TENANT OR 引用规则的数据库物理表达。
-   FileObject
    清理失败状态/错误字段、退避、租约、人工补偿字段及时间列精确定义。
-   FileObject `storage_key` 唯一范围、SHA-256 物理编码、MIME 字段长度。
-   ChunkVector `updated_at` 更新时间语义、状态枚举、错误字段取舍。
-   ProcessingTask claim SQL、重试上限、租约时长、checkpoint
    格式、任务类型枚举。
-   ChatMessage STREAMING 状态、断线恢复、SSE 持久化粒度。
-   Citation 快照字段、脱敏、保留和删除策略。
-   TenantQuota 配额种类、单位、周期、扣减与重算规则。
-   AuditLog action 字典、保留期限、脱敏策略。
-   FK 删除行为、索引按实际 SQL/EXPLAIN 的最终验证。

### 3.3 跨系统和运行参数

-   Profile 迁移增量同步水位、失败回滚、验证阈值、旧 Collection 保留期。
-   文件清理宽限期、orphan 对账频率、最大文件大小。
-   Chat/文件/向量/审计保留与归档策略。
-   ADR-013 容量目标的压力测试与性能验证。
-   联合备份恢复演练与对账告警。

## 4. 决策使用规则

-   本文件的已确认项不得被 Codex 重新询问或降级为 PENDING。
-   `physical-design.md` 是物理字段、关系、键和索引的唯一正文。
-   `logical-model.md` 是逻辑实体和业务关系基准。
-   ADR-001 至 ADR-013 保持原 Accepted 状态；本文不替代 ADR。
-   PENDING
    只能在取得明确决策或技术验证证据后关闭；不得为了通过检查而擅自批准。

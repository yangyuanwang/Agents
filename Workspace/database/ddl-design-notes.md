# MySQL DDL 草案设计说明

状态：设计说明，配套 `ddl-draft.sql`；不是获批生产 schema。SQL 文件仅为文本草案，未执行。

## 数据库目标候选

- **候选版本：MySQL 8.0.16+**，以支持 CHECK 实际执行；生产版本仍需确认。DDL 语法和功能兼容性未在数据库实例上验证。
- 存储引擎 InnoDB；字符集 utf8mb4；排序规则 utf8mb4_0900_ai_ci（MySQL 8.0 候选）。如需区分大小写的登录名/文件 key，应另设列级排序规则或规范化键，规则 PENDING。
- 时间统一 UTC DATETIME(3)，由应用写入；数据库连接/session 时区应固定 UTC。

## User 登录标识

`username` 与 `email` 在草案中均可 NULL，分别唯一；“至少一个非空”尚未冻结。候选做法为目标 MySQL 确认可执行 CHECK 后增加 `(username IS NOT NULL OR email IS NOT NULL)`，或由服务层在创建/更新事务中校验。大小写、邮箱规范化规则继续 PENDING；本草案不擅自强制。

## UUID 规范

所有 UUID 主键/引用使用 BINARY(16)。应用层将 RFC 4122 UUID 的 16 个字节按网络序直接编码，不使用 MySQL UUID_TO_BIN 的 time-swap 选项；反向转换保持相同字节次序。Chunk UUID 以相同字节值作为 Qdrant UUID Point ID。草案未定义自动生成默认值，服务端生成 ID。

## 物理表达与边界

ChatSession FK 仅保证 Tenant、User、KnowledgeBase 分别存在，并保证 KB 属于所存 tenant；不保证用户当前为 ACTIVE TenantMember，也不保证 MEMBER 有效的 KnowledgeBaseMember 授权。两者必须由服务层在每次访问时实时校验，普通 FK 不构成完整授权保障。

- 无符号整数用于计数/字节；`BIGINT UNSIGNED` 文件长度/配额，`INT UNSIGNED` 维数/版本/尝试数；金额不适用。
- JSON 仅用于配置快照、checkpoint、审计 details/非关系元数据，禁止存密钥。敏感密钥只保存 Secret 引用。
- 字符串长度及状态值仍是物理候选，需跟产品校验确认。
- Profile 的 `scope`/`owner_tenant_id` 使用 CHECK 保证 SYSTEM→NULL、TENANT→非 NULL。`collection_name` UNIQUE。SYSTEM 与 TENANT Profile 被 KB 引用的 OR 规则不能由草案中的 `active_embedding_profile_id` 单列 FK 强制；该 FK 只保证 Profile 存在。首版只创建 SYSTEM Profile、TENANT Profile 仅同租户 KB 可引用，须服务层在 Profile 指派/读取时验证。若后续要求数据库强制，需选用额外引用列/复合路径并另行确认，当前不新增实体。
- KnowledgeBaseMember 的两个复合 FK 依赖 KnowledgeBase `(tenant_id,id)` 和 TenantMember `(tenant_id,user_id)` 唯一键；FK 不保证成员 ACTIVE 或 role，服务层每次授权检查。
- Document 活动版本复合 FK 后置添加，避免建表循环依赖；DocumentVersion `(document_id,id)` 为父唯一键。
- Chunk/Citation 来源链使用复合 FK；普通独立 FK 不能替代组合来源一致性。
- FileObject `(tenant_id,sha256)` 保持唯一；不物化 ref_count。上传新增引用与清理 claim 同事务锁定同一行；仅 AVAILABLE 可新增引用；清理锁行后重新检查所有保留版本引用，无引用才置 DELETING。删除在事务外执行，失败保留唯一行；NotFound 视成功；数据库写回失败用原 storage_key 重试。
- 所有 FK 草案采用 RESTRICT/RESTRICT，避免数据库级联绕过软删除、历史保留和异步清理。最终 ON DELETE/UPDATE 需业务保留策略审核。

## 外键删除动作

草案中的每个 FK 暂以 `ON DELETE RESTRICT ON UPDATE RESTRICT` 作为候选动作。依据是逻辑删除、历史版本保留、异步外部清理、Citation 快照与独立审计保留；级联删除可能绕过这些流程。逐 FK 清单见验证报告。RESTRICT 尚未批准，须按实体保留/清理策略逐项确认。

## 建表顺序

1. `user`, `tenant`。
2. `tenant_member`, `embedding_profile`。
3. `knowledge_base`, `file_object`。
4. `knowledge_base_member`, `document`。
5. `document_version`，随后 ALTER 添加 `document` 活动版本 FK。
6. `processing_task`, `chunk`。
7. `chunk_vector`, `chat_session`。
8. `chat_message`，再 `citation`。
9. `audit_log`, `tenant_quota`。

## 类型与关系核对

复合 FK 两侧均采用 BINARY(16)，顺序按 FK 声明与父 UNIQUE 键一致；字符字段未参与复合 FK。Profile owner FK 单列均 BINARY(16)。需要在目标 MySQL 上核对 CHECK、复合索引长度、索引自动创建行为和命名限制。此说明未将 SQL 连接或执行作为验证方式。

## 草案不能代表的运行时事实

DDL 不能强制租户/KB 动态授权、Profile OR 授权、FileObject 与磁盘内容一致、Qdrant 与 MySQL 原子一致、Task 多态 resource 有效或状态机合法迁移。它们由应用事务、可信身份校验、幂等 adapter、任务重放及对账实现。

# MySQL Schema 部署验证报告

## 目标与执行环境

- 目标：本机开发 MySQL `127.0.0.1:3306`，schema `rag`。
- 服务器版本：MySQL 8.0.29；执行用户 root（凭据不写入仓库）。
- 执行前 `rag` 不存在；无既有库表或业务数据。项目 Compose 仅定义 Qdrant，不包含 MySQL。
- 使用 `schema-mysql.sql`；仅执行建库和建表语句，没有 DROP/清理/迁移。

## SQL 静态检查

- DDL 包含 17 个 CREATE TABLE，全部指定 InnoDB、utf8mb4 与 utf8mb4_0900_ai_ci。
- 包含 31 个外键，ChunkVector 使用联合主键 `(chunk_id,embedding_profile_id)`，没有独立 point_id 或 ref_count；Citation 没有 score。
- Document 活动版本 FK 由建表后的 ALTER 添加。
- MySQL 8.0.29 支持草案所用 CHECK。Citation 的保留字列 `rank` 使用反引号。

## 实际执行结果

首次执行在 `citation.rank` 处因未引用 MySQL 保留字 RANK 而停止；此前 14 张空表及活动版本循环 FK 已成功创建。修正 DDL 后，对已存在的 `rag` 仅补建 Citation、AuditLog、TenantQuota；没有删除或覆盖既有对象。最终执行成功。

## 数据库元数据验证

执行后通过 information_schema 查询：

- BASE TABLE 数：17。
- REFERENTIAL_CONSTRAINTS 数：31。
- 17 张表均有 PRIMARY KEY；ChunkVector 主键包含两列（chunk_id、embedding_profile_id）。
- 所有预期 FOREIGN KEY、UNIQUE 和普通索引均已创建；CHECK `ck_profile_scope_owner` 存在。
- Document 活动版本外键 `fk_doc_active_version` 已存在。
- 未检查业务数据行为；新库当前无业务数据。

## 尚未解决的非阻断事项

- 后续 Flyway V2 为 knowledge_base 增加 `is_deleted TINYINT NOT NULL DEFAULT 0`；部署现存开发库时只执行增量 ALTER，不重建表或删除数据。`deleted_at` 保留为逻辑删除时间。

- MySQL 8.0.29 已验证该 DDL 可执行；其他 MySQL 版本未验证。
- `rank` 使用反引号引用；应用 SQL/ORM 查询也必须引用或安全映射该列。
- Profile SYSTEM/TENANT OR 归属、FileObject 同行锁协议及动态权限依赖服务层实现，DDL 单独不能保证。
- CHECK、FK 删除动作、状态枚举及原设计中其他 PENDING 的业务实现细节仍按决策文档保留。
- 本次只验证元数据结构，不代表应用实体/Mapper 已实现或 CRUD 测试通过。

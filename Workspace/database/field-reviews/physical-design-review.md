# 全量数据库设计：第二轮关系修订归档

状态：本轮关系一致性修订已归档。本文记录候选设计，不是 DDL、实现状态或新增 ADR。

## 1. 评审范围

只修订全量候选稿中受跨实体关系、租户隔离、外键完整性、ChunkVector 状态和 FileObject 并发生命周期影响的部分；不重做 17 实体字段全集、不增加逻辑实体、不修改 ADR 或逻辑模型。

## 2. 三项用户确认决策

### EmbeddingProfile：系统预置 + 租户自建

**已确认：** Profile 增 `scope` 与 `owner_tenant_id`。SYSTEM 时 owner 为 NULL；TENANT 时非 NULL。首版仅开放系统级创建，但保留租户自建模型。系统 Profile 可由多个租户 KB 引用；租户 Profile 仅同租户 KB 可引用。Profile 不可变且一 Profile 对应一 Collection；`collection_name` 服务端生成且全局唯一。密钥使用服务端 Secret 引用/配置键，禁止明文入库。Qdrant 查询继续强制 tenantId + knowledgeBaseId Payload Filter。

**推导建议：** 对 scope/owner 的 NULL 规则加数据库 CHECK（目标 MySQL 能力确认后）；以 `(scope,owner_tenant_id,id)` 提供归属候选键。系统/租户归属的 OR 引用不能由单个普通 FK 表达：系统 Profile 允许任意 KB，租户 Profile 要求 owner 与 KB tenant 相等；候选实现为两条可空引用路径及互斥约束，或单 FK 加服务层不可绕过的引用授权校验。候选稿明确采用组合归属字段并由服务层校验跨租户 OR；在不引入额外实体的约束下，物理级单一 FK 不能完整表达该 OR。

### KnowledgeBaseMember 租户隔离

**已确认：** 增 `tenant_id NOT NULL`、创建后不可独立修改；复合 FK `(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`；复合 FK `(tenant_id,user_id)` → TenantMember `(tenant_id,user_id)`；唯一 `(knowledge_base_id,user_id)`。父表需有对应唯一键。服务层实时校验成员 ACTIVE、租户角色及授权有效性。OWNER/ADMIN 自动拥有租户 KB 管理权限；MEMBER 需显式授权。FK 不取代动态判权。

### FileObject 同行锁仲裁

**已确认：** `(tenant_id,sha256)` 唯一、不跨租户复用、不物化 ref_count。所有仍保留的 DocumentVersion（含非活动历史版本）均为有效引用。新增引用与清理 claim 锁同一 FileObject 行；仅 AVAILABLE 可新增引用；清理事务持锁重查所有有效引用，无引用才原子置 DELETING；DELETING 禁止新引用。清理失败保留原唯一行，禁止同 hash 新建绕过。文件不存在视为删除成功；写回失败时重试同一 storage_key。清理完成后新上传重新仲裁，不复用正在删除对象。

## 3. 已确认与推导关系约束

- **已确认/推导：** DocumentVersion `UNIQUE(document_id,id)`；Document 活动版本 FK `(id,active_version_id)` → DocumentVersion `(document_id,id)`。active_version_id 可 NULL。循环 FK 先建表后添加；激活事务锁 Document、核实版本属于该文档且 READY/校验通过，再原子切换。
- **推导建议：** Document `UNIQUE(tenant_id,id)`；Document `(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`。
- **已确认/推导：** DocumentVersion `(tenant_id,document_id)` → Document `(tenant_id,id)`；`(tenant_id,source_file_id)` → FileObject `(tenant_id,id)`。
- **推导建议：** Chunk `(document_version_id,id)` 唯一；Chunk 的 version FK 指向 DocumentVersion。唯一 `(document_version_id,chunk_index)` 保证版本内顺序。
- **已确认：** ChunkVector PK `(chunk_id,embedding_profile_id)`；不存 point_id；索引 `(embedding_profile_id,index_status,updated_at)`；`updated_at` 字段存在已确认、更新时间语义 PENDING。
- **推导建议：** Citation `document_id` + `document_version_id` 复合 FK → DocumentVersion `(document_id,id)`；`document_version_id` + `chunk_id` 复合 FK → Chunk `(document_version_id,id)`；`message_id` FK → ChatMessage。由此锁定引用链不跨文档/版本。
- **已确认：** ProcessingTask 首版使用 `resource_type + resource_id` 多态引用，不新增实体；资源存在、类型匹配、tenant 归属由服务层校验。
- **已确认：** ChatSession/Citation 不为追求 FK 盲目增加冗余 scope 字段。session 与 Tenant/User/KB 的存在关系可由普通 FK 保证，用户成员关系、KB 授权及 Citation 与 session 来源的租户一致由服务层实时校验。

## 4. 数据库与服务层责任边界

数据库 FK/UNIQUE 能保证静态行身份、租户复合归属、版本/Chunk 引用链及去重唯一性；不能保证 TenantMember/KBMember 当前 ACTIVE、角色仍有效、用户当前可访问 KB、Profile 的系统或同租户 OR 授权条件、DocumentVersion 业务状态已完成、Qdrant Point 存在或外部文件完整。所有敏感读取、下载、检索和管理操作必须以可信身份重新授权。状态跨系统推进由事务、幂等重试和对账共同保证。

## 5. FileObject 清理事务责任

1. 上传在临时位置写入并计算 SHA-256。
2. 以 `(tenant_id,sha256)` 唯一键仲裁同租户对象；冲突时锁定已有行。
3. 上传引用事务锁定 FileObject 行，只有 AVAILABLE 才允许建立 DocumentVersion 引用，并在同一事务提交。
4. 清理任务锁定相同 FileObject 行，重新检查所有仍保留版本引用；逻辑删除 Document 本身不移除历史版本的有效引用。
5. 仅当关联版本清理完成且不存在有效引用时，在该事务原子置 DELETING/领取清理，随后提交。
6. 事务外用该行原 storage_key 删除物理对象；期间任何新引用因 DELETING 被拒绝。
7. 删除成功或对象已不存在后幂等写回 DELETED。若 DB 写回失败，重试同一 key；若删除失败，保留唯一行并进入可重试失败处理，禁止新建同 hash 绕过。
8. 清理结束后到达的新上传重新以唯一键和锁状态仲裁；不得复用正在删除的物理对象。

失败状态字段、退避和人工补偿仍 PENDING；状态仲裁及锁行协议已确认。

## 6. ChunkVector 与 FileObject 状态结论

ChunkVector 三项决策均保持不变：自然联合主键、不单独保存 point_id、状态扫描索引。`updated_at` 字段存在为已确认，更新时间语义 PENDING。

FileObject 候选状态为 STAGING → AVAILABLE → DELETE_PENDING → DELETING → DELETED。上传写入与数据库登记不具共同事务，失败临时对象需补偿/对账。清理失败状态名、退避与人工修复 PENDING，但失败不得释放唯一 hash 行或允许新记录绕过。

## 7. 仍为 PENDING 的事项

- Profile 唯一键按配置字段的精确组合、scope 检查约束在目标 MySQL 的表达方式，以及系统/租户 OR 引用约束采用何种物理表达（服务层校验边界已明确）。
- FileObject 清理失败字段、退避、人工补偿机制；状态时间字段精确定义。
- Document、DocumentVersion、ProcessingTask 等最终状态枚举；ChunkVector.updated_at 更新时间语义。
- ChatMessage STREAMING 状态及断线恢复；Citation 快照字段/保留/删除；Refresh Token 生命周期与轮换撤销；KB 角色枚举与完整权限矩阵。
- 所有 FK 的最终 ON DELETE/ON UPDATE 行为；MySQL 版本、迁移工具及复合 FK 实现细节。
- ADR 其余明确 PENDING 的任务恢复参数、Profile 迁移水位/验证阈值/保留期、配额、审计保留、容量验证等。

## 8. DDL 阶段条件

关系身份与主要复合键已足以进入**DDL 草案设计**，尤其是父表唯一键、引用列顺序和循环 FK 后置创建顺序已经明确。最终 DDL 冻结前仍须验证目标 MySQL 的 CHECK/FK/index 能力、Profile scope/owner OR 规则表达、ON DELETE 行为和全部状态字段的最终列取舍。当前文档任务不包含 DDL 生成。

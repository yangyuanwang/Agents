# 数据库设计文档导航

## 当前状态

- 当前阶段：全量物理设计候选的第二轮关系一致性修订已归档；下一阶段为 DDL 草案设计，尚未授权执行。

- MySQL 当前只有后端 JDBC 依赖与连接配置；没有业务实体、Repository、DDL、初始化 SQL 或迁移脚本。
- Accepted ADR 已确定目标数据职责；状态和业务模型不能据此声称已实现。
- 本阶段数据库设计产物为 [`logical-model.md`](logical-model.md)：包含 17 个逻辑实体、ER 图、字段类型建议、组合约束/索引候选、生命周期与 PENDING 项；已归档六项逻辑模型确认，包括独立 ChunkVector 表、Profile/Collection 一对一、同租户文件复用、DocumentVersion.tenant_id、状态职责和 Citation 必要快照/重新授权。
- [`logical-model-review.md`](logical-model-review.md)：第三阶段第二步确认准备及结果归档，记录六项用户确认、其结构影响、剩余 PENDING 依赖和下一轮字段评审顺序。
- [`field-reviews/chunk-vector-review.md`](field-reviews/chunk-vector-review.md)：第四阶段第一步 ChunkVector 字段级评审稿；自然联合主键 `(chunk_id, embedding_profile_id)`、不单独保存 `point_id`、状态扫描索引 `(embedding_profile_id, index_status, updated_at)` 已确认。其余字段和实现细节仍待评审；本文不是最终 DDL 或物理表结构。
- [`field-reviews/file-object-review.md`](field-reviews/file-object-review.md)：第四阶段第二组 FileObject 字段级评审稿；分析字段、复用、引用计数、状态及异步清理，建议均待用户确认，不是最终字段基线或 DDL。
- [`physical-design-candidate.md`](physical-design-candidate.md)：数据库第四阶段 17 个逻辑实体全量字段级设计候选；统一列出类型、键、索引、租户约束、状态和 PENDING。作为待审方案，不是最终基线或 DDL。
- [`physical-design-review.md`](physical-design-review.md)：第二轮关系一致性修订归档，记录 Profile 归属、KBMember 租户隔离、活动版本与引用链、FileObject 并发清理及数据库/服务层责任。
- 逻辑模型文档不是最终字段设计或 ER/DDL。ChunkVector 的上述三项字段设计已确认；状态枚举、时间/错误字段取舍、任务关联、外键物理实现等仍 PENDING。其他实体的字段、唯一键、索引、组合外键与 ORM 实现、状态枚举及保留策略仍待后续字段评审。
- 评审文档不重复审议 ADR-001 至 ADR-013，也不改变其 Accepted 状态；逻辑模型确认不构成建表或生成迁移的授权。
- MySQL 具体版本和迁移工具仍 PENDING。

## 数据边界基线

MySQL 保存身份/租户/知识库授权、文档版本与任务、Chunk 完整文本、Chat/引用、审计和配额等关系型业务事实。Qdrant 保存向量及检索 Payload；向量本体不写 MySQL。首版本地文件系统保存原始文件，通过 File Storage abstraction 访问；API 不暴露本地物理路径。

Chunk 是 DocumentVersion 下的稳定文本实体。不要采用旧草案的单一 Chunk `qdrant_point_id`/`embedding_profile_id` 作为最终向量归属；ChunkVector 独立逻辑实体及独立表已确认，其自然联合主键、不单独保存 Point ID、状态扫描索引三项字段设计也已确认。其他字段、状态枚举及外键物理实现仍待评审。文件 SHA-256 同租户复用同一 FileObject/物理文件已确认，不跨租户复用；`ref_count` 是否物化及并发清理协议仍 PENDING。

逻辑模型的组合外键建议用于防止“两个外键各自存在、组合业务归属却不一致”，例如 Document 的 tenant 与 KB、ChatSession 的 tenant/user/KB、Citation 的 document/version/chunk。动态成员状态、软删除状态、当前权限和 Citation 对会话来源的跨域授权仍由服务层在事务/请求中校验。UUID 的 `BINARY(16)`、Java UUID 与 Qdrant UUID Point ID 按同一 16 字节网络序表示，详细约定见逻辑模型；当前尚未进入 Java 实现。

Profile 是向量模型配置，Collection 是 Qdrant 物理集合。一个不可变 Profile 对应一个 Collection；模型变化创建新 Profile/Collection，同 Profile 多 Collection generation 不支持。不同 Profile 迁移的增量同步、水位、验证、切换回滚与旧 Collection 保留细节仍 PENDING。

DocumentVersion 冗余保存创建时确定且不可独立修改的 `tenant_id` 已确认；组合唯一键、外键及 ORM 映射待字段评审。Document、DocumentVersion、ProcessingTask、ChunkVector、FileObject 的状态职责及 Task 租约超期重领/幂等恢复已确认，具体枚举和执行参数待确认。Citation 保留必要历史快照已确认；快照字段/脱敏/保留期待确认，读取源文件或 Chunk 原文始终重新校验当前权限和文档状态。

Document 逻辑删除提交后必须立即阻止检索返回其 Chunk，不能等 Qdrant 异步删除；Worker/清理任务要幂等、可重试、可对账。状态职责：Document=业务资源生命周期，DocumentVersion=版本产物可用性，ProcessingTask=执行阶段/lease/attempt；具体枚举 PENDING。

## 设计规则

1. 不创建数据库表、初始化 SQL 或迁移脚本，除非收到对应实施任务。
2. 数据库结构变更同步更新逻辑/基准结构与增量迁移，并记录兼容性。
3. 不把图和字段草案直接当成已批准 schema。
4. 按根目录 `AGENTS.md` 处理数据库操作；未经明确授权不执行初始化、清理或删除。

Accepted 决策见 [`../decisions/README.md`](../decisions/README.md)；实现架构见 [`../architecture/README.md`](../architecture/README.md)。

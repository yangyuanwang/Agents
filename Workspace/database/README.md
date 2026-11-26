# 数据库设计文档导航

## 当前状态

- 当前阶段：**本机开发 MySQL 初始化 schema 已创建并完成元数据验证**。
- 初始化脚本：[`schema-mysql.sql`](schema-mysql.sql)；部署验证：[`schema-deployment-report.md`](schema-deployment-report.md)。
- 下一阶段：Spring Boot 持久化基础设施（连接配置、MyBatis/MyBatis-Plus 选择与配置、映射/Mapper、事务、Flyway 增量迁移、基础 CRUD 和租户隔离测试）。
- MySQL 业务表已在本机开发环境初始化；Java 业务实体、Repository 和 Flyway 版本管理尚未实现。
## 权威文档（只保留三个主文档）

1.  [`logical-model.md`](logical-model.md)：17 个逻辑实体、ER
    关系、业务边界及逻辑模型决策。
2.  [`physical-design.md`](physical-design.md)：17
    个实体的字段、类型建议、主键/唯一键/复合外键/索引、状态、并发与生命周期。**当前唯一物理设计正文。**
3.  [`design-decisions.md`](design-decisions.md)：已确认决策和仍然
    PENDING 的事项索引；不重复维护字段表。

## 历史评审资料

以下文件仅用于追溯历史讨论，不再作为当前权威设计来源；若与三个主文档冲突，以主文档为准：

-   `logical-model-review.md`
-   `physical-design-candidate.md`
-   `physical-design-review.md`
-   `field-reviews/chunk-vector-review.md`
-   `field-reviews/file-object-review.md`

历史文件可以归档到 `archive/`，但不要直接删除；归档前确认相对链接及
AGENTS.md 规范。Codex 日常设计任务不得把历史评审稿当作独立待确认事项。

## 数据边界基线

-   MySQL 保存身份/租户/知识库授权、文档版本与任务、Chunk
    完整文本、Chat/引用、审计和配额等关系型业务事实。
-   Qdrant 保存向量及检索 Payload；向量本体不写 MySQL。
-   首版本地文件系统保存原始文件，通过 Storage abstraction 访问；API
    不暴露本地物理路径。
-   Qdrant 检索由服务端强制 tenantId + knowledgeBaseId Payload Filter。
-   Document 逻辑删除提交后立即停止检索返回其 Chunk；不能等待 Qdrant
    异步删除。
-   所有敏感读取、下载、检索和管理操作均基于可信身份重新授权。

## 决策基线

-   ADR-001 至 ADR-013 保持 Accepted 状态，见
    [`../decisions/README.md`](../decisions/README.md)。
-   已确认决策与真正未决事项见
    [`design-decisions.md`](design-decisions.md)。
-   逻辑模型见 [`logical-model.md`](logical-model.md)。
-   物理字段/关系/索引唯一正文见
    [`physical-design.md`](physical-design.md)。

## 工作规则

1.  设计阶段只修改设计文档，不修改业务代码，不生成或执行
    DDL/SQL，除非收到明确授权。
2.  不重新询问 `design-decisions.md` 中已经确认的事项。
3.  不得把历史评审稿里的旧 PENDING 覆盖当前已确认决策。
4.  每次修改物理结构，同步更新
    `physical-design.md`；若改变已确认决策，必须先走 ADR/决策变更流程。
5.  DDL 草案只在物理设计验收通过后进行；DDL 草案不等于数据库实施授权。
6.  所有 FK 删除行为、索引与数据库版本能力必须在 DDL
    阶段验证，不得在当前文档阶段伪造已验证结果。


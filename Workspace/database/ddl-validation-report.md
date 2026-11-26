# DDL 草案第二轮差异核对报告

状态：静态文本审查；没有执行 SQL、连接数据库或测试 MySQL 实例。依据 `physical-design.md` 第 4 节、当前 DDL 与权威决策文档。

## 1. 17 表逐表字段差异核对

下表给出逐表核对状态；“需逐项冻结”表示字段类型/NULL/default 仍有物理设计 PENDING，不能据此视为 schema 已批准。

| 表 | 覆盖核对 | 已识别差异/处理 |
|---|---|---|
| User | 字段名基本对应 | username/email 可 NULL 与物理稿一致；至少一个非空仍 PENDING。唯一索引允许多个 NULL；大小写/规范化 PENDING。 |
| Tenant | 字段名基本对应 | 字段类型/软删状态按候选，需确认租户名称规则。 |
| TenantMember | 字段名基本对应 | 草案同时有两个字段顺序不同的唯一键，分别满足业务唯一与复合 FK；可审查其冗余。 |
| KnowledgeBase | 字段名基本对应 | active_profile 单列 FK 不能强制 SYSTEM/TENANT OR 归属，需服务层校验。 |
| KnowledgeBaseMember | 字段名及两个复合 FK 对应 | 已包含 tenant_id、两个复合 FK、唯一键。 |
| FileObject | 字段名基本对应 | 未引入 ref_count；时间字段可选状态仍按 PENDING。 SHA 编码与 storage_key 唯一范围待定。 |
| Document | 字段名基本对应 | active_version_id nullable；后置复合 FK 已列。 |
| DocumentVersion | 字段名基本对应 | 唯一 `(document_id,id)` 已列。个别字段 nullable/长度待最终逐项确认。 |
| ProcessingTask | 字段名基本对应 | 多态 resource 保持无 FK，服务层校验；字段默认值和 retry 参数仍 PENDING。 |
| EmbeddingProfile | 字段名对应 | scope/owner/secret_ref、全局 collection 唯一；OR 归属需服务校验。 |
| Chunk | 字段名基本对应 | `(document_version_id,id)` 唯一用于引用链。 |
| ChunkVector | 字段名基本对应 | 联合 PK、无 point_id、已确认索引和 updated_at 保留。 |
| ChatSession | 字段名基本对应 | FK 仅保证实体存在/KB 属于 tenant，不保证动态 membership/KB 授权。 |
| ChatMessage | 字段名基本对应 | STREAMING/断线恢复仍 PENDING。 |
| Citation | 字段命名已修复 | `title_snapshot`→`document_title_snapshot`；`snippet_snapshot`→`excerpt_snapshot`。额外 `score DOUBLE NOT NULL` 不存在于物理设计第 4.15 节且无已确认依据，已从 DDL 删除。不增加 tenant_id；是否冗余仍 PENDING。 |
| AuditLog | 字段名基本对应 | 审计保留与脱敏策略仍 PENDING。 |
| TenantQuota | 字段名基本对应 | quota 项目、周期和用量语义仍 PENDING。 |

静态审查发现草案有按主字段列表撰写，未能证明每个表的所有 NULL/default/长度均逐项与物理稿一致；特别需要冻结版本前对 17 表逐列做机械 diff。报告不把这一限制隐藏为通过。

## 2. 31 个 FK 删除动作审核

核对 `ddl-draft.sql` 实际共有 **31 条** FK；所有动作均为 `ON DELETE RESTRICT ON UPDATE RESTRICT`。以下逐条列出。RESTRICT 仅为草案候选，依据是保留逻辑删除、历史版本、异步清理、Citation 快照和审计，不批准级联行为。

| # | FK | 删除动作候选 | 依据 |
|---:|---|---|---|
|1|fk_tm_tenant|RESTRICT|保留成员关系/审计，显式撤销|
|2|fk_tm_user|RESTRICT|身份删除需生命周期流程|
|3|fk_profile_owner|RESTRICT|租户删除需先处理私有 Profile/Collection|
|4|fk_kb_tenant|RESTRICT|KB 逻辑生命周期由业务流程处理|
|5|fk_kb_creator|RESTRICT|保留创建者审计关联|
|6|fk_kb_profile|RESTRICT|Profile/Collection 迁移和回滚保留|
|7|fk_kbm_kb_tenant|RESTRICT|授权显式撤销，不级联清除历史|
|8|fk_kbm_tm|RESTRICT|成员状态改变不等于删除授权历史|
|9|fk_kbm_creator|RESTRICT|保留授权操作者关联|
|10|fk_file_tenant|RESTRICT|文件需先执行引用判定及异步清理|
|11|fk_doc_kb_tenant|RESTRICT|文档逻辑删除及向量清理需编排|
|12|fk_doc_creator|RESTRICT|保留创建者审计关联|
|13|fk_dv_doc_tenant|RESTRICT|版本历史保留及活动版本约束|
|14|fk_dv_file_tenant|RESTRICT|共享文件引用须先判零再清理|
|15|fk_dv_creator|RESTRICT|保留版本发起者关联|
|16|fk_doc_active_version|RESTRICT|活动版本切换/退役显式执行|
|17|fk_task_tenant|RESTRICT|任务需完成/取消/归档|
|18|fk_task_creator|RESTRICT|保留任务发起者；系统任务可 NULL|
|19|fk_chunk_version|RESTRICT|版本清理需先处理向量和引用|
|20|fk_cv_chunk|RESTRICT|向量清理按状态/幂等任务完成|
|21|fk_cv_profile|RESTRICT|Collection 回滚/迁移保留|
|22|fk_session_tenant|RESTRICT|会话按独立删除保留策略处理|
|23|fk_session_user|RESTRICT|用户删除不得级联抹除会话证据|
|24|fk_session_kb_tenant|RESTRICT|KB 删除需显式处理会话|
|25|fk_message_session|RESTRICT|会话消息按保留策略显式清理|
|26|fk_citation_message|RESTRICT|Citation 快照保留边界需先执行策略|
|27|fk_citation_document_version|RESTRICT|引用历史保留且来源链完整|
|28|fk_citation_version_chunk|RESTRICT|引用历史保留且来源链完整|
|29|fk_audit_tenant|RESTRICT|审计独立保留策略|
|30|fk_audit_actor|RESTRICT|保留审计操作者身份关联|
|31|fk_quota_tenant|RESTRICT|租户删除需显式处理配额|

约束名正则静态计数与逐项清单均为 31 条。

## 3. 约束/索引与其他核验

- 复合 FK 的父唯一键和列顺序按当前草案匹配；类型为 BINARY(16)。真实 MySQL 可执行性未验证。
- Document 活动版本循环 FK 通过后置 ALTER 表达。
- ChatSession 只约束 tenant 与 KB 归属，TenantMember ACTIVE 和 KBMember 授权必须实时服务层校验。
- User 至少一个登录标识非空尚未冻结；候选 MySQL CHECK 或服务层校验，账号大小写/规范化规则继续 PENDING。
- Profile scope-owner CHECK 与 Collection 唯一已表达；KB 单列 profile FK 不强制 OR 归属，服务层须校验。
- FileObject 并发/引用协议不可能仅由 DDL 完整表达，必须实现同一行锁仲裁。

## 4. 修复及未解决事项

已修复 Citation 列名与物理设计不一致，并删除未经物理设计/已确认决策支持的 `score` 字段。该字段可能表达检索相似度，但其来源、精度、历史快照必要性均未获确认，因此不应仅因旧 DDL 存在而保留。未增加 Citation.tenant_id；是否冗余仍 PENDING。User 至少一项登录标识的 CHECK/服务校验方案未擅自选定。

**仍未通过最终 DDL 冻结：** 17 表逐列差异需完成自动 diff；FK 数量统一为 31；物理 MySQL 版本、CHECK 与 FK 兼容性、全部字段长度/default、Profile OR 表达及 FK 删除动作仍需明确。未连接或运行 MySQL。

## 结论

**有条件通过进入最终 DDL 冻结前审查；当前草案不可冻结或执行。** 本轮已从 DDL 删除无物理设计依据的 `Citation.score`，并将 Citation 快照字段修正为 `document_title_snapshot` 与 `excerpt_snapshot`；FK 数量统一为 31。逐字段完整性和 MySQL 兼容性仍未验证。

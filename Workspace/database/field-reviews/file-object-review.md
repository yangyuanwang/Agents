# 第四阶段第二组：FileObject 字段级评审稿

状态：**设计评审稿；不是最终字段基线、DDL 或实施授权。** 本稿只评审 FileObject 的字段与生命周期边界。所有尚未由既有决策确定的方案均标为“本轮推荐，待用户确认”或“PENDING”。

## 1. 已确认约束

- **已确认：** 第一版使用本地文件系统，由存储接口抽象，未来可替换 MinIO/S3；API 不暴露本地物理路径。
- **已确认：** 支持 PDF、DOCX、TXT、Markdown；租户内 SHA-256 相同的内容复用同一 FileObject 和物理文件，不跨租户复用。
- **已确认：** 多个 Document 可引用同一 FileObject；Document 管业务生命周期，FileObject 管物理文件生命周期；删除一个 Document 不得误删仍被引用的文件。
- **已确认：** 文件逻辑删除后异步清理物理文件，清理应可重试、幂等；租户身份来自服务端可信主体及授权，不信任请求传来的 tenantId。
- **PENDING：** ref_count、并发上传/删除协议、Hash 命中后软删除对象的复用规则、物理清理重试/对账细节。

## 2. 当前逻辑模型字段

当前逻辑模型候选字段为 `id`、`tenant_id`、`storage_key`、`sha256`、`byte_size`、`media_type`、`original_name`、`storage_status`、`created_by`、`created_at`、`delete_requested_at`、`deleted_at`。本轮输入中的 `original_filename`、`content_type`、`file_extension`、`file_size`、`status`、`updated_at` 是对应概念的命名/拆分评审，不代表全部需要新增。

## 3. 主键与唯一约束

### 主键

**本轮推荐，待用户确认：** `id` 用全局 UUID，MySQL 建议 `BINARY(16)`，作为 FileObject 逻辑身份；如需跨租户组合外键，可另有候选 UNIQUE `(tenant_id,id)`。全库 UUID 字节序需遵循统一约定。主键并不替代内容去重约束。

### 内容唯一键

**已确认语义：** `(tenant_id, sha256)` 标识租户内相同内容的可复用对象。**本轮推荐，待用户确认：** 对活动/可复用对象施加数据库级唯一性，避免应用层“先查后插”竞态。是否可直接对全行建立 UNIQUE，取决于删除后重新上传究竟复用原记录、等待清理完成还是创建新对象；软删除行是否占据唯一键仍 **PENDING**。不要在未定此语义前定稿唯一索引表达方式。

SHA-256 **本轮推荐，待用户确认：** 存 32 字节二进制值（`BINARY(32)`）；字符串形式 `CHAR(64)` 更便于人工查看，但占用更大且须规范小写/字符集。比较应按完整 SHA-256，而非截断摘要。

### 并发上传与冲突复用

数据库唯一约束是并发仲裁点。可先将内容写到临时/不可见对象并计算摘要，再尝试登记/发布唯一对象；唯一冲突时读取并锁定胜出的 FileObject，核验租户、摘要、大小、可用状态及存储对象存在性后复用。未确认状态或对象不完整时，不可盲目复用；转入修复/重试路径。确切事务边界和锁策略 **PENDING**。

## 4. 字段逐项分析

| 字段 | 评审结论 | 理由/待定点 |
|---|---|---|
| `id` | 本轮推荐，待确认 | UUID 逻辑身份，建议 `BINARY(16)`；组合外键需要时可提供 `(tenant_id,id)` 唯一键。 |
| `tenant_id` | 已确认需要 | 去重与隔离作用域；服务端推导。候选 FK Tenant，建议组合唯一键供 DocumentVersion 同租户约束；具体 FK 待确认。 |
| `sha256` | 已确认业务用途；存储形式待确认 | 租户内复用键；推荐 32 字节二进制。 |
| `original_filename` / `original_name` | 本轮推荐不放在 FileObject，待确认 | 同一内容可能由不同 Document/版本以不同文件名上传；文件名是上传/业务呈现元数据，应由 Document 或 DocumentVersion 持有。若有原始上传审计需求，再单独定义归属。 |
| `storage_key` | 本轮推荐保留，待确认 | 不可猜测的存储抽象 key，由 adapter 解析；不能是 API 暴露的绝对路径。可唯一约束候选（全局或 provider 范围，取决于抽象命名空间）。 |
| `storage_provider` | PENDING；本轮倾向首版由配置/adapter 决定 | 首版只有本地 provider 时，逐行重复存储冗余；若同环境迁移/多 provider 并存需要逐对象路由，则字段有明确价值。未来扩展不能自动视为首版需求。 |
| `content_type` / `media_type` | 本轮推荐保留一个规范化检测 MIME 字段，待确认 | 两个名称重复；不要同时保存。上传声明值不可信，存服务端检测结果；检测规则及是否允许空值 PENDING。 |
| `file_extension` | 本轮推荐不单独保存，待确认 | 可由安全规范化后的原始名/检测类型得到，但两者不总一致；若处理器路由确需稳定扩展名，应明确采用推导值还是独立保存。扩展名不能替代内容检测。 |
| `file_size` / `byte_size` | 本轮推荐保留单一 `byte_size`，待确认 | 明确用于配额、下载长度及完整性检查，不与其他字段重复。非负约束候选。 |
| `ref_count` | PENDING；见第 5 节 | 引用关系可以从 DocumentVersion 计算；物化可降低大租户读取成本，但带来并发和修复复杂度。 |
| `status` / `storage_status` | 已确认需要分离物理生命周期状态；具体字段名/值 PENDING | 避免与 Document/Version/Task 状态混淆。只保留一个状态列。 |
| `created_at` | 本轮推荐保留，待确认 | 对象登记时间及审计/清理排序依据。 |
| `updated_at` | 本轮推荐保留，待确认 | 状态/租约相关状态最近更新时间，可支撑陈旧项扫描；需定义由谁更新。 |
| `deleted_at` | PENDING；本轮倾向表达物理删除完成时刻 | 逻辑清理请求与物理删除完成不是同一时刻。若保留，需与状态语义一致；`delete_requested_at` 是否另存待定。 |
| `delete_requested_at` | PENDING | 可用于清理年龄/排队诊断；也可由状态更新时间或任务记录承载，需结合调度查询确定。 |
| `created_by` | PENDING | 当前候选字段，但归属可能属于 Document/上传审计；共享 FileObject 的“创建者”不代表后续引用者。需确认审计要求。 |

存储 key 与路径边界：业务层只持有 opaque key 并调用 Storage abstraction；仅 adapter 可将 key 映射到本地根目录下的路径或对象存储地址。路径拼接、路径穿越防护和根目录配置属于实现约束，不应让 API/业务代码依赖磁盘布局。

## 5. ref_count 方案比较

| 方案 | 优点 | 风险与成本 |
|---|---|---|
| A. 不保存，按引用关系计算 | 单一事实来源；不会产生计数漂移；新增/删除引用不需维护第二份状态 | 大租户判断是否仍有引用需对 DocumentVersion 引用表查询；适当索引可做存在性查询，但高频清理/热点可能增加负载。 |
| B. 保存并事务维护 | 判断剩余引用成本低；可快速筛选候选清理对象 | 建立/撤销引用与计数必须同事务、同锁协议；重复请求、软删除定义、失败补偿和并发热点易造成漂移；0 不能证明没有有效引用，仍需防竞态。 |
| C. 保存并定期对账修复 | 读路径便宜，能检测并纠正部分漂移 | 仍承担 B 的写复杂度；对账有延迟，修复期间不能让计数直接授权删除；要定义告警、修复冲突和扫描成本。 |

**本轮推荐，待用户确认：A，暂不物化 `ref_count`。** 先以 DocumentVersion 对 FileObject 的有效引用关系作为真值，通过索引存在性查询判断是否仍被使用。理由是当前模型已有引用关系，且错误计数会造成不可逆的文件误删；当前没有规模数据证明读查询已成瓶颈。大租户成本可通过测量后再评审 C。若未来确认物化，推荐 C 而非仅 B，并要求事务内维护、删除前仍验证真实引用、周期对账；这些均非本轮批准。

计算口径 **PENDING**：软删除 Document 的版本是否立即不算有效引用；文档物理清理与版本保留的关系需保持一致。物理清理时建议以“没有任何仍受保留策略保护的引用”作为判定，而不只检查 Document 是否处于某个业务状态。

## 6. 文件状态机候选

下列名称和转换均为**候选，PENDING，未批准**：

| 候选状态 | 含义 | 候选转换/恢复 |
|---|---|---|
| `STAGING` | 临时写入或对象尚未发布 | 成功写入并验 hash 后登记/发布至 `AVAILABLE`；过期临时对象可清扫。 |
| `AVAILABLE` | 存储对象完整且可被授权读取/复用 | 最后有效引用移除且保留策略允许时转 `DELETE_PENDING`。 |
| `DELETE_PENDING` | 已决定清理、等待 Worker | 新引用竞争必须与清理原子仲裁；获准复用则回 `AVAILABLE`，否则领取清理。 |
| `DELETING` | Worker 持租约执行物理删除 | 删除成功转 `DELETED`；失败/租约过期回待重试状态。是否单列状态或用任务租约表达 PENDING。 |
| `DELETE_FAILED` | 最近清理失败 | 退避后重试；状态名与失败信息归属 PENDING。 |
| `DELETED` | 物理对象确认不存在 | 可保留 tombstone 供幂等/审计；同 hash 新上传是否复用此行 PENDING。 |

异常候选处理：

- 文件写入成功、DB 登记失败：临时/最终对象成为 orphan；需可扫描、按租约/宽限期清理，不能立即误删另一个并发上传发布的对象。
- DB 记录成功、文件写入失败：记录不可标为可用；由任务重试补写或将对象置失败/清理候选。创建顺序和补偿策略 PENDING。
- 一个 Document 删除：只撤销其有效版本引用；若还有其他有效引用，不进入物理清理。
- 清理时新引用到达：通过锁定 FileObject 行或等效原子 claim 争夺状态；只有引用建立与状态检查协调后才能复用。被标记清理中的对象不直接复用，应等待取消清理/重新发布协议。
- 文件已删除但 DB 状态写回失败：重试删除，存储层对“不存在”视作成功，再将状态推进为完成；需要稳定 key 和幂等 adapter。

清理任务/租约：**PENDING。** 可由既有异步任务机制承载，但本稿不定义 ProcessingTask 字段。若只扫描 FileObject，需要至少有可辨别的待清理状态、更新时间/领取保护和重试错误归属；是否独立清理任务记录、租约列或复用任务实体，应在任务模型评审中决定。

## 7. 索引设计建议

| 候选索引 | 用途 | 状态 |
|---|---|---|
| `(tenant_id, sha256)` UNIQUE | 并发去重与命中复用 | 业务键已确认；软删除/清理对象占键语义 PENDING，最终唯一表达待确认。 |
| `(status, updated_at)` 或 `(storage_status, updated_at)` | 扫描待清理、失败重试、陈旧 STAGING | 本轮推荐，待确认；最终列名/状态转换定后按实际扫描条件验证。 |
| `(tenant_id, id)` UNIQUE | 组合 FK/按租户对象定位 | 若组合外键方案采用则需要；单独 tenant 查询则其左前缀可用，不另建 `(tenant_id)`。 |
| `storage_key` UNIQUE | 保证单一物理 key 不被多个 FileObject 错误拥有 | 本轮推荐评估，待确认；若 key 仅在 provider 内唯一，则需纳入 provider。 |

不建议另建单列 `tenant_id` 索引（组合唯一索引已有左前缀），也不为 `deleted_at`、`created_at` 单独建索引，除非实际查询证明需要。索引数受 FK 自动索引行为及 MySQL 版本影响，DDL 阶段再核对，当前不生成 SQL。

## 8. 复用与并发清理边界

候选安全流程：上传先在受控临时区落盘并计算摘要；数据库唯一键仲裁同租户摘要；唯一冲突后锁定读取现有行并确认 `AVAILABLE` 且对象可读，安全复用。创建 DocumentVersion 引用时与清理状态仲裁使用同一数据库事务/行锁顺序。删除 Document 时只撤销引用并提交业务逻辑删除；清理 Worker 在事务中确认有效引用为零并 claim 清理，再在事务外调用 adapter 删除。物理删除失败则记录可重试结果；成功或对象已不存在，再幂等写回完成状态。

该流程避免把文件系统操作放进长事务，但 MySQL 与文件系统不存在共同事务，仍需 orphan 扫描、清理重试和周期对账。锁定顺序、宽限期、租约续期、上传去重与删除交错时的仲裁规则均 **PENDING**。

租户安全：客户端不决定 tenant_id；对象读取和下载先校验当前主体/租户/Document 权限，再以受控 key 调用 adapter。若 DocumentVersion 与 FileObject 都有 tenant_id，候选组合 FK `(tenant_id,file_object_id)` 可阻止跨租户关联；具体组合键与 ORM/数据库支持 PENDING。

关系边界：一个 FileObject 可被零到多个 DocumentVersion 引用；多个 Document 因版本引用而共享。**不新增关联实体**；现有 DocumentVersion 引用关系作为关联。Document 删除后的有效引用口径与保留策略 PENDING。

## 9. 推荐字段表

以下均为设计建议，不是批准 schema。

| 字段建议 | 类型建议 | NULL | 分类 | 备注 |
|---|---|---:|---|---|
| `id` | `BINARY(16)` UUID | 否 | 本轮推荐，待确认 | 主键。 |
| `tenant_id` | `BINARY(16)` | 否 | 已确认概念；物理约束 PENDING | 租户作用域；可信服务端赋值。 |
| `sha256` | `BINARY(32)` | 否 | 已确认概念；编码待确认 | 租户内复用摘要。 |
| `storage_key` | `VARCHAR`（长度待定） | 否 | 本轮推荐，待确认 | opaque key，不是公开路径。 |
| `byte_size` | `BIGINT` unsigned 候选 | 否 | 本轮推荐，待确认 | 文件实际字节数。 |
| `media_type` | `VARCHAR`（长度待定） | 否候选 | 本轮推荐，待确认 | 服务端检测的单一 MIME 值。 |
| `storage_status` | `VARCHAR`（长度待定） | 否 | 状态职责已确认；枚举 PENDING | 物理文件生命周期。 |
| `created_at` | UTC `DATETIME(3)` | 否 | 本轮推荐，待确认 | 首次登记。 |
| `updated_at` | UTC `DATETIME(3)` | 否 | 本轮推荐，待确认 | 状态更新时间/扫描辅助。 |
| `delete_requested_at` | UTC `DATETIME(3)` | 是 | PENDING | 是否由状态时间/任务替代待定。 |
| `deleted_at` | UTC `DATETIME(3)` | 是 | PENDING | 建议语义为物理删除完成时间。 |

本轮建议暂不纳入：`original_filename`（归 Document/Version）、独立 `file_extension`（非必要重复）、`storage_provider`（首版配置可确定）、`ref_count`（推荐 A）、`created_by`（审计归属待确认）。这些是本轮建议，不构成确认删除字段。

## 10. 尚需用户确认的事项

1. 主键是否采用 UUID `BINARY(16)`；是否需要 `(tenant_id,id)` 组合唯一键及组合 FK。
2. `(tenant_id,sha256)` 如何表达活动对象唯一性；清理/软删除中对象是否占据 hash 键，重新上传是复用还是等待/新建。
3. SHA-256 二进制或规范化十六进制字符串。
4. 是否接受不物化 `ref_count`（推荐 A）；“有效引用”是否包括逻辑删除但仍受保留策略保护的版本。
5. 存储 key 唯一范围；首版是否需要 `storage_provider`。
6. `original_filename` 归属、MIME 检测值、扩展名是否确有稳定业务用途。
7. 状态枚举、状态转换、清理租约/任务记录、失败信息与退避的归属。
8. `delete_requested_at`、`deleted_at`、`updated_at`、`created_by` 的保留及精确定义。
9. 清理宽限期、orphan 对账、最大文件大小、配额与文件校验策略（ADR 标为 PENDING 的项目）。

## 11. 对后续 DDL、Java Entity、Mapper 的影响

- UUID 主键影响 JDBC/Mapper 的 UUID 与 `BINARY(16)` 编码；需复用全库字节序规范。
- 内容唯一约束应成为并发上传仲裁点；应用不能依赖先查后插。冲突处理需事务读取、校验租户/摘要/状态与对象完整性。
- 若采用不物化引用数，清理查询必须按 DocumentVersion 有效引用关系做索引友好的存在性检查；组合 FK 可阻止跨租户错误引用。
- 若将来物化 ref_count，Entity/Mapper 的引用增删必须与计数维护同事务并支持幂等；对账不得省略。
- Storage adapter 接收 opaque key；业务 Entity/DTO 不暴露磁盘绝对路径。状态推进与物理 I/O 分事务，清理需可重试并容忍对象已不存在。
- 索引最终需依据清理扫描 SQL、租户查询和 MySQL 版本验证；本稿未产生 DDL/SQL。

本稿只完成 FileObject 评审，不改变 ADR 或逻辑模型基线，不代表任何字段/枚举已批准。

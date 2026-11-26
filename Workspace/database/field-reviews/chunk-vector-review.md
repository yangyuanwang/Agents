# ChunkVector 字段级评审稿

状态：**三项 ChunkVector 字段设计已由用户确认；其余字段与物理实现仍待评审。本文件不是最终 DDL 或物理表结构。** 本文只评审 ChunkVector，不扩展其他实体字段，也不修改 `logical-model.md` 中已确认的逻辑模型约束。

### 本轮已确认的字段设计

1. 自然联合主键：`(chunk_id, embedding_profile_id)`。
2. 不单独保存 `point_id`；Qdrant Point ID 由 `chunk_id` 按统一 UUID 编码规范转换得到。
3. 建立状态扫描索引：`(embedding_profile_id, index_status, updated_at)`。该索引已纳入字段设计；最终 DDL 阶段仍须用实际 SQL 和执行计划验证其有效性及索引顺序。

## 1. 当前模型与已确认约束

ChunkVector 已确认是独立逻辑实体，采用独立数据库表。以下约束不在本轮重新评审：

- 一个 Chunk 在不同 EmbeddingProfile 下可以有不同索引映射。
- 一个不可变 EmbeddingProfile 对应一个 Qdrant Collection；模型配置变化使用新的 Profile 和 Collection；同 Profile 多 Collection Generation 不支持。
- Chunk ID 与其在相应 Profile Collection 中的 Qdrant Point ID 相同。
- MySQL 保存 Chunk 完整文本；Qdrant 保存向量与检索 Payload。
- Document、DocumentVersion、ProcessingTask、ChunkVector、FileObject 各自管理状态；ProcessingTask 的 `status` 与 `stage` 分离。
- Worker lease 过期可重新领取，并依靠幂等机制恢复；Qdrant 与 MySQL 无共同事务，操作需可重试、可对账。

上述约束来自 ADR-004/006/007/009/012 和用户已确认的六项逻辑模型结果。本轮另确认了本稿开头列出的三项字段设计；除这三项外，其他字段和状态名仍为评审候选或 PENDING。

## 2. 当前逻辑模型中的候选字段

当前 `logical-model.md` 的 ChunkVector 候选字段为：

| 当前候选字段 | 当前含义 | 本轮评审关注点 |
|---|---|---|
| `id` | 映射记录主键，`BINARY(16)` UUID | 旧逻辑模型候选；本轮确认不纳入，采用自然联合主键 |
| `chunk_id` | 来源 Chunk | 与 `embedding_profile_id` 共同确定一条索引映射 |
| `embedding_profile_id` | 向量空间/Profile | 不同 Profile 的映射并存；没有 generation 维度 |
| `point_id` | Qdrant Point ID | 旧候选；本轮确认不单独保存，由 `chunk_id` 转换得到 |
| `index_status` | 该映射的索引状态 | 责任已确认，具体枚举和转移仍 PENDING |
| `indexed_at` | 成功索引时间 | 是否改为 `last_indexed_at`，与任务时间的边界 |
| `last_error_code` | 最近失败错误代码 | 是否需要错误文本，以及执行历史由谁保存 |
| `updated_at` | 映射状态更新时间 | 用于状态推进和扫描陈旧记录 |

字段列表记录逻辑模型中的既有候选，不代表必须保留每一列。`id` 和 `point_id` 已被本轮确认方案取代；`created_at` 是否新增仍 PENDING。

## 3. 主键方案比较（决策已确认，其他方案仅作记录）

| 方案 | 结构 | 优点 | 缺点 |
|---|---|---|---|
| A. UUID 代理主键 | `id BINARY(16) PK`，另加 UNIQUE `(chunk_id,embedding_profile_id)` | Java/Spring Data 中可使用简单 `@Id`；将来其他关系只需引用一个 ID；便于直接定位和外部日志关联 | `id` 不表达业务唯一性，仍必须加复合 UNIQUE；多一个 UUID 字段和索引，写入及存储开销增加；没有当前已确认的引用者需要该代理 ID |
| B. 自然联合主键 | `(chunk_id,embedding_profile_id)` 为复合 PK | 主键即业务身份；无需生成或保存冗余 `id`；PK 同时保证同一 Chunk/Profile 只有一条映射；与幂等 upsert 的键一致 | JPA/Spring Data 需 `@EmbeddedId`/`@IdClass`，关联映射比单 `@Id` 繁琐；其他表引用它时需要复合 FK；MyBatis/SQL 调用需同时传两个键 |
| C. 代理 UUID 并以两个字段作唯一键 | 与 A 类似，但将业务复合 UNIQUE 设为权威幂等键 | 保留单列引用的便利，同时正确约束业务唯一性 | 与 A 的数据结构相同，不能省掉复合索引；只有确有代理引用需求时才值得增加 `id` |

### 已确认决策：采用 B

已确认采用 **B：`(chunk_id, embedding_profile_id)` 作为自然联合主键**。ChunkVector 的身份是“哪个 Chunk 在哪个 Profile 下的索引记录”；当前没有已确认的实体需要单列 ChunkVector ID。联合主键也提供 Qdrant 写入完成后的幂等写入键和按 Chunk 查询的索引前缀。

目前项目资料未确认使用 JPA/Spring Data 还是 MyBatis；仓库现状以 JDBC 配置为主，也没有 ChunkVector Repository。MyBatis/JDBC 可直接使用两个字段查询/写入；如果后续确认使用 JPA，`@EmbeddedId` 是可行但需要统一的复合 ID 类型。方案 A/C 仅作为评审历史比较，不再是待选项；若未来出现新的单列引用需求，应作为新的设计变更单独评审，不能改变本轮确认结果。

## 4. Point ID 存储方案比较（决策已确认）

### 方案比较

| 方案 | 说明 | 影响 |
|---|---|---|
| A. 只存 `chunk_id` | 调 Qdrant 时直接把 Chunk UUID 转为 Point ID；`embedding_profile_id` 决定 Collection | 符合 ADR-006；不重复保存同一值，避免写入/更新不一致；Point ID 反查 Chunk 直接使用 UUID |
| B. 同时存 `chunk_id`、`point_id` | 增加另一份 BINARY(16) 值，并要求二者永远相等 | 当前没有独立业务含义；普通 UNIQUE 不能保证两个不同列值相等，需要 CHECK/触发器/应用校验；增加不一致风险和存储/索引开销 |

### 已确认决策：只存 `chunk_id`

已确认 **不单独存 `point_id`**，Qdrant Point ID 由 `chunk_id` 转换得到。Chunk ID 与 Point ID 相同是既定规则；将同一个 UUID 存两次没有额外映射价值。`embedding_profile_id` 唯一决定 Collection，因此请求 Qdrant 时用 Profile 选 Collection、用 `chunk_id` 作为 Point ID。

不增加 UNIQUE `(embedding_profile_id, point_id)`：`point_id` 不存储；而 `(embedding_profile_id, chunk_id)` 与联合主键表达相同的映射唯一性。若实际查询需要按 Profile + Point ID 反查映射，可评估状态扫描索引之外是否确有反向**非唯一**索引 `(embedding_profile_id, chunk_id)` 的需求；这不改变已确认的唯一键设计。

## 5. 唯一约束和索引设计

### 约束

- **已确认的唯一身份：** `(chunk_id, embedding_profile_id)`，由自然联合主键保证；不另设代理 `id`。
- **外键候选：** `chunk_id → Chunk.id`、`embedding_profile_id → EmbeddingProfile.id`。具体外键、删除行为以及 ORM/MySQL 支持仍待字段评审；本稿不指定级联规则。
- 不使用 `point_id` 唯一约束，因为该列不单独存储，Point ID 由 `chunk_id` 转换得到。

### 查询索引

| 查询场景 | 建议索引 | 必要性 |
|---|---|---|
| 按 Chunk 查看所有 Profile 的索引映射 | 主键 `(chunk_id,embedding_profile_id)` | 主键左前缀可直接查询同一 Chunk 的所有 Profile 状态，无需额外索引 |
| 按 Profile 扫描待写、失败重试或待删除记录 | **已确认纳入**二级索引 `(embedding_profile_id,index_status,updated_at)` | Profile 放首列，可定位某 Profile 的状态队列；`updated_at` 可帮助扫描陈旧记录/分页。最终 DDL 阶段用实际 SQL 和执行计划验证索引有效性与顺序；重试时限和退避由 Task 策略决定 |
| 按 DocumentVersion 清理该版本向量 | 先由 Chunk 的 `document_version_id` 索引枚举 Chunk，再按 `chunk_id` 查 ChunkVector | 复用 Chunk 表现有关系和 ChunkVector 主键前缀；不在 ChunkVector 冗余存 DocumentVersion ID |
| 按 Profile/Point ID 与 Qdrant 对账 | `point_id` 派生为 `chunk_id`；根据批量 ID 和 Profile 查询 | 默认不再加反向索引；若实测查询必须以 Profile 为第一条件，再评估非唯一 `(embedding_profile_id,chunk_id)` |

不建议为 `last_error_code`、`indexed_at`、`updated_at` 单独建索引，也不另建 `(embedding_profile_id,point_id)` 唯一索引。已确认的状态扫描索引仍需在最终 DDL 阶段结合实际 SQL、数据规模和执行计划验证。

## 6. `index_status` 状态机候选

索引状态负责表达 Chunk/Profile 映射当前物化结果；ProcessingTask 负责一次执行的排队、租约、阶段和尝试。以下是语义候选，不是已批准枚举：

| 候选语义/示例名称 | 说明 |
|---|---|
| 尚未写入（如 `PENDING`） | 已登记映射，尚无 Qdrant Point 写入确认；也可作为失败重试任务重新领取后的初始状态 |
| 写入中（如 `INDEXING`） | 当前有任务在尝试 Embedding/Upsert；任务租约过期后可由新 Worker 重新领取 |
| 可检索（如 `INDEXED`） | Qdrant 对 Upsert 返回成功，并且状态成功持久化；不能在 Qdrant 确认前标记 |
| 失败（如 `FAILED`） | 最近执行失败；是否可重试、何时重试和尝试次数由 ProcessingTask/重试策略管理，不建议仅在 ChunkVector 中重复保存 retryable、attempt_count |
| 待删除（如 `DELETE_PENDING`） | 已请求从 Qdrant 删除该 Point，等待清理执行 |
| 删除中（如 `DELETING`） | 清理任务正在执行；失败应回到可重试状态/任务，实际映射方式待定 |
| 已删除（如 `DELETED`） | Qdrant 已确认删除；映射记录是否保留为 tombstone、保留多久仍 PENDING |

可考虑的流程：`PENDING → INDEXING → INDEXED`；写入失败到 `FAILED`，重试时回到处理中；删除流程为待删除→删除中→已删除，失败后安排重试。具体状态名、失败是否另分终态、何时可清理 tombstone、删除与重建竞态如何裁决均需用户确认。本轮不批准任何 enum。

### Qdrant 成功但 MySQL 更新失败

Qdrant 和 MySQL 没有共同事务。如果 Qdrant Upsert 已成功、MySQL 仍显示 `PENDING/INDEXING`，Worker lease 过期后重新处理同一 `(chunk_id,profile_id)`，用同一个 `chunk_id` 作为 Point ID 再次 Upsert；该操作按已确认的幂等要求覆盖同一目标 Point。收到成功确认后再把 ChunkVector 置为可检索状态。若请求超时而结果未知，也走相同重试与对账流程，不假设第一次一定失败。

反向情况也应防止：不能先把 MySQL 标为 `INDEXED` 再调用 Qdrant。若外部结果、状态写回或进程崩溃造成不确定性，依赖任务重放和周期对账修复；不能声称两库强一致。Document 逻辑删除仍需遵循 ADR-012 的即时检索门禁，不由本状态表单独替代。

## 7. 时间与错误字段

| 字段 | 建议 | 理由/边界 |
|---|---|---|
| `created_at` | 建议保留 | 映射记录首次登记时间；即使首次写入失败，仍能看到应建立的索引映射 |
| `updated_at` | 建议保留 | 最近一次映射状态变更时间；支持 Profile 状态队列扫描和陈旧项对账 |
| `indexed_at` | 建议保留并评估改名为 `last_indexed_at` | 表示最近一次 Qdrant Upsert 成功确认时间；重新索引时更新。任务 `completed_at` 是批次执行结束时间，不必然等同每个 Point 的成功时间 |
| `last_error_code` | 建议保留 | 以稳定代码快速识别最近索引失败原因；不必为其单独建索引 |
| `last_error_message` | 不建议首版持久化在 ChunkVector | 详细、脱敏后的执行错误更适合由 ProcessingTask 保存；重复写入消息会造成重复事实和潜在敏感信息。若任务模型不能定位失败对象，再评估最小摘要字段 |
| `deleted_at` | 本轮建议不加，待确认 | 可用 `index_status=DELETED` 与 `updated_at` 表示最近完成时间；若业务保留/审计要求必须区分删除完成时刻与其他状态更新时间，再增加专用时间字段 |
| `last_task_id` | 本轮建议不加，见第 8 节 | 一个 Task 可批量处理多个 ChunkVector，单个“最后任务”只保存指针，不保留完整执行关联/历史 |

重试次数、租约、开始/结束、错误详情属于 ProcessingTask 的单次执行事实；不在 ChunkVector 重复存储。保留 `last_error_code` 的前提是它只表示最近错误摘要，不代替任务历史。

## 8. 与 ProcessingTask 的关系

一个 Embedding/Upsert Task 可能批量影响多个 ChunkVector，因此实体关系在业务上是“一次任务影响多条映射”，但本轮不增加任务关联实体/表。

- **`last_task_id`：不建议在 ChunkVector 保存。** 它只指出最后一次任务，无法表示之前的失败/重试历史；批量任务会在很多行上重复同一 Task ID；多个 Worker/重试交错时还会产生“谁是最后一次”的更新竞态。
- **直接外键关联：** 如果每个 task 仅处理一个 ChunkVector，单外键可行，但与已规划批量 Embedding/Upsert 不匹配；若在 Task 中存单个 `chunk_vector_id` 也无法表达批量。
- **关联表：** 可精确记录 task 与多条 ChunkVector 的尝试关系，但本轮明确不新增实体/关联表，不纳入推荐字段。
- **仅依靠 Task 日志与 ChunkVector 状态：** 足以做当前最小恢复：Task 提供 lease/attempt/stage/错误执行信息；ChunkVector 提供每个 Chunk/Profile 的当前索引事实；按稳定 ID 幂等 Upsert/Delete，并用重放和对账修复状态差异。它不能提供精确、永久的“某次 Task 具体触及哪些映射”的审计关系。如果这项审计以后成为要求，应单独评审关系建模。

因此，本轮建议 ChunkVector 不存 Task 外键或 `last_task_id`；重试由 ProcessingTask 驱动，向量映射保留当前状态和最近成功/失败摘要。如何从现有任务目标范围定位某批 Profile/Chunk 映射，依赖 ProcessingTask 的后续字段/任务范围设计，仍为 PENDING；本轮不增改该实体。

## 9. 字段设计状态表

| 字段 | 类型建议 | 可空 | 键/索引建议 | 状态分类 | 说明 |
|---|---|---:|---|---|---|
| `chunk_id` | `BINARY(16)` | 否 | 联合 PK；FK → Chunk 候选 | 联合主键及 Point ID 来源已确认；外键实现 PENDING | Chunk 身份；转换后作为 Qdrant Point ID，不重复保存 |
| `embedding_profile_id` | `BINARY(16)` | 否 | 联合 PK；FK → EmbeddingProfile 候选 | 联合主键已确认；外键实现 PENDING | 区分不同向量空间/Collection |
| `index_status` | `VARCHAR(24)` | 否 | 已确认纳入 `(embedding_profile_id,index_status,updated_at)` | 状态职责已确认；具体枚举 PENDING | 指定 Chunk/Profile 的索引状态 |
| `last_indexed_at`（现候选 `indexed_at`） | `DATETIME(3)` | 是 | 默认不单独建索引 | 本轮推荐 | 最近一次 Qdrant Upsert 成功确认时间 |
| `last_error_code` | `VARCHAR(64)` | 是 | 默认不单独建索引 | 当前候选保留；语义待确认 | 最近失败代码；详细错误留在任务执行记录 |
| `created_at` | `DATETIME(3)` | 否 | 默认不单独建索引 | 本轮推荐新增 | 映射首次登记时间 |
| `updated_at` | `DATETIME(3)` | 否 | 已确认索引 `(embedding_profile_id,index_status,updated_at)` 的组成列 | 作为该索引组成列已确认；更新时间语义遵循字段基线 | 状态最近更新时间 |

**本轮已确认不纳入：** 代理 `id`、独立 `point_id`。**仍待评审的候选：** `last_error_message`、`last_task_id`、`deleted_at`；本稿建议首版不加入，但这不是用户确认的字段删除决定。`created_at` 是否新增也仍待确认。

## 10. 尚需用户确认的事项

1. `index_status` 具体枚举及状态流转：是否区分可重试/终态失败、删除 tombstone 的保留方式。
2. `indexed_at` / `last_indexed_at` 的字段命名。
3. 是否保留 `last_error_code`；是否新增 `last_error_message`。
4. 是否新增 `created_at`。
5. 是否新增 `deleted_at`、`last_task_id`。
6. ProcessingTask 与 ChunkVector 的精确任务范围关联；若要求按单次任务审计具体映射，需要另行评审关系设计。
7. 外键物理实现及删除行为、MySQL/Mapper 能力。
8. 最终 DDL 及已确认索引的实际 SQL/执行计划验证。

以上问题不会重新审议独立 ChunkVector 表、Profile/Collection 一对一、无同 Profile Generation、Chunk/Point ID 相同或跨库最终一致性等已经确认的约束。

## 11. 对后续 DDL、Java Entity、Mapper 的影响

- 已确认自然联合主键 `(chunk_id,embedding_profile_id)`；Java 需复合 ID 类型（若采用 JPA，可用 EmbeddedId/IdClass）或 MyBatis 多参数键；upsert/delete 命令携带两个字段。
- 已确认不存 `point_id`；Qdrant adapter 由 Profile 解析 Collection，并按统一 UUID `BINARY(16)` 编码规范从 `chunk_id` 生成 Qdrant UUID Point ID。
- 已确认纳入 `(embedding_profile_id,index_status,updated_at)` 状态扫描索引；最终 DDL 阶段必须以预计查询和执行计划验证索引顺序及效果。
- Worker 对 Qdrant 操作必须使用稳定 `(chunk_id,profile_id)` 目标和幂等 upsert/delete；Task 成功不等于每个 Vector 一定成功，映射状态需逐项推进并可重试/对账。
- 本稿归档三项已确认字段决策；其余字段和实现细节继续待评审。本轮没有修改逻辑模型、任务实体、代码、ADR 或数据库结构。

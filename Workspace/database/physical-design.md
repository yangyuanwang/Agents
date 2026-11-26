# 数据库物理设计基准

状态：**整合版物理设计基准（字段与关系候选已统一；实现细节仍有
PENDING）。本文件是当前唯一物理设计正文，不等于已冻结
DDL，也不授权建表或迁移。** 本设计覆盖逻辑模型既有 17
个实体，不增加正式实体。实现现状仍以源码为准：当前 MySQL 仅有 JDBC
配置，没有业务实体、Repository、DDL 或迁移。

## 1. 范围与状态标记

-   **已确认**：来自 Accepted ADR 或用户已确认的逻辑/字段决策。
-   **推导建议**：为使关系、并发或查询可落地而提出，待用户确认。
-   **PENDING**：需要业务、规模、MySQL/Mapper 选择或实施查询后才能决定。

本稿将字段和约束作为单一审查方案提出；不会因仍有 PENDING
而把建议写成批准基线。MySQL 类型为建议，未生成 SQL。

## 2. 依据与已确认边界

遵循 ADR-001 至
ADR-013、`logical-model.md`、`logical-model-review.md`、ChunkVector 与
FileObject 字段评审。关键约束：全局
User、多租户成员及角色；服务端可信身份授权；租户内 hash
文件复用；Document/Version/Task/ChunkVector/FileObject
状态职责分离；Chunk 全文存 MySQL、向量存 Qdrant；不可变 Profile 对应单
Collection；Chat/Citation 权限重验和必要快照；Worker
lease、幂等、重放、补偿；逻辑删除即时屏蔽检索；独立审计和配置化配额。

ChunkVector 已确认自然联合主键 `(chunk_id,embedding_profile_id)`、不存
`point_id`、状态扫描索引
`(embedding_profile_id,index_status,updated_at)`，且 `updated_at`
字段存在；其更新时间语义仍 PENDING。FileObject 已确认租户内同 SHA-256
复用、不跨租户、不物化
`ref_count`，并已确认有效引用口径及同行锁清理仲裁协议。所有
DocumentVersion（包括非活动历史版本）只要仍保留，均构成有效引用。具体协议见本文件第
9 节。

## 3. 统一数据规范（候选）

  -----------------------------------------------------------------------------------------------------------------------------------
  项目                    统一建议                                                                      状态
  ----------------------- ----------------------------------------------------------------------------- -----------------------------
  ID                      UUID v4/随机 UUID，MySQL `BINARY(16)`；Java UUID 与 Qdrant Point UUID         推导建议；UUID
                          使用同一网络序 16 字节编码                                                    版本与字节序最终确认 PENDING

  时间                    UTC `DATETIME(3)`，应用统一注入/更新；API 展示层转换时区                      推导建议

  创建/更新时间           `created_at`、`updated_at`                                                    推导建议
                          仅用于有生命周期/更新查询价值的实体；更新时间只在定义的状态/业务变更时更新    

  删除                    不全表套 `deleted_at`。Document 逻辑删除是即时检索门禁；FileObject            ADR 已确认语义，具体列
                          物理清理状态独立；成员禁用优先用 status；审计按保留策略管理                   PENDING

  状态                    `VARCHAR(24..32)` 候选，应用枚举校验；状态迁移由事务/条件更新控制；MySQL      推导建议
                          CHECK 是否可依赖 PENDING                                                      

  布尔值                  `BOOLEAN/TINYINT(1)`；仅表达稳定二态事实，不替代多态状态                      推导建议

  摘要                    SHA-256 `BINARY(32)` 推荐；不截断                                             推导建议，编码待确认

  文件/配额大小           文件长度 `BIGINT UNSIGNED` 候选；配额及用量统一字节整数，避免浮点             推导建议；配额周期/用量模型
                                                                                                        PENDING

  文本                    名称 160--255；邮箱 254；摘要/说明按产品限制；Chunk 正文 `LONGTEXT` 候选      推导建议，字符集/长度 PENDING

  JSON                    仅存不可变配置快照/结构化扩展；不能把关系、可过滤授权字段或高频索引字段藏入   推导建议
                          JSON                                                                          

  并发                    乐观版本列不默认全表添加；用唯一键、行锁、条件状态更新和 lease                PENDING
                          处理已有明确竞争点。高冲突资源再评估 `version`                                

  软删除                  状态与时间戳是否双存须有不同用途；不使用软删除绕过唯一性和租户约束            推导建议
  -----------------------------------------------------------------------------------------------------------------------------------

## 4. 全部实体字段候选

字段状态列中"推导建议"表示建议纳入候选结构，未获用户确认；"已确认"只标记字段/语义本身已由约束确定，不意味着类型细节均已定。PK
字段默认 NOT NULL。

### 4.1 User（全局用户）

  ----------------------------------------------------------------------------------------------------------------------------
  字段            MySQL 类型建议             PK           NULL 默认       用途 / 状态
  --------------- -------------- -------------- -------------- ---------- ----------------------------------------------------
  id              BINARY(16)                 是             否 无         全局身份；推导建议

  username        VARCHAR(100)               否             是 NULL       可选登录名；推导建议

  email           VARCHAR(254)               否             是 NULL       可选登录名；推导建议

  password_hash   VARCHAR(255)               否             否 无         凭证哈希，不存明文/token；已确认安全边界，算法参数
                                                                          PENDING

  display_name    VARCHAR(150)               否             否 无         展示名；推导建议

  status          VARCHAR(24)                否             否 无         账户可用状态；推导建议，枚举 PENDING

  created_at      DATETIME(3)                否             否 无         建立时间；推导建议

  updated_at      DATETIME(3)                否             否 无         修改时间；推导建议

  deleted_at      DATETIME(3)                否             是 NULL       仅在账户删除/保留语义确认后保留；PENDING
  ----------------------------------------------------------------------------------------------------------------------------

约束/索引候选：username、email 各自唯一（非空语义受 MySQL NULL
唯一规则影响）；至少一项非空的业务约束
PENDING。敏感值不写日志。索引另列在第 6 节。

### 4.2 Tenant

  -----------------------------------------------------------------------------------------
  字段         MySQL 类型建议             PK           NULL 默认       用途 / 状态
  ------------ -------------- -------------- -------------- ---------- --------------------
  id           BINARY(16)                 是             否 无         租户 ID；推导建议

  name         VARCHAR(160)               否             否 无         租户名称；推导建议

  status       VARCHAR(24)                否             否 无         租户生命周期；枚举
                                                                       PENDING

  created_at   DATETIME(3)                否             否 无         建立时间；推导建议

  updated_at   DATETIME(3)                否             否 无         修改时间；推导建议

  deleted_at   DATETIME(3)                否             是 NULL       是否软删及名称重用
                                                                       PENDING
  -----------------------------------------------------------------------------------------

租户名称唯一范围 PENDING；不擅自加全局唯一。

### 4.3 TenantMember

  --------------------------------------------------------------------------------------------------
  字段         MySQL                     PK           NULL 默认       用途 / 状态
               类型建议                                               
  ------------ ------------- -------------- -------------- ---------- ------------------------------
  id           BINARY(16)                是             否 无         关系行 ID；推导建议

  tenant_id    BINARY(16)                否             否 无         Tenant FK；已确认关系

  user_id      BINARY(16)                否             否 无         User FK；已确认关系

  role         VARCHAR(16)               否             否 无         OWNER/ADMIN/MEMBER；已确认值

  status       VARCHAR(24)               否             否 无         邀请/生效/禁用状态；枚举
                                                                      PENDING

  joined_at    DATETIME(3)               否             是 NULL       正式生效时间；推导建议

  created_at   DATETIME(3)               否             否 无         关系建立时间；推导建议

  updated_at   DATETIME(3)               否             否 无         关系变更时间；推导建议
  --------------------------------------------------------------------------------------------------

唯一 `(user_id,tenant_id)` 已确认。FK 分别指向 User/Tenant；索引
`(user_id,status)`、`(tenant_id,status,role)` 候选。

### 4.4 KnowledgeBase

  ------------------------------------------------------------------------------------------------------------
  字段                          MySQL 类型建议              PK           NULL 默认       用途 / 状态
  ----------------------------- --------------- -------------- -------------- ---------- ---------------------
  id                            BINARY(16)                  是             否 无         KB ID；推导建议

  tenant_id                     BINARY(16)                  否             否 无         租户归属；已确认

  name                          VARCHAR(160)                否             否 无         KB 名称；推导建议

  description                   VARCHAR(1000)               否             是 NULL       描述；推导建议

  active_embedding_profile_id   BINARY(16)                  否             是 NULL       当前 Profile；ADR-007
                                                                                         语义已确认；Profile
                                                                                         归属范围候选见复合 FK

  status                        VARCHAR(24)                 否             否 无         KB 生命周期；枚举
                                                                                         PENDING

  created_by                    BINARY(16)                  否             否 无         创建用户；推导建议

  created_at                    DATETIME(3)                 否             否 无         建立时间；推导建议

  updated_at                    DATETIME(3)                 否             否 无         修改时间；推导建议

  is_deleted                    TINYINT                     否             否 0          逻辑删除标记；0 正常、1 已删除；ADR-012

  deleted_at                    DATETIME(3)                 否             是 NULL       若采用软删；语义
                                                                                         逻辑删除时间；ADR-012
  ------------------------------------------------------------------------------------------------------------

必须提供 `UNIQUE(tenant_id,id)` 供租户复合 FK；名称唯一
`(tenant_id,name)` 仍需考虑大小写/排序规则与软删语义。已确认 Profile
采用系统预置+租户自建归属模型：系统 Profile 可被多个租户引用，租户
Profile 仅同租户 KB 可引用。建议 Profile 用 `(scope,owner_tenant_id,id)`
候选键并由 KB 以系统/同租户两类引用规则约束；普通单一 FK
无法表达"系统或同租户"的 OR，物理实现候选为双 nullable
FK/约束或服务层校验，最终方案 PENDING。

### 4.5 KnowledgeBaseMember

  ---------------------------------------------------------------------------------------------------------------------
  字段                MySQL                     PK           NULL 默认       用途 / 状态
                      类型建议                                               
  ------------------- ------------- -------------- -------------- ---------- ------------------------------------------
  id                  BINARY(16)                是             否 无         授权行 ID；推导建议

  tenant_id           BINARY(16)                否             否 无         创建后不可独立修改；已确认，复合租户约束

  knowledge_base_id   BINARY(16)                否             否 无         KB FK；已确认关系

  user_id             BINARY(16)                否             否 无         TenantMember 用户；已确认关系

  role                VARCHAR(32)               否             否 无         KB 内授权角色；值/矩阵 PENDING

  status              VARCHAR(24)               否             否 无         授权状态；枚举 PENDING

  created_by          BINARY(16)                否             否 无         授权操作者；推导建议

  created_at          DATETIME(3)               否             否 无         创建时间；推导建议

  updated_at          DATETIME(3)               否             否 无         修改时间；推导建议
  ---------------------------------------------------------------------------------------------------------------------

唯一 `(knowledge_base_id,user_id)` 已确认；必须建立复合 FK
`(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`，以及
`(tenant_id,user_id)` → TenantMember
`(tenant_id,user_id)`。父表复合唯一键须存在。`tenant_id`
创建后不可独立修改。OWNER/ADMIN 自动管理租户下 KB，MEMBER
需显式授权已确认；FK 不证明 TenantMember
ACTIVE，实时权限必须服务层校验。

### 4.6 FileObject

  ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  字段                  MySQL 类型建议             PK           NULL 默认       用途 / 状态
  --------------------- -------------- -------------- -------------- ---------- ------------------------------------------------------------------------------------------------------
  id                    BINARY(16)                 是             否 无         物理对象逻辑 ID；推导建议

  tenant_id             BINARY(16)                 否             否 无         去重与隔离范围；已确认概念

  storage_key           VARCHAR(512)               否             否 无         opaque key，非公开路径；本轮推荐待确认

  sha256                BINARY(32)                 否             否 无         租户内复用摘要；编码待确认

  byte_size             BIGINT                     否             否 无         文件长度；推导建议
                        UNSIGNED                                                

  media_type            VARCHAR(127)               否             否 无         检测 MIME；推导建议

  storage_status        VARCHAR(24)                否             否 无         STAGING→AVAILABLE→DELETE_PENDING→DELETING→DELETED；主状态仲裁协议已确认，失败状态/重试字段与退避策略
                                                                                PENDING

  created_at            DATETIME(3)                否             否 无         登记时间；推导建议

  updated_at            DATETIME(3)                否             否 无         状态更新时间；推导建议

  delete_requested_at   DATETIME(3)                否             是 NULL       清理请求时间；PENDING

  deleted_at            DATETIME(3)                否             是 NULL       物理删除完成时间；PENDING
  ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

已确认租户内同 hash 复用、不跨租户；`(tenant_id,sha256)`
是租户内唯一仲裁键。不物化 `ref_count`。所有仍保留的
DocumentVersion（含非活动历史版本）均算有效引用。上传新增引用与清理
claim 必须锁定同一 FileObject 行；仅 AVAILABLE
允许新增引用；清理事务持锁重查有效引用，无引用才原子置
DELETING。DELETING 禁止新增引用。清理失败保留原唯一行，禁止同 SHA
新建对象绕过；文件不存在视为删除成功；删除后 DB 写回失败时重试同一
storage_key。清理完成后新上传重新仲裁，不复用正在删除的对象。失败状态字段、退避及人工补偿仍
PENDING。原文件名归 Document/Version，不纳入此表。

### 4.7 Document

  -------------------------------------------------------------------------------------------------------------------------------------
  字段                MySQL 类型建议             PK           NULL 默认       用途 / 状态
  ------------------- -------------- -------------- -------------- ---------- ---------------------------------------------------------
  id                  BINARY(16)                 是             否 无         文档 ID；推导建议

  tenant_id           BINARY(16)                 否             否 无         冗余租户边界；推导建议

  knowledge_base_id   BINARY(16)                 否             否 无         所属 KB；已确认关系

  display_name        VARCHAR(255)               否             否 无         业务展示名；推导建议

  status              VARCHAR(24)                否             否 无         业务资源生命周期；枚举 PENDING

  active_version_id   BINARY(16)                 否             是 NULL       当前通过验证并可检索版本；推导建议

  created_by          BINARY(16)                 否             否 无         创建者；推导建议

  created_at          DATETIME(3)                否             否 无         建立时间；推导建议

  updated_at          DATETIME(3)                否             否 无         修改时间；推导建议

  deleted_at          DATETIME(3)                否             是 NULL       逻辑删除即时检索门禁；语义已确认，是否状态+时间双存待定
  -------------------------------------------------------------------------------------------------------------------------------------

至少建立 `UNIQUE(tenant_id,id)` 供租户复合引用；如确有按 KB+Document
组合引用需要，再评估 `(knowledge_base_id,id)`。复合 FK
`(tenant_id,knowledge_base_id)` → KnowledgeBase `(tenant_id,id)`
防跨租户错配。DocumentVersion 的活动版本关系采用
`UNIQUE(document_id,id)` 与复合 FK `(id,active_version_id)` →
DocumentVersion `(document_id,id)`；`active_version_id` 可
NULL。因循环依赖，先建表后添加该 FK。激活时事务锁定
Document，核验版本属于该文档且
READY/校验通过，再原子切换。检索前服务端还须检查 Document 当前状态。

### 4.8 DocumentVersion

  ----------------------------------------------------------------------------------------------------------------------
  字段                MySQL 类型建议              PK           NULL 默认       用途 / 状态
  ------------------- --------------- -------------- -------------- ---------- -----------------------------------------
  id                  BINARY(16)                  是             否 无         版本 ID；推导建议

  document_id         BINARY(16)                  否             否 无         所属 Document；已确认

  version_no          INT UNSIGNED                否             否 无         文档内递增版本；推导建议

  tenant_id           BINARY(16)                  否             否 无         创建时固定冗余租户；已确认

  source_file_id      BINARY(16)                  否             否 无         来源 FileObject；已确认关系

  source_sha256       BINARY(32)                  否             否 无         内容校验快照；推导建议

  media_type          VARCHAR(127)                否             否 无         识别格式快照；推导建议

  byte_size           BIGINT UNSIGNED             否             否 无         文件大小快照；推导建议

  original_filename   VARCHAR(255)                否             是 NULL       版本上传名；推导建议，归属待确认

  status              VARCHAR(24)                 否             否 无         版本产物可用性；枚举 PENDING

  processing_config   JSON                        否             是 NULL       解析/切片配置快照；推导建议，敏感值禁入

  error_code          VARCHAR(64)                 否             是 NULL       可稳定分类的最终错误；PENDING

  error_message       VARCHAR(1000)               否             是 NULL       脱敏摘要；是否由 Task 独占 PENDING

  created_by          BINARY(16)                  否             否 无         发起用户；推导建议

  created_at          DATETIME(3)                 否             否 无         版本创建时间；推导建议

  activated_at        DATETIME(3)                 否             是 NULL       成为活动版本时间；推导建议

  retired_at          DATETIME(3)                 否             是 NULL       退出活动时间；保留语义 PENDING
  ----------------------------------------------------------------------------------------------------------------------

唯一 `(document_id,version_no)` 与 `UNIQUE(document_id,id)`；复合 FK
`(tenant_id,document_id)` → Document
`(tenant_id,id)`、`(tenant_id,source_file_id)` → FileObject
`(tenant_id,id)`；后者确保文件引用不跨租户。创建时不可独立改 tenant_id
已确认。切换活动版本应在事务内验证新版本成功/索引校验并原子更新
Document；旧版清理由异步流程处理。

### 4.9 ProcessingTask

  -----------------------------------------------------------------------------------------------------------------
  字段               MySQL 类型建议              PK           NULL 默认       用途 / 状态
  ------------------ --------------- -------------- -------------- ---------- -------------------------------------
  id                 BINARY(16)                  是             否 无         Task ID；推导建议

  tenant_id          BINARY(16)                  否             否 无         调度/隔离作用域；已确认概念

  resource_type      VARCHAR(32)                 否             否 无         目标类型；多态引用，枚举 PENDING

  resource_id        BINARY(16)                  否             否 无         目标 ID；无普通 FK，PENDING

  task_type          VARCHAR(32)                 否             否 无         parse/index/delete/migration
                                                                              等；具体分类 PENDING

  status             VARCHAR(24)                 否             否 无         queued/running/retry/terminal
                                                                              控制状态；枚举 PENDING

  stage              VARCHAR(32)                 否             是 NULL       当前安全恢复阶段；与 status
                                                                              分离已确认

  priority           SMALLINT                    否             否 0 候选     调度优先级；必要性 PENDING

  available_at       DATETIME(3)                 否             否 无         可领取/退避到期时间；推导建议

  lease_owner        VARCHAR(128)                否             是 NULL       Worker 身份；推导建议

  lease_expires_at   DATETIME(3)                 否             是 NULL       租约截止；过期重领已确认

  heartbeat_at       DATETIME(3)                 否             是 NULL       最近心跳；频率 PENDING

  attempt_count      INT UNSIGNED                否             否 0 候选     执行尝试；推导建议

  max_attempts       INT UNSIGNED                否             是 NULL       尝试上限；策略 PENDING

  checkpoint         JSON                        否             是 NULL       幂等恢复游标；安全内容/结构 PENDING

  error_code         VARCHAR(64)                 否             是 NULL       最近错误分类；推导建议

  error_message      VARCHAR(1000)               否             是 NULL       脱敏错误摘要；禁止敏感信息，PENDING

  created_by         BINARY(16)                  否             是 NULL       用户触发者/系统任务；推导建议

  created_at         DATETIME(3)                 否             否 无         入队时间；推导建议

  started_at         DATETIME(3)                 否             是 NULL       最近开始执行时间；推导建议

  completed_at       DATETIME(3)                 否             是 NULL       终结时间；推导建议

  updated_at         DATETIME(3)                 否             否 无         调度状态更新时间；推导建议
  -----------------------------------------------------------------------------------------------------------------

索引候选 `(status,available_at,lease_expires_at,priority)` 与
`(tenant_id,status,created_at)`。没有普通 FK 校验多态
resource；候选方案为保留多态字段并服务层校验，避免本轮新增实体。租户/资源类型强约束或关联表属
PENDING，不在 17 实体基线加入。claim
应以条件更新/锁定保证单次租约持有者；过期重领后所有副作用必须幂等。

### 4.10 EmbeddingProfile

  --------------------------------------------------------------------------------------------------------------------------
  字段              MySQL 类型建议             PK           NULL 默认       用途 / 状态
  ----------------- -------------- -------------- -------------- ---------- ------------------------------------------------
  id                BINARY(16)                 是             否 无         Profile ID；推导建议

  scope             VARCHAR(16)                否             否 无         SYSTEM / TENANT；归属范围已确认，具体编码
                                                                            PENDING

  owner_tenant_id   BINARY(16)                 否             是 NULL       SYSTEM 时 NULL，TENANT 时 NOT
                                                                            NULL；归属约束已确认

  provider          VARCHAR(64)                否             否 无         模型提供方；推导建议

  model_name        VARCHAR(160)               否             否 无         模型标识；推导建议

  model_revision    VARCHAR(160)               否             是 NULL       固定模型版本/快照；是否必须 PENDING

  dimensions        INT UNSIGNED               否             否 无         向量维度；推导建议

  distance_metric   VARCHAR(24)                否             否 无         距离度量；候选枚举 PENDING

  collection_name   VARCHAR(255)               否             否 无         服务端生成管理且全局唯一；一个不可变 Profile 一
                                                                            Collection 已确认

  secret_ref        VARCHAR(512)               否             是 NULL       服务端 Secret
                                                                            引用/配置键，不存密钥；系统/租户配置边界已确认

  config_snapshot   JSON                       否             是 NULL       非密钥模型参数快照；秘密不得存入，PENDING

  status            VARCHAR(24)                否             否 无         provisioning/ready/retired 等候选；枚举 PENDING

  created_at        DATETIME(3)                否             否 无         创建时间；推导建议

  updated_at        DATETIME(3)                否             否 无         状态更新时间；推导建议
  --------------------------------------------------------------------------------------------------------------------------

`collection_name` 全局唯一已确认其规则；唯一键候选。SYSTEM Profile
可共享，TENANT Profile 仅可由相同 tenant_id 的 KB
引用。归属唯一性候选：SYSTEM 按模型配置全局唯一；TENANT 按
`(owner_tenant_id,provider,model_name,model_revision,dimensions,distance_metric)`
唯一，范围/是否允许重复 PENDING。KB 引用需约束 SYSTEM 或
owner_tenant_id=KB.tenant_id；租户 Profile 跨租户引用禁止。首版只开放
SYSTEM 创建已确认。Profile 配置不可变；密钥不明文存储已确认。

### 4.11 Chunk

  -------------------------------------------------------------------------------------------------------------
  字段                  MySQL                     PK           NULL 默认       用途 / 状态
                        类型建议                                               
  --------------------- ------------- -------------- -------------- ---------- --------------------------------
  id                    BINARY(16)                是             否 无         稳定 Chunk ID，也是派生 Qdrant
                                                                               Point UUID；已确认映射语义

  document_version_id   BINARY(16)                否             否 无         所属版本；已确认关系

  chunk_index           INT UNSIGNED              否             否 无         版本内顺序；推导建议

  content               LONGTEXT                  否             否 无         完整文本存 MySQL；已确认

  content_sha256        BINARY(32)                否             是 NULL       内容校验/对账；是否需要 PENDING

  token_count           INT UNSIGNED              否             是 NULL       估算 token
                                                                               数；配额/切片优化候选，PENDING

  metadata              JSON                      否             是 NULL       非授权性解析元数据；字段范围
                                                                               PENDING

  created_at            DATETIME(3)               否             否 无         生成时间；推导建议
  -------------------------------------------------------------------------------------------------------------

唯一 `(document_version_id,chunk_index)`；组合
`(document_version_id,id)` 供 Citation/ChunkVector 外键。按 version
扫描索引由唯一键左前缀满足。大文本不默认建全文索引。

### 4.12 ChunkVector

  ----------------------------------------------------------------------------------------------------------------------
  字段                   MySQL                     PK           NULL 默认       用途 / 状态
                         类型建议                                               
  ---------------------- ------------- -------------- -------------- ---------- ----------------------------------------
  chunk_id               BINARY(16)              是\*             否 无         Chunk 身份及 Point ID 来源；联合 PK
                                                                                已确认

  embedding_profile_id   BINARY(16)              是\*             否 无         Profile/Collection；联合 PK 已确认

  index_status           VARCHAR(24)               否             否 无         映射当前索引状态；状态职责已确认，枚举
                                                                                PENDING

  last_indexed_at        DATETIME(3)               否             是 NULL       最近 upsert 成功时间；字段名待确认

  last_error_code        VARCHAR(64)               否             是 NULL       最近失败摘要；保留待确认

  created_at             DATETIME(3)               否             否 无         映射登记时间；PENDING

  updated_at             DATETIME(3)               否             否 无         字段存在已确认；更新时间语义 PENDING
  ----------------------------------------------------------------------------------------------------------------------

\*联合主键 `(chunk_id,embedding_profile_id)` 已确认；不保存 `id` 或
`point_id` 已确认。索引 `(embedding_profile_id,index_status,updated_at)`
已确认纳入，updated_at 字段存在亦已确认。更新时间语义、FK
删除行为及其他字段仍 PENDING，详见字段评审稿。

### 4.13 ChatSession

  ----------------------------------------------------------------------------------------------------------------
  字段                MySQL 类型建议             PK           NULL 默认       用途 / 状态
  ------------------- -------------- -------------- -------------- ---------- ------------------------------------
  id                  BINARY(16)                 是             否 无         会话 ID；推导建议

  tenant_id           BINARY(16)                 否             否 无         授权/隔离范围；已确认绑定

  user_id             BINARY(16)                 否             否 无         所有者；已确认绑定

  knowledge_base_id   BINARY(16)                 否             否 无         检索范围；已确认绑定

  title               VARCHAR(255)               否             是 NULL       展示标题；推导建议

  status              VARCHAR(24)                否             否 无         会话可用/删除生命周期；枚举 PENDING

  created_at          DATETIME(3)                否             否 无         创建时间；推导建议

  updated_at          DATETIME(3)                否             否 无         最近活动时间；列表排序需求推导建议

  deleted_at          DATETIME(3)                否             是 NULL       用户主动删除后清理；策略 PENDING
  ----------------------------------------------------------------------------------------------------------------

候选组合 FK `(tenant_id,knowledge_base_id)` → KB、`(tenant_id,user_id)`
需相应 User/TenantMember 组合键（User 本身无 tenant）；成员授权无法纯 FK
表达，应每次请求校验。索引 `(tenant_id,user_id,updated_at)`。

### 4.14 ChatMessage

  -------------------------------------------------------------------------------------------------------------
  字段           MySQL 类型建议             PK           NULL 默认       用途 / 状态
  -------------- -------------- -------------- -------------- ---------- --------------------------------------
  id             BINARY(16)                 是             否 无         消息 ID；推导建议

  session_id     BINARY(16)                 否             否 无         所属会话；已确认关系

  sequence_no    BIGINT                     否             否 无         会话内顺序；推导建议
                 UNSIGNED                                                

  role           VARCHAR(16)                否             否 无         USER/ASSISTANT/SYSTEM/TOOL；已确认值

  content        LONGTEXT                   否             否 无         消息文本；推导建议

  status         VARCHAR(24)                否             否 无         流式生成中/完成/失败等候选；状态
                                                                         PENDING

  model_name     VARCHAR(160)               否             是 NULL       生成模型审计快照；是否保存 PENDING

  token_input    INT UNSIGNED               否             是 NULL       输入用量；容量统计候选，PENDING

  token_output   INT UNSIGNED               否             是 NULL       输出用量；容量统计候选，PENDING

  error_code     VARCHAR(64)                否             是 NULL       安全错误分类；PENDING

  created_at     DATETIME(3)                否             否 无         消息开始/入库时间；推导建议

  completed_at   DATETIME(3)                否             是 NULL       流完成时间；SSE 生命周期映射 PENDING
  -------------------------------------------------------------------------------------------------------------

唯一 `(session_id,sequence_no)`；长文本不做常规索引。SSE 的
message_start/delta/citation/done/error 是事件协议，不意味着每个 delta
均持久化；保存粒度/失败消息策略 PENDING。

### 4.15 Citation

  -----------------------------------------------------------------------------------------------------------------
  字段                      MySQL 类型建议             PK           NULL 默认       用途 / 状态
  ------------------------- -------------- -------------- -------------- ---------- -------------------------------
  id                        BINARY(16)                 是             否 无         引用 ID；推导建议

  message_id                BINARY(16)                 否             否 无         来源回答消息；已确认关系

  rank                      SMALLINT                   否             否 无         回答内排序；推导建议
                            UNSIGNED                                                

  tenant_id                 BINARY(16)                 否             否 无         授权/一致性边界候选；是否冗余
                                                                                    PENDING

  document_id               BINARY(16)                 否             否 无         来源 Document；已确认

  document_version_id       BINARY(16)                 否             否 无         精确版本；已确认

  chunk_id                  BINARY(16)                 否             否 无         精确 Chunk；已确认

  document_title_snapshot   VARCHAR(255)               否             是 NULL       必要历史快照；需确认范围

  excerpt_snapshot          TEXT                       否             是 NULL       必要引用片段；脱敏/长度/保留
                                                                                    PENDING

  source_locator            VARCHAR(512)               否             是 NULL       页码/段落等定位；格式 PENDING

  created_at                DATETIME(3)                否             否 无         创建时间；推导建议
  -----------------------------------------------------------------------------------------------------------------

候选 FK 保证 `(document_id,document_version_id)` 与
`(document_version_id,chunk_id)` 组合一致；若要求 tenant
一致则需组合键或服务层校验。每次读取源文件/Chunk
原文仍重新鉴权，快照不授予访问权，已确认。唯一
`(message_id,rank)`；索引按 message、来源文档访问场景选取。

### 4.16 AuditLog

  ----------------------------------------------------------------------------------------------------------------------------
  字段            MySQL                     PK           NULL 默认       用途 / 状态
                  类型建议                                               
  --------------- ------------- -------------- -------------- ---------- -----------------------------------------------------
  id              BINARY(16)                是             否 无         审计记录 ID；推导建议

  tenant_id       BINARY(16)                否             是 NULL       租户上下文；系统级事件可空，具体 PENDING

  actor_user_id   BINARY(16)                否             是 NULL       操作者/系统执行者；推导建议

  action          VARCHAR(64)               否             否 无         业务动作代码；字典 PENDING

  resource_type   VARCHAR(32)               否             是 NULL       目标类型；推导建议

  resource_id     BINARY(16)                否             是 NULL       目标 ID；多态，无普通 FK

  trace_id        VARCHAR(64)               否             是 NULL       请求关联；推导建议

  outcome         VARCHAR(16)               否             否 无         成功/拒绝/失败；枚举 PENDING

  details         JSON                      否             是 NULL       最小脱敏审计属性；不得含密码、token、原文等敏感信息

  created_at      DATETIME(3)               否             否 无         事件时间；推导建议
  ----------------------------------------------------------------------------------------------------------------------------

追加式写入、独立保留策略已确认；修改/删除权限、保留期限、归档机制
PENDING。索引 `(tenant_id,created_at)`、`(action,created_at)`
候选；审计表可按时间分区是后续容量决策，不先纳入基线。

### 4.17 TenantQuota

  ---------------------------------------------------------------------------------------------------
  字段           MySQL                     PK           NULL 默认       用途 / 状态
                 类型建议                                               
  -------------- ------------- -------------- -------------- ---------- -----------------------------
  id             BINARY(16)                是             否 无         配额行 ID；推导建议

  tenant_id      BINARY(16)                否             否 无         所属租户；已确认

  quota_key      VARCHAR(64)               否             否 无         配额种类；项目字典 PENDING

  limit_value    BIGINT                    否             否 无         限额，单位由 quota_key
                 UNSIGNED                                               定义；推导建议

  used_value     BIGINT                    否             否 0 候选     当前用量；是否物化/对账
                 UNSIGNED                                               PENDING

  period_start   DATETIME(3)               否             是 NULL       周期配额窗口；是否需要
                                                                        PENDING

  period_end     DATETIME(3)               否             是 NULL       周期结束；是否需要 PENDING

  updated_at     DATETIME(3)               否             否 无         最近用量/限额更新；推导建议
  ---------------------------------------------------------------------------------------------------

候选唯一 `(tenant_id,quota_key)`
仅适用于每租户每配额类型单行；多窗口/多资源维度时键需扩展，当前
PENDING。计量原子递增、超限并发控制、用量对账和豁免规则需按配额项目确定。

## 5. 关系、复合约束与租户隔离（统一基准）

本节列出当前已确认关系和可由现有模型直接推导的候选约束。父表复合唯一键必须先于子表复合外键建立。所有
`ON DELETE` / `ON UPDATE` 的最终动作仍需在 DDL
阶段按保留策略确认，不得默认级联删除业务数据。

### 5.1 身份与租户

-   `TenantMember UNIQUE(user_id,tenant_id)` 已确认；分别关联
    User、Tenant。
-   `KnowledgeBase UNIQUE(tenant_id,id)`，供租户内复合引用。
-   `KnowledgeBaseMember UNIQUE(knowledge_base_id,user_id)` 已确认。
-   `KnowledgeBaseMember(tenant_id,knowledge_base_id)` →
    `KnowledgeBase(tenant_id,id)`。
-   `KnowledgeBaseMember(tenant_id,user_id)` →
    `TenantMember(tenant_id,user_id)`。
-   `KnowledgeBaseMember.tenant_id` NOT NULL，创建后不可独立修改。
-   FK 只保证静态归属，不保证成员 ACTIVE
    或当前角色；请求时仍须服务层实时判权。

### 5.2 Document 与 DocumentVersion

-   `Document UNIQUE(tenant_id,id)`，供租户边界复合引用。
-   `Document(tenant_id,knowledge_base_id)` →
    `KnowledgeBase(tenant_id,id)`。
-   `DocumentVersion UNIQUE(document_id,id)`，供活动版本复合 FK。
-   `DocumentVersion(tenant_id,document_id)` →
    `Document(tenant_id,id)`。
-   `DocumentVersion(tenant_id,source_file_id)` →
    `FileObject(tenant_id,id)`。
-   `Document(id,active_version_id)` →
    `DocumentVersion(document_id,id)`；`active_version_id` 可 NULL。
-   Document 与 DocumentVersion
    存在循环依赖：先创建两表及父键，再后置添加活动版本 FK。
-   活动版本切换在事务内锁定 Document，核验目标版本归属及
    READY/索引验证状态后原子更新；失败不得切换，旧版本异步清理。

### 5.3 FileObject

-   `(tenant_id,sha256)` 唯一，租户内去重，不跨租户复用。
-   不物化 `ref_count`。
-   所有仍保留的 DocumentVersion（包括非活动历史版本）都是有效引用。
-   `DocumentVersion(tenant_id,source_file_id)` 的复合 FK
    防止跨租户引用文件。
-   Hash 唯一行在清理失败时必须保留；不得插入同 SHA 新行绕过清理。

### 5.4 Chunk、ChunkVector 与 Citation

-   `Chunk UNIQUE(document_version_id,id)`。
-   `Chunk(document_version_id)` → `DocumentVersion(id)`。
-   `ChunkVector` 自然联合主键
    `(chunk_id,embedding_profile_id)`；`chunk_id` →
    `Chunk(id)`，`embedding_profile_id` → `EmbeddingProfile(id)`。
-   ChunkVector 不保存 `point_id`；由 chunk_id 按统一 UUID 编码规范派生
    Qdrant Point ID。
-   `Citation(document_id,document_version_id)` →
    `DocumentVersion(document_id,id)`。
-   `Citation(document_version_id,chunk_id)` →
    `Chunk(document_version_id,id)`。
-   `Citation.message_id` → `ChatMessage(id)`。
-   上述复合关系确保 Citation 指向的版本属于指定 Document，Chunk
    又属于指定版本；不能仅依靠多个互不关联的单列 FK。

### 5.5 Chat、Task 与 Profile 的边界

-   ChatSession 的静态身份关系可由普通 FK 保证；用户成员关系、KB
    授权及当前权限由服务层实时校验。
-   不为 ChatSession/Citation 盲目增加 tenant/user/KB
    冗余字段，只为追求更多 FK。
-   ProcessingTask 保留 `resource_type + resource_id`
    多态引用，不新增实体；资源存在、类型匹配和 tenant 归属由服务层校验。
-   EmbeddingProfile 的 SYSTEM/TENANT 归属规则已确认，但"系统 Profile
    或同租户 Profile"是 OR 规则，单个普通 FK
    无法完整表达。首版采用服务层不可绕过的引用校验；数据库 scope/owner
    CHECK 及可空双引用路径属于实现方案 PENDING。
-   SYSTEM Profile 可被多个租户 KB 引用；TENANT Profile 仅同租户 KB
    可引用。首版只开放 SYSTEM 创建。Collection
    名称服务端生成且全局唯一。

### 5.6 数据库与服务层责任

数据库约束负责静态身份、唯一性、租户复合归属及版本/Chunk
引用链；服务层负责动态成员状态、角色、当前权限、Profile OR
归属授权、业务状态、外部文件存在性和 Qdrant
状态。所有敏感读取、下载、检索和管理操作均须基于可信身份重新授权。

## 6. 唯一键和索引候选

  ---------------------------------------------------------------------------------------------------------------------------------------------
  实体                    唯一/主键候选                                                       查询索引候选及用途
  ----------------------- ------------------------------------------------------------------- -------------------------------------------------
  User                    PK id；username/email 分别唯一（非空规则待定）                      status 仅当账户管理列表查询需要

  Tenant                  PK id                                                               status/updated_at 按租户管理查询需要

  TenantMember            UNIQUE(user_id,tenant_id) 已确认                                    (user_id,status)
                                                                                              列出我的租户；(tenant_id,status,role) 管成员

  KnowledgeBase           UNIQUE(tenant_id,id)；可能 UNIQUE(tenant_id,name)                   (tenant_id,status,updated_at) 列表

  KnowledgeBaseMember     UNIQUE(knowledge_base_id,user_id) 已确认；FK 依赖 KB/ TenantMember  (user_id,status,knowledge_base_id) 查授权 KB
                          复合唯一键                                                          

  FileObject              UNIQUE(tenant_id,sha256) 已确认；失败清理保留原行，不允许同 hash    (storage_status,updated_at) 清理/重试；tenant_id
                          绕过                                                                已由复合键前缀覆盖

  Document                UNIQUE(tenant_id,id)；活动版本复合 FK 依赖 DocumentVersion 父键     (knowledge_base_id,status,created_at)
                                                                                              列表；(tenant_id,status,deleted_at) 清理/租户过滤

  DocumentVersion         UNIQUE(document_id,version_no)、UNIQUE(document_id,id)；tenant_id   (document_id,status,created_at) 版本/状态查询
                          复合 FK 依赖 Document/FileObject 父键                               

  ProcessingTask          PK id                                                               (status,available_at,lease_expires_at,priority)
                                                                                              领取；(tenant_id,status,created_at) 管理列表

  EmbeddingProfile        PK id；collection_name 唯一候选                                     status/updated_at 仅迁移任务需要

  Chunk                   UNIQUE(document_version_id,chunk_index)、(document_version_id,id)   首个唯一键左前缀供版本枚举；不为正文常规建索引
                          候选                                                                

  ChunkVector             PK(chunk_id,embedding_profile_id) 已确认                            (embedding_profile_id,index_status,updated_at)
                                                                                              已确认

  ChatSession             PK id                                                               (tenant_id,user_id,updated_at) 会话列表

  ChatMessage             UNIQUE(session_id,sequence_no)                                      PK/唯一键前缀按会话顺序读取

  Citation                UNIQUE(message_id,rank)                                             (document_id,document_version_id)
                                                                                              反查引用（若需要）

  AuditLog                PK id                                                               (tenant_id,created_at)、(action,created_at)
                                                                                              审计查询

  TenantQuota             UNIQUE(tenant_id,quota_key) 条件候选                                唯一键已支持按租户配额读取
  ---------------------------------------------------------------------------------------------------------------------------------------------

不得把全部候选索引直接落地；应从预计 SQL、数据分布和 EXPLAIN
验证。复合索引覆盖左前缀时不重复创建单列索引。FK 索引是否由 MySQL
自动补建须在目标版本确认。

## 7. 状态机候选

所有具体名称均为**推导建议/PENDING**，只有状态职责分离为已确认：

  -----------------------------------------------------------------------------------------------------------------------------
  对象                    候选状态和转换                                        一致性要求
  ----------------------- ----------------------------------------------------- -----------------------------------------------
  User/Tenant/KB          ACTIVE ↔ SUSPENDED；ACTIVE → DELETED（候选）          禁用/删除影响授权需同步门禁；级联业务流程未定

  TenantMember/KBMember   INVITED → ACTIVE ↔ DISABLED/REVOKED                   请求时重新判权；重邀是否复用行/保留历史 PENDING

  FileObject              STAGING → AVAILABLE → DELETE_PENDING → DELETING →     清理 claim 与新引用原子仲裁；外部文件操作幂等
                          DELETED；失败进入 retryable failed 候选               

  Document                ACTIVE/PROCESSING/FAILED/DELETING/DELETED 候选        逻辑删除提交后立即停止检索；不要混入解析阶段

  DocumentVersion         RECEIVED → PROCESSING → READY/FAILED →                新版成功校验后原子切换 active；旧版可继续服务
                          RETIRED/DELETE_PENDING 候选                           

  ProcessingTask.status   QUEUED → RUNNING →                                    lease 过期可重领；attempt/checkpoint
                          SUCCEEDED/RETRY_WAIT/FAILED/CANCELLED 候选            幂等；状态与 stage 分离

  ProcessingTask.stage    UPLOADED/QUEUED/PARSING/CHUNKING/EMBEDDING/INDEXING   阶段不是任务终态，不全部放入 Document.status
                          等 ADR 阶段候选                                       

  EmbeddingProfile        PROVISIONING → READY → RETIRED/DELETE_PENDING 候选    Profile 配置不可变；KB 切换前完成迁移验证；旧
                                                                                Collection 保留期 PENDING

  ChunkVector             PENDING → INDEXING → INDEXED；FAILED                  每点状态映射 Qdrant
                          可重试；DELETE_PENDING → DELETED 候选                 结果；双系统无共同事务，重放/对账

  ChatSession/Message     ACTIVE/DELETED；Message GENERATING → COMPLETED/FAILED SSE 流结束写回状态策略 PENDING
                          候选                                                  

  AuditLog                append-only，无业务状态                               独立保留与脱敏
  -----------------------------------------------------------------------------------------------------------------------------

实现原则：状态迁移通过带预期旧状态的条件更新或短事务；避免"先宣告完成、后执行外部副作用"。外部写入成功、DB
写回失败时采用稳定幂等键重放；物理对象不存在可视作删除成功。具体退避、租约和状态名不在本轮批准。

## 8. FileObject 引用计数与清理协议（已确认）

### 8.1 引用计数

-   **已确认：不物化 `ref_count`。**
-   DocumentVersion 与 FileObject 的引用关系是唯一事实来源。
-   所有仍保留的 DocumentVersion 均为有效引用，包括非活动历史版本。
-   Document
    逻辑删除会立即停止检索，但不会自动使历史版本引用失效；只有版本按保留/清理流程完成清理后，才不再构成有效引用。
-   不得仅以 Document 是否逻辑删除、是否活动版本来判断文件可清理。

### 8.2 状态和仲裁原则

主状态流转候选：`STAGING → AVAILABLE → DELETE_PENDING → DELETING → DELETED`。失败状态名称、重试字段、退避和人工补偿仍
PENDING；但以下仲裁协议已经确认，不得再列为待确认：

1.  上传先写临时对象并计算 SHA-256，以 `(tenant_id,sha256)` 唯一键仲裁。
2.  创建 DocumentVersion 引用时，事务锁定对应 FileObject 行；只有
    `AVAILABLE` 才允许新增引用。
3.  清理 Worker 在事务中锁定同一 FileObject 行，重新检查所有仍保留的
    DocumentVersion 引用。
4.  只有不存在有效引用时，才能在同一事务内原子切换为 `DELETING`
    并领取清理。
5.  `DELETING` 状态禁止新增引用；上传不能复用正在删除的对象。
6.  清理事务提交后，在事务外使用该行原 `storage_key` 删除物理对象。
7.  删除成功或文件本来不存在，均视为物理删除成功；随后幂等写回
    `DELETED`。
8.  文件删除成功但数据库写回失败时，重试同一个
    `storage_key`；不能因对象不存在而报永久失败。
9.  清理失败时保留原 FileObject 唯一行，不释放
    `(tenant_id,sha256)`，不允许新建同 SHA 行绕过。
10. 清理完成后新上传重新进行唯一键及行状态仲裁，不复用正在删除的对象。

### 8.3 跨系统一致性

MySQL
与本地文件系统没有共同事务。上传临时对象、数据库登记失败、物理删除成功但状态写回失败等情况必须依赖稳定
storage_key、幂等 adapter、orphan
扫描、重试和对账恢复。不得在长事务中执行文件系统 I/O。

## 9. 并发、一致性与生命周期

-   **上传去重：** 先计算 SHA-256，`(tenant_id,sha256)`
    唯一键作为并发仲裁点；冲突后锁定现有行，核验租户、摘要、大小、状态及对象存在性。只有
    AVAILABLE 可复用。
-   **引用与清理：** 新版本建立 FileObject 引用和清理 claim
    锁定同一行；清理事务重新检查所有仍保留版本。逻辑删除 Document
    不会自动撤销历史版本引用。
-   **活动版本：** 在一个 DB 事务中锁定
    Document，验证目标版本归属、READY 和索引校验，原子更新
    active_version；保留旧版直到清理策略允许。
-   **任务领取：** 使用 MySQL 锁/条件更新方案；lease
    超时只保证可重新执行，不保证 exactly-once。目标外部操作按 ID 幂等。
-   **Qdrant：** KB 检索强制 tenantId、knowledgeBaseId filter；Document
    逻辑删除以 MySQL 权威状态立即过滤；Qdrant 删除异步补偿。
-   **Profile 迁移：** 新 Profile/Collection 全量准备并校验后切换 KB
    活跃指针；旧 Collection
    保留回滚。增量水位、切换时并发写入策略、保留期限 PENDING。
-   **Chat/Citation：** 每次访问重新校验 session 当前用户/租户/KB
    权限；必要历史快照用于历史展示，不赋予下载或源正文权限；读取源文件或
    Chunk 原文重新授权。
-   **配额：** `used_value`
    若物化需原子更新、幂等扣减/释放与重算；配额种类、周期、超限语义
    PENDING。

## 10. 设计风险及 ADR 冲突检查

未发现本候选有意改写 ADR-001 至 ADR-013 的 Accepted
决策；存在以下需保留的落地风险：

1.  MySQL 版本及迁移/ORM 工具未定，影响 UUID、JSON、CHECK、复合
    FK、索引长度和 Worker claim 方案。
2.  ADR-005
    同租户去重与异步清理并存；唯一键、有效引用口径和上传/清理同行锁协议已确认。仍待确定的是失败状态字段、退避、人工补偿及其具体实现。
3.  ADR-006/007/012 要求版本切换、Profile 迁移和删除门禁；外部 Qdrant
    状态最终一致，不能仅以向量删除完成作为权限控制。
4.  ADR-009 多态 task target 不能由普通 FK 保证资源类型与
    tenant；若保留多态字段，服务层校验不可缺。
5.  租户组合 FK 可防止归属错配，但动态 membership/KB role
    必须实时鉴权，DB 外键不能替代授权。
6.  Citation
    快照范围/保留与文档删除策略需协调；既要必要历史展示，又不能绕过权限读取源内容。
7.  审计、Chat、文件及向量规模保留/归档策略未知；ADR-013
    容量数字是规划目标，不是已验证性能。
8.  当前业务代码尚未实现这些模型，所有字段、索引与状态都是设计候选，不得描述为已实现。

## 11. PENDING 清单（按阻塞优先级）

### P0：DDL 草案前/草案中必须明确的实现选择

1.  目标 MySQL 版本、迁移工具、数据访问方式（MyBatis/JDBC/JPA）与 UUID
    `BINARY(16)` 字节序。
2.  User 登录标识唯一/大小写规则、账户/租户删除语义及成员重邀历史策略。
3.  Document、DocumentVersion、ProcessingTask、EmbeddingProfile、ChunkVector、Chat
    等状态枚举及合法转移；FileObject
    的失败状态字段、退避、人工补偿字段。
4.  Document 活动版本循环 FK 的目标 MySQL 实现、FK 创建顺序与 ON
    DELETE/ON UPDATE 行为。
5.  Profile SYSTEM/TENANT OR
    引用规则的数据库约束能力；服务层引用授权校验不可绕过。
6.  所有复合 FK 在目标 MySQL/Mapper 下的实现与删除行为。
7.  FileObject 已确认的 hash 唯一、同行锁、有效引用、DELETING
    禁止新引用协议，须在 DDL/实现设计中完整承载；这些业务决策本身不是
    PENDING。

### P1：决定字段、约束和查询索引

1.  KnowledgeBaseMember 角色矩阵、状态与历史行策略；KB 名称唯一规则。
2.  ProcessingTask
    多态目标类型、任务类型/阶段/重试上限/租约时长/checkpoint 格式和
    claim 查询。
3.  EmbeddingProfile 必需版本字段、Collection
    名生成规则、状态和配置快照边界。
4.  FileObject provider/key、MIME、文件名归属、清理时间列与存储 key
    唯一范围。
5.  Citation 快照字段、脱敏、长度及保留/删除规则；Chat SSE
    消息持久化粒度。
6.  配额种类、单位、周期、并发扣减、重算及超限处理。
7.  审计 action 字典、系统事件 tenant 可空语义、保留/归档策略。
8.  所有列表/清理/领取
    SQL、过滤顺序、分页与数据规模，以执行计划确定索引。

### P2：实施前后验证

1.  所有字段最大长度、排序规则、字符集、时区转换与错误脱敏。
2.  大表分页/归档/分区阈值；长文本上限与 Chunk token 统计方法。
3.  ADR-013 规划负载（100 租户、1000 用户、20 Chat、5
    Worker）下的基准与压力验证。
4.  备份恢复一致性：MySQL 与本地文件/Qdrant
    联合恢复、孤儿扫描和对账告警。

## 12. 下一阶段落地影响

-   DDL 需从本候选中只选定字段/约束，按依赖顺序建
    Tenant/User、关系、KB/Profile、文件/文档/版本、Chunk/Vector、任务、Chat/Citation、审计/配额；循环
    FK 需单独安排。
-   Java Entity/Mapper 需实现 UUID `BINARY(16)` 统一 codec、复合键
    ChunkVector、tenant
    组合条件、状态条件更新和分页查询；当前技术栈未决定 ORM，不绑定 JPA
    注解方案。
-   Mapper
    应使用数据库唯一冲突而非先查后插保证成员/文件/版本/消息幂等；外部存储和
    Qdrant 操作在事务外，并有状态重放与对账。
-   每一项物理索引需由实际查询及目标 MySQL
    执行计划核验；不能将候选索引整体照搬为生产 schema。
-   API 必须从认证上下文推导 tenant，不把数据库 tenant_id
    约束误当作授权系统。

## 13. 评审结论

本文件覆盖逻辑模型全部 17
个既有实体，统一字段表、类型建议、主键/唯一键/FK/索引、租户隔离、状态候选、生命周期与风险。已确认决策已落实；真正未决事项继续标记
PENDING。未增加正式实体，未修改逻辑模型/ADR，未生成
DDL/SQL，未实施数据库结构。本文件是物理设计唯一基准，但仍不是已冻结
DDL。

## 14. 合并依据与本轮修订记录

本文件整合以下资料的有效内容，避免它们继续作为平行权威设计源：

-   `physical-design-candidate.md`：17
    个实体字段全集及原有类型/索引候选。
-   `physical-design-review.md`：第二轮关系修订、Profile 归属、KBMember
    复合约束、Document 活动版本关系、Chunk/Citation 引用链和 FileObject
    清理协议。
-   `field-reviews/chunk-vector-review.md`：ChunkVector
    三项已确认字段决策及其余 PENDING。
-   `field-reviews/file-object-review.md`：FileObject
    字段、去重、引用与生命周期讨论；其中早期 PENDING
    已由后续用户确认覆盖的部分，按本文件已确认结论更新。

本文件优先级高于上述旧候选稿/字段评审稿。旧文件保留仅用于历史追溯，不得作为
Codex 当前设计依据。

### 本轮明确已确认、不得重新询问

1.  FileObject 不物化 `ref_count`。
2.  所有仍保留的 DocumentVersion（包括非活动历史版本）均为 FileObject
    有效引用。
3.  新增引用与清理 claim 锁同一 FileObject 行；仅 AVAILABLE
    允许新增引用。
4.  清理事务锁行后重新检查引用，无有效引用才原子切换 DELETING；DELETING
    禁止新增引用。
5.  清理失败保留原唯一 hash 行，不允许同 SHA
    新建绕过；物理文件不存在视为删除成功；写回失败重试同一 storage_key。
6.  EmbeddingProfile SYSTEM/TENANT 归属、首版仅开放 SYSTEM 创建、系统
    Profile 可共享、租户 Profile 仅同租户引用。
7.  KnowledgeBaseMember 两个租户复合外键及唯一键。
8.  Document 活动版本复合 FK、DocumentVersion `(document_id,id)`
    唯一键。
9.  Chunk/Citation 来源链复合约束。
10. ChunkVector 自然联合主键、不保存 point_id、状态扫描索引；updated_at
    字段存在，但更新时间语义仍 PENDING。

# 架构决策记录（ADR）索引

本目录记录正式架构决策。ADR 状态表示决策评审状态，不表示代码已实现。`Accepted` 表示项目负责人已确认目标约束；PENDING 表示该 ADR 中明确列出的细节尚未确认。具体实施仍需遵守根目录 `AGENTS.md` 的代码、数据库和文档规则。

## Accepted 决策

| ADR | 标题 | 状态 |
|---|---|---|
| [ADR-001](ADR-001-user-tenant-membership.md) | 用户与租户成员关系 | Accepted |
| [ADR-002](ADR-002-authentication.md) | Spring Security 与 JWT 认证 | Accepted |
| [ADR-003](ADR-003-tenant-kb-authorization.md) | 租户和知识库授权 | Accepted |
| [ADR-004](ADR-004-qdrant-vector-storage.md) | Qdrant Collection 与 Embedding Profile | Accepted |
| [ADR-005](ADR-005-file-storage.md) | 本地文件存储与去重 | Accepted |
| [ADR-006](ADR-006-chunk-data-model.md) | DocumentVersion 与 Chunk 数据 | Accepted |
| [ADR-007](ADR-007-embedding-migration.md) | Embedding Profile 迁移 | Accepted |
| [ADR-008](ADR-008-chat-and-sse.md) | Chat 会话、消息、引用与 SSE | Accepted |
| [ADR-009](ADR-009-document-processing.md) | 文档处理任务与 Worker | Accepted |
| [ADR-010](ADR-010-api-and-security.md) | API 规范与接口安全 | Accepted |
| [ADR-011](ADR-011-deployment-and-operations.md) | 部署与运维 | Accepted |
| [ADR-012](ADR-012-data-lifecycle-and-quota.md) | 数据生命周期与配额 | Accepted |
| [ADR-013](ADR-013-capacity-and-performance.md) | 容量与性能目标 | Accepted（目标待压测） |

## 记录规则

- 13 项决策来自用户确认的架构决策评审。背景/理由中的补充分析不冒充历史会议结论，文中明确标注“分析补充”。
- Accepted 决策内容不得被分析建议静默改变；如需改变，应由用户重新决策并新增 superseding ADR/版本记录。
- 尚未确认的问题继续以 `PENDING` 标记，不会阻塞已确认决策记录。
- C1-C5 的处理是实现约束协调或保留 PENDING，不将分析报告的推荐伪装成 Accepted 新业务决策。
- 本目录的 ADR 是目标架构；实际实现状态参见 [`../architecture/README.md`](../architecture/README.md)，分析结论参见 [`../architecture/consistency-analysis.md`](../architecture/consistency-analysis.md)。

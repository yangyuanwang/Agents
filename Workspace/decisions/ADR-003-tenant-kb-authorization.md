# ADR-003：租户和知识库授权

## 状态

Accepted

## 决策背景

租户隔离和知识库成员访问需要同时支持。当前后端仅接受请求 tenantId 并用于向量过滤，没有身份或资源授权。

## 决策内容

- TenantMember 租户角色为 `OWNER`、`ADMIN`、`MEMBER`。
- `OWNER`、`ADMIN` 自动拥有所属租户内知识库的管理权限。
- `MEMBER` 必须通过 `KnowledgeBaseMember` 获得明确授权。
- 后端依据可信身份和数据库权限校验资源访问。
- 不信任客户端传入的 tenantId/userId 作为授权依据。

## 备选方案

- 只按租户角色统一开放所有 KB：不符合 MEMBER 显式授权要求。
- 由前端隐藏按钮实施授权：不能保护直接 API 调用。

## 决策理由

**分析补充：** 两级授权可保留租户管理职责，同时支持知识库级成员授权。

## 影响及约束

所有 tenant-scoped API 必须先验证 TenantMember；访问 KB 资源还须执行 KB 归属与成员授权检查。Qdrant Payload Filter 仅是数据检索过滤，不替代业务授权。

## 相关决策

ADR-001、ADR-002、ADR-004、ADR-010。

## 尚未解决的问题

- PENDING：`KB_ADMIN`、`EDITOR`、`VIEWER` 等知识库角色及完整权限矩阵。
- PENDING：系统级管理员的身份及其跨租户权限边界。
- PENDING：OWNER/ADMIN 对知识库成员管理、检索和删除的细分权限，按最终矩阵确认。

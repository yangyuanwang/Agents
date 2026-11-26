# ADR-002：Spring Security 与 JWT 认证

## 状态

Accepted

## 决策背景

系统需要为用户和多租户 API 建立可信身份上下文。当前前后端未实现登录认证，后端没有 Spring Security。

## 决策内容

- 后端使用 Spring Security。
- 使用 JWT 作为认证机制。
- 支持用户名或邮箱 + 密码登录。
- 后端基于已验证身份建立可信 Principal，供授权层使用。

## 备选方案

- 无认证，仅依赖客户端 tenantId：无法建立可信授权边界。
- 服务端 session/cookie：未被本次评审选定。

## 决策理由

**分析补充：** JWT 与 API 服务的身份传递方式相容，并可由 Spring Security 统一处理认证过滤链；此为技术分析，不是额外确认的 token 细节。

## 影响及约束

所有受保护 API 必须在业务授权前验证身份。JWT 的具体签发、保存和密钥管理需遵循后续安全实现约定；不得在前端自行信任 userId/tenantId。

## 相关决策

ADR-001、ADR-003、ADR-010。

## 尚未解决的问题

- PENDING：Access Token 与 Refresh Token 生命周期、刷新、撤销和多设备策略。
- PENDING：密码哈希参数、找回/修改密码及账户锁定策略。
- PENDING：禁用 User/TenantMember 后已签 JWT 的即时失效要求。

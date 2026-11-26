# ADR-011：部署与运维

## 状态

Accepted

## 决策背景

当前 Compose 只定义 Qdrant，尚无全系统部署、监控、备份或恢复演练方案。

## 决策内容

- 使用 Docker Compose 单机部署，预留未来扩展能力。
- DEV、TEST、PROD 三套环境隔离。
- 第一阶段使用 Actuator + Micrometer；后续按需引入 Prometheus + Grafana。
- 联合备份 MySQL、原始文件、Qdrant。
- 定期执行恢复演练，恢复后校验数据一致性。

## 备选方案

- 首版立即部署完整指标平台：本轮决策为先 Actuator + Micrometer。
- 只备份 MySQL：无法恢复文件和向量索引完整状态。

## 决策理由

**分析补充：** Compose 适合首版单机运行；三类持久数据需在恢复流程中保持对应关系。

## 影响及约束

Compose 当前只有 Qdrant，不代表系统部署已完成。配置、凭证、数据卷需环境隔离。联合备份应考虑 DocumentVersion、FileObject 与 Qdrant Profile/Collection 的映射和恢复顺序，但恢复流程细节待定。

## 相关决策

ADR-005、ADR-007、ADR-009、ADR-010、ADR-012、ADR-013。

## 尚未解决的问题

- PENDING：备份频率、保留周期、RPO/RTO、加密/异地副本及演练频率。
- PENDING：DEV/TEST/PROD 环境具体拓扑和 secrets 管理。
- PENDING：MySQL 版本与生产部署方式。

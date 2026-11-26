# ADR-009：文档处理任务与 Worker

## 状态

Accepted

## 决策背景

文档解析、切片和 Embedding 是异步且可能失败的流程。当前后端没有文档处理任务或 Worker。

## 决策内容

- 第一版使用 MySQL 任务表、Spring Boot Worker、定时扫描和领取任务。
- 处理阶段基线为 `UPLOADED`、`QUEUED`、`PARSING`、`CHUNKING`、`EMBEDDING`、`INDEXING`、`COMPLETED`、`FAILED`、`CANCELLED`。
- 从可安全恢复阶段继续执行。
- 关键操作幂等。
- 使用任务租约/锁定防止多个 Worker 重复执行同一任务。
- 新版本失败时保留旧版本继续检索。
- 暂不引入 RabbitMQ/Kafka。

## 备选方案

- 同步在上传请求中完成完整处理：受模型和文件处理时长影响，难以恢复。
- 第一版引入独立消息 Broker：本轮明确暂不急于引入。

## 决策理由

**分析补充：** MySQL 持久任务和轮询可在首版减少基础设施，同时支持崩溃后恢复；至少一次执行配合幂等比假设 exactly-once 更可实现。

## 影响及约束

状态职责协调：Document 表示业务资源可用/删除生命周期，DocumentVersion 表示特定内容版本/索引产物状态，ProcessingTask 表示执行尝试/阶段。ADR-009 阶段名不能未经映射全部放进单一 Document 状态字段。此职责分离为实现约束；具体状态字段、枚举/转移矩阵 PENDING。

## 相关决策

ADR-005、ADR-006、ADR-007、ADR-011、ADR-012、ADR-013。

## 尚未解决的问题

- PENDING：任务超时、取消、人工重试、补偿和租约时长/心跳。
- PENDING：Worker checkpoint、并行度配置、任务优先级与公平调度。
- PENDING：Profile 迁移期间的任务双目标/增量同步机制（ADR-007）。

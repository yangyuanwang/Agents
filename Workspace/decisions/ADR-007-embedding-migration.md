# ADR-007：Embedding Profile 迁移

## 状态

Accepted

## 决策背景

Embedding 模型或向量空间变化时需要避免不兼容向量混用，并保留验证和回滚窗口。当前没有 Profile 注册/迁移实现。

## 决策内容

- 新模型使用新的 Collection。
- 全量迁移完成并通过验证后切换。
- 按知识库整体切换 Embedding Profile。
- 旧 Collection 保留一段时间以支持回滚。
- 使用固定测试集评估 Recall@K、MRR、命中率和延迟。

## 备选方案

- 在原 Collection 原位覆盖向量：难以回滚，且模型空间可能不兼容。
- 只以向量维度判断新旧模型兼容：决策明确禁止。

## 决策理由

**分析补充：** 新旧 Collection 并存让重建和质量验证可分离，按知识库切换限制切换范围。

## 影响及约束

Collection 属于 Profile；按知识库切换要求系统可解析每个 KB 当前有效 Profile，检索只在与 KB 当前 Profile 匹配的 Collection/索引中执行。此为实现约束，不规定具体数据库字段。Chunk 与 Profile 的多份索引状态需与 ADR-006 协调。

## 相关决策

ADR-004、ADR-006、ADR-009、ADR-013。

## 尚未解决的问题

- PENDING：全量迁移期间新增、更新、重处理、删除的增量同步机制。
- PENDING：追平判定、水位/校验标准、Profile 原子切换、失败回滚和旧 Collection 保留时长。
- PENDING：模型/provider 选型及测试集版本、阈值和验收门槛。

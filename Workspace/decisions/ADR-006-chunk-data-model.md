# ADR-006：DocumentVersion 与 Chunk 数据

## 状态

Accepted

## 决策背景

文档内容可能重新处理，切片需可追溯、可重建向量。当前没有 Document/Chunk 业务模型或切片管道。

## 决策内容

- MySQL 保存完整 Chunk 文本。
- Chunk ID 与 Qdrant Point ID 相同；Point ID 使用 Qdrant 支持的格式，建议 UUID，不假设任意字符串有效。
- 使用 DocumentVersion 进行版本化。
- 新版本处理完成并校验后再切换生效版本。
- 保存必要处理元数据，例如 chunk_index、token_count、字符范围、document_version_id、content_hash。

## 备选方案

- 只在 Qdrant 保存文本：难以作为业务版本事实和稳定重建来源。
- 直接覆盖旧 DocumentVersion/Chunk：无法在新版本失败时保留旧版本。

## 决策理由

**分析补充：** 保存原文与不可变版本有助于重建索引、追踪引用和安全切换版本。

## 影响及约束

Chunk 是稳定文本片段，向量索引随 Embedding Profile 变化。协调 ADR-004/007：同一 Chunk UUID 可在不同 Profile 的独立 Collection 作为 point ID；索引状态必须能按 Profile 区分。**此为实现约束，不表示已批准新增 ChunkVector 表。** 若无法以 Chunk ID 唯一表达多索引状态，需独立映射方案，但具体持久化形态 PENDING。旧 database 文档中的单一 `qdrant_point_id` 不可直接作为最终设计。

## 相关决策

ADR-004、ADR-005、ADR-007、ADR-008、ADR-009、ADR-012。

## 尚未解决的问题

- PENDING：ChunkVector/ChunkIndex 是否为独立逻辑实体/表，以及 point ID 映射和索引状态字段。
- PENDING：字符范围编码、token 计数器和内容 hash 规范。
- PENDING：Chunk 保留/清理与历史 Citation 关系。

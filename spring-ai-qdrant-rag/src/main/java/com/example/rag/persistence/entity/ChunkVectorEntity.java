package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "chunk_vector", autoResultMap = true)
public class ChunkVectorEntity {
    @TableField("chunk_id")
    private UUID chunkId;
    @TableField("embedding_profile_id")
    private UUID embeddingProfileId;
    @TableField("index_status")
    private String indexStatus;
    @TableField("last_indexed_at")
    private LocalDateTime lastIndexedAt;
    @TableField("last_error_code")
    private String lastErrorCode;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public UUID getEmbeddingProfileId() { return embeddingProfileId; }
    public void setEmbeddingProfileId(UUID embeddingProfileId) { this.embeddingProfileId = embeddingProfileId; }

    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }

    public LocalDateTime getLastIndexedAt() { return lastIndexedAt; }
    public void setLastIndexedAt(LocalDateTime lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

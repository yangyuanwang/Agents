package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "chunk", autoResultMap = true)
public class ChunkEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("document_version_id")
    private UUID documentVersionId;
    @TableField("chunk_index")
    private Integer chunkIndex;
    @TableField("content")
    private String content;
    @TableField(value = "content_sha256", typeHandler = com.example.rag.persistence.typehandler.Sha256BinaryTypeHandler.class)
    private byte[] contentSha256;
    @TableField("token_count")
    private Integer tokenCount;
    @TableField("metadata")
    private String metadata;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentVersionId() { return documentVersionId; }
    public void setDocumentVersionId(UUID documentVersionId) { this.documentVersionId = documentVersionId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public byte[] getContentSha256() { return contentSha256 == null ? null : contentSha256.clone(); }
    public void setContentSha256(byte[] contentSha256) { this.contentSha256 = contentSha256 == null ? null : contentSha256.clone(); }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

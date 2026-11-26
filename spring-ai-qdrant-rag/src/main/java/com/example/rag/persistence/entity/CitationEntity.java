package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "citation", autoResultMap = true)
public class CitationEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("message_id")
    private UUID messageId;
    @TableField("`rank`")
    private Integer rank;
    @TableField("document_id")
    private UUID documentId;
    @TableField("document_version_id")
    private UUID documentVersionId;
    @TableField("chunk_id")
    private UUID chunkId;
    @TableField("document_title_snapshot")
    private String documentTitleSnapshot;
    @TableField("excerpt_snapshot")
    private String excerptSnapshot;
    @TableField("source_locator")
    private String sourceLocator;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getDocumentVersionId() { return documentVersionId; }
    public void setDocumentVersionId(UUID documentVersionId) { this.documentVersionId = documentVersionId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public String getDocumentTitleSnapshot() { return documentTitleSnapshot; }
    public void setDocumentTitleSnapshot(String documentTitleSnapshot) { this.documentTitleSnapshot = documentTitleSnapshot; }

    public String getExcerptSnapshot() { return excerptSnapshot; }
    public void setExcerptSnapshot(String excerptSnapshot) { this.excerptSnapshot = excerptSnapshot; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

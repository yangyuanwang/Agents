package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "file_object", autoResultMap = true)
public class FileObjectEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("tenant_id")
    private UUID tenantId;
    @TableField("storage_key")
    private String storageKey;
    @TableField(value = "sha256", typeHandler = com.example.rag.persistence.typehandler.Sha256BinaryTypeHandler.class)
    private byte[] sha256;
    @TableField("byte_size")
    private Long byteSize;
    @TableField("media_type")
    private String mediaType;
    @TableField("storage_status")
    private String storageStatus;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableField("delete_requested_at")
    private LocalDateTime deleteRequestedAt;
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public byte[] getSha256() { return sha256 == null ? null : sha256.clone(); }
    public void setSha256(byte[] sha256) { this.sha256 = sha256 == null ? null : sha256.clone(); }

    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getStorageStatus() { return storageStatus; }
    public void setStorageStatus(String storageStatus) { this.storageStatus = storageStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeleteRequestedAt() { return deleteRequestedAt; }
    public void setDeleteRequestedAt(LocalDateTime deleteRequestedAt) { this.deleteRequestedAt = deleteRequestedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

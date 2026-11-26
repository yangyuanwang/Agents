package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "document_version", autoResultMap = true)
public class DocumentVersionEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("document_id")
    private UUID documentId;
    @TableField("version_no")
    private Integer versionNo;
    @TableField("tenant_id")
    private UUID tenantId;
    @TableField("source_file_id")
    private UUID sourceFileId;
    @TableField(value = "source_sha256", typeHandler = com.example.rag.persistence.typehandler.Sha256BinaryTypeHandler.class)
    private byte[] sourceSha256;
    @TableField("media_type")
    private String mediaType;
    @TableField("byte_size")
    private Long byteSize;
    @TableField("original_filename")
    private String originalFilename;
    @TableField("status")
    private String status;
    @TableField("processing_config")
    private String processingConfig;
    @TableField("error_code")
    private String errorCode;
    @TableField("error_message")
    private String errorMessage;
    @TableField("created_by")
    private UUID createdBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("activated_at")
    private LocalDateTime activatedAt;
    @TableField("retired_at")
    private LocalDateTime retiredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(UUID sourceFileId) { this.sourceFileId = sourceFileId; }

    public byte[] getSourceSha256() { return sourceSha256 == null ? null : sourceSha256.clone(); }
    public void setSourceSha256(byte[] sourceSha256) { this.sourceSha256 = sourceSha256 == null ? null : sourceSha256.clone(); }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProcessingConfig() { return processingConfig; }
    public void setProcessingConfig(String processingConfig) { this.processingConfig = processingConfig; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public LocalDateTime getRetiredAt() { return retiredAt; }
    public void setRetiredAt(LocalDateTime retiredAt) { this.retiredAt = retiredAt; }
}

package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "embedding_profile", autoResultMap = true)
public class EmbeddingProfileEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("scope")
    private String scope;
    @TableField("owner_tenant_id")
    private UUID ownerTenantId;
    @TableField("provider")
    private String provider;
    @TableField("model_name")
    private String modelName;
    @TableField("model_revision")
    private String modelRevision;
    @TableField("dimensions")
    private Integer dimensions;
    @TableField("distance_metric")
    private String distanceMetric;
    @TableField("collection_name")
    private String collectionName;
    @TableField("secret_ref")
    private String secretRef;
    @TableField("config_snapshot")
    private String configSnapshot;
    @TableField("status")
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public UUID getOwnerTenantId() { return ownerTenantId; }
    public void setOwnerTenantId(UUID ownerTenantId) { this.ownerTenantId = ownerTenantId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelRevision() { return modelRevision; }
    public void setModelRevision(String modelRevision) { this.modelRevision = modelRevision; }

    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }

    public String getDistanceMetric() { return distanceMetric; }
    public void setDistanceMetric(String distanceMetric) { this.distanceMetric = distanceMetric; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }

    public String getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(String configSnapshot) { this.configSnapshot = configSnapshot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

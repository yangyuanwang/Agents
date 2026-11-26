package com.example.rag.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@TableName(value = "tenant_quota", autoResultMap = true)
public class TenantQuotaEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("tenant_id")
    private UUID tenantId;
    @TableField("quota_key")
    private String quotaKey;
    @TableField("limit_value")
    private Long limitValue;
    @TableField("used_value")
    private Long usedValue;
    @TableField("period_start")
    private LocalDateTime periodStart;
    @TableField("period_end")
    private LocalDateTime periodEnd;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getQuotaKey() { return quotaKey; }
    public void setQuotaKey(String quotaKey) { this.quotaKey = quotaKey; }

    public Long getLimitValue() { return limitValue; }
    public void setLimitValue(Long limitValue) { this.limitValue = limitValue; }

    public Long getUsedValue() { return usedValue; }
    public void setUsedValue(Long usedValue) { this.usedValue = usedValue; }

    public LocalDateTime getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDateTime periodStart) { this.periodStart = periodStart; }

    public LocalDateTime getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDateTime periodEnd) { this.periodEnd = periodEnd; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

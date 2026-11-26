package com.example.rag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileObjectResponse(UUID id, UUID tenantId, String sha256, long byteSize,
        String mediaType, String storageStatus, LocalDateTime createdAt) { }

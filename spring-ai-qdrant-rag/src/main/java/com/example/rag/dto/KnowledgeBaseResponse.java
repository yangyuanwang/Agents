package com.example.rag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record KnowledgeBaseResponse(UUID id, UUID tenantId, String name, String description,
        String status, UUID createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}

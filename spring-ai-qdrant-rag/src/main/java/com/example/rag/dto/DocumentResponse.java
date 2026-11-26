package com.example.rag.dto;
import java.time.LocalDateTime;
import java.util.UUID;
public record DocumentResponse(UUID id,UUID tenantId,UUID knowledgeBaseId,String displayName,String status,
        UUID activeVersionId,long versionCount,DocumentVersionResponse activeVersion,LocalDateTime createdAt,LocalDateTime updatedAt) { }

package com.example.rag.dto;
import java.time.LocalDateTime;
import java.util.UUID;
public record DocumentVersionResponse(UUID id,UUID documentId,int versionNo,UUID fileObjectId,String sha256,
        String mediaType,long byteSize,String originalFilename,String status,LocalDateTime createdAt,LocalDateTime activatedAt) { }

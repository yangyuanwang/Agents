package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** tenantId/createdBy are temporary development identity inputs; not authorization claims. */
public record KnowledgeBaseCreateRequest(@NotNull UUID tenantId, @NotNull UUID createdBy,
        @NotBlank @Size(max=160) String name, @Size(max=1000) String description) {}

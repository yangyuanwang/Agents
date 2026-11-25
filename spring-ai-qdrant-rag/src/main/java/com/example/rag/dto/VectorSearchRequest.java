package com.example.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VectorSearchRequest(@NotBlank String query, @NotBlank String tenantId, @Min(1) @Max(100) int topK) {}

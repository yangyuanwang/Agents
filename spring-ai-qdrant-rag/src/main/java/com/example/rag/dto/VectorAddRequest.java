package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record VectorAddRequest(@NotBlank String id, @NotBlank String content, @NotBlank String tenantId, @NotBlank String category) {}

package com.example.rag.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record DocumentCreateRequest(@NotNull UUID knowledgeBaseId,@NotBlank @Size(max=255) String displayName) { }

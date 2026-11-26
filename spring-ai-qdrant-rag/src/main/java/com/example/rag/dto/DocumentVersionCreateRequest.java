package com.example.rag.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record DocumentVersionCreateRequest(@NotNull UUID fileObjectId,@Size(max=255) String originalFilename) { }

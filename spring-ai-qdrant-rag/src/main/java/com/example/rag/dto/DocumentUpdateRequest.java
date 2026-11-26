package com.example.rag.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record DocumentUpdateRequest(@NotBlank @Size(max=255) String displayName) { }

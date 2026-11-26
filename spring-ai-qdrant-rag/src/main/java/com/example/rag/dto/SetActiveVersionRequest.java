package com.example.rag.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record SetActiveVersionRequest(@NotNull UUID versionId) { }

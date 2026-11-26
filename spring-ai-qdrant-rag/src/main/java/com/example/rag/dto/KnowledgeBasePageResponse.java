package com.example.rag.dto;

import java.util.List;

public record KnowledgeBasePageResponse(List<KnowledgeBaseResponse> items, long total, long page, long size) {}

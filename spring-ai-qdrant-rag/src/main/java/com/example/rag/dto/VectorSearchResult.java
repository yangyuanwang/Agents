package com.example.rag.dto;

import java.util.Map;

public record VectorSearchResult(String id, String content, double score, Map<String, Object> metadata) {}

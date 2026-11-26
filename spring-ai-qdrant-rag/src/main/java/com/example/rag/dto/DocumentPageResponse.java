package com.example.rag.dto;
import java.util.List;
public record DocumentPageResponse(List<DocumentResponse> items,long total,long page,long size) { }

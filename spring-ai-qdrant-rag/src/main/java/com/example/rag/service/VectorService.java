package com.example.rag.service;

import com.example.rag.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class VectorService {
    private static final Logger log = LoggerFactory.getLogger(VectorService.class);
    private final VectorStore vectorStore;
    public VectorService(VectorStore vectorStore) { this.vectorStore = vectorStore; }

    public void add(VectorAddRequest request) {
        // tenantId、category 与 content 一起写入 Qdrant Payload，供检索过滤与返回。
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", request.tenantId()); metadata.put("category", request.category());
        Document document = Document.builder().id(request.id()).text(request.content()).metadata(metadata).build();
        vectorStore.add(List.of(document));
        log.info("向量写入成功 id={}, tenantId={}, category={}", request.id(), request.tenantId(), request.category());
    }

    public List<VectorSearchResult> search(VectorSearchRequest request) {
        // FilterExpression 使用 Spring AI 官方过滤 DSL，租户条件在 Qdrant 端执行。
        SearchRequest search = SearchRequest.builder().query(request.query()).topK(request.topK())
                .filterExpression("tenantId == '" + escape(request.tenantId()) + "'").build();
        return vectorStore.similaritySearch(search).stream()
                .map(doc -> new VectorSearchResult(doc.getId(), doc.getText(), doc.getScore() == null ? 0 : doc.getScore(), doc.getMetadata())).toList();
    }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("'", "\\'"); }
}

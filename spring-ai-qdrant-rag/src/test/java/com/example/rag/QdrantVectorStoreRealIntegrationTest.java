package com.example.rag;

import com.example.rag.dto.VectorAddRequest;
import com.example.rag.dto.VectorSearchRequest;
import com.example.rag.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Calls the configured remote Qdrant and embedding provider; deliberately uses no mock. */
@SpringBootTest(properties="spring.ai.openai.api-key=test-placeholder")
class QdrantVectorStoreRealIntegrationTest {
 @Autowired VectorService service;
 @Autowired VectorStore vectorStore;
 @Test void realQdrantAddAndTenantFilteredSearch(){
  String id=UUID.randomUUID().toString(),tenant="qdrant-regression-"+UUID.randomUUID(),text="unique qdrant regression payload "+UUID.randomUUID();
  try {
   service.add(new VectorAddRequest(id,text,tenant,"integration-test"));
   var found=service.search(new VectorSearchRequest(text,tenant,10)).stream().filter(x->x.id().equals(id)).findFirst();
   assertTrue(found.isPresent(),"newly written point must be returned by Qdrant search");
   assertEquals(text,found.get().content());assertEquals(tenant,found.get().metadata().get("tenantId"));assertEquals("integration-test",found.get().metadata().get("category"));
  } finally { vectorStore.delete(java.util.List.of(id)); }
 }
}

package com.example.rag;

import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder"})
@AutoConfigureMockMvc
class KnowledgeBaseApiIntegrationTest {
 @MockBean VectorStore vectorStore;
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired TenantMapper tenants; @Autowired UserMapper users;
 @Autowired KnowledgeBaseMapper kbs; @Autowired PlatformTransactionManager tx;
 UUID tenantId,userId,kbId; String marker;
 @BeforeEach void setup(){marker="kb-api-test-"+UUID.randomUUID();tenantId=UUID.randomUUID();userId=UUID.randomUUID();
  LocalDateTime n=LocalDateTime.now(); UserEntity u=new UserEntity();u.setId(userId);u.setUsername(marker);u.setEmail(marker+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(marker);u.setStatus("ACTIVE");u.setCreatedAt(n);u.setUpdatedAt(n);users.insert(u);
  TenantEntity t=new TenantEntity();t.setId(tenantId);t.setName(marker);t.setStatus("ACTIVE");t.setCreatedAt(n);t.setUpdatedAt(n);tenants.insert(t);
 }
 @AfterEach void cleanup(){jdbc.update("DELETE FROM knowledge_base WHERE tenant_id=?",bytes(tenantId));jdbc.update("DELETE FROM tenant WHERE id=?",bytes(tenantId));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(userId));}
 @Test void crudSoftDeleteValidationNotFoundAndRollback() throws Exception {
  String body="{\"tenantId\":\""+tenantId+"\",\"createdBy\":\""+userId+"\",\"name\":\""+marker+"\",\"description\":\"first\"}";
  String result=mvc.perform(post("/api/knowledge-bases").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  kbId=UUID.fromString(result.split("\"id\":\"")[1].split("\"")[0]);
  mvc.perform(get("/api/knowledge-bases").param("tenantId",tenantId.toString()).param("page","1").param("size","10")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/knowledge-bases/"+kbId)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(marker));
  mvc.perform(put("/api/knowledge-bases/"+kbId).contentType("application/json").content("{\"name\":\""+marker+"-updated\",\"description\":\"changed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("changed"));
  mvc.perform(get("/api/knowledge-bases/not-a-uuid")).andExpect(status().isBadRequest());
  mvc.perform(put("/api/knowledge-bases/"+UUID.randomUUID()).contentType("application/json").content("{\"name\":\"missing\"}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/knowledge-bases").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/knowledge-bases/"+kbId)).andExpect(status().isNoContent());
  mvc.perform(delete("/api/knowledge-bases/"+kbId)).andExpect(status().isNotFound());
  mvc.perform(get("/api/knowledge-bases/"+kbId)).andExpect(status().isNotFound());
  mvc.perform(put("/api/knowledge-bases/"+kbId).contentType("application/json").content("{\"name\":\"again\"}")).andExpect(status().isNotFound());
  mvc.perform(get("/api/knowledge-bases").param("tenantId",tenantId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("SELECT is_deleted FROM knowledge_base WHERE id=?",Integer.class,bytes(kbId)));
  org.junit.jupiter.api.Assertions.assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM knowledge_base WHERE id=?",java.sql.Timestamp.class,bytes(kbId)));
  org.junit.jupiter.api.Assertions.assertNotNull(jdbc.queryForObject("SELECT updated_at FROM knowledge_base WHERE id=?",java.sql.Timestamp.class,bytes(kbId)));
  UUID rollback=UUID.randomUUID();org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->new TransactionTemplate(tx).execute(s->{KnowledgeBaseEntity e=new KnowledgeBaseEntity();e.setId(rollback);e.setTenantId(tenantId);e.setCreatedBy(userId);e.setName(marker+"-rollback");e.setStatus("ACTIVE");e.setCreatedAt(LocalDateTime.now());e.setUpdatedAt(e.getCreatedAt());kbs.insert(e);throw new IllegalStateException();}));
  org.junit.jupiter.api.Assertions.assertNull(kbs.selectById(rollback));
 }
 static byte[] bytes(UUID id){java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(16);b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());return b.array();}
}

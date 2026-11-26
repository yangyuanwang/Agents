package com.example.rag;

import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder"})
@AutoConfigureMockMvc
class KnowledgeBaseAssociationRetentionIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired TenantMapper tenants;
 @org.springframework.boot.test.mock.mockito.MockBean org.springframework.ai.vectorstore.VectorStore vectorStore;
 @Autowired TenantMemberMapper tenantMembers; @Autowired KnowledgeBaseMapper kbs; @Autowired KnowledgeBaseMemberMapper kbMembers;
 @Autowired DocumentMapper documents;
 UUID tenant,user,kb,document,member; String tag;
 @BeforeEach void createRows(){tag="kb-retain-"+UUID.randomUUID();tenant=UUID.randomUUID();user=UUID.randomUUID();kb=UUID.randomUUID();document=UUID.randomUUID();member=UUID.randomUUID();LocalDateTime n=LocalDateTime.now();
  UserEntity u=new UserEntity();u.setId(user);u.setUsername(tag);u.setEmail(tag+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(tag);u.setStatus("ACTIVE");u.setCreatedAt(n);u.setUpdatedAt(n);users.insert(u);
  TenantEntity t=new TenantEntity();t.setId(tenant);t.setName(tag);t.setStatus("ACTIVE");t.setCreatedAt(n);t.setUpdatedAt(n);tenants.insert(t);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,user_id,role,status,joined_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",bytes(UUID.randomUUID()),bytes(tenant),bytes(user),"OWNER","ACTIVE",n,n,n);
  KnowledgeBaseEntity e=new KnowledgeBaseEntity();e.setId(kb);e.setTenantId(tenant);e.setName(tag);e.setStatus("ACTIVE");e.setCreatedBy(user);e.setCreatedAt(n);e.setUpdatedAt(n);kbs.insert(e);
  KnowledgeBaseMemberEntity m=new KnowledgeBaseMemberEntity();m.setId(member);m.setTenantId(tenant);m.setKnowledgeBaseId(kb);m.setUserId(user);m.setRole("ADMIN");m.setStatus("ACTIVE");m.setCreatedBy(user);m.setCreatedAt(n);m.setUpdatedAt(n);kbMembers.insert(m);
  DocumentEntity d=new DocumentEntity();d.setId(document);d.setTenantId(tenant);d.setKnowledgeBaseId(kb);d.setDisplayName(tag+".txt");d.setStatus("READY");d.setCreatedBy(user);d.setCreatedAt(n);d.setUpdatedAt(n);documents.insert(d);
 }
 @AfterEach void cleanup(){jdbc.update("DELETE FROM document WHERE id=?",bytes(document));jdbc.update("DELETE FROM knowledge_base_member WHERE id=?",bytes(member));jdbc.update("DELETE FROM knowledge_base WHERE id=?",bytes(kb));jdbc.update("DELETE FROM tenant_member WHERE tenant_id=? AND user_id=?",bytes(tenant),bytes(user));jdbc.update("DELETE FROM tenant WHERE id=?",bytes(tenant));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(user));}
 @Test void logicalDeleteRetainsForeignKeyDependents() throws Exception {
  mvc.perform(delete("/api/knowledge-bases/"+kb)).andExpect(status().isNoContent());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_base WHERE id=? AND is_deleted=1 AND deleted_at IS NOT NULL",Integer.class,bytes(kb)));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_base_member WHERE id=?",Integer.class,bytes(member)));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE id=?",Integer.class,bytes(document)));
  mvc.perform(get("/api/knowledge-bases/"+kb)).andExpect(status().isNotFound());
 }
 static byte[] bytes(UUID id){var b=java.nio.ByteBuffer.allocate(16);b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());return b.array();}
}

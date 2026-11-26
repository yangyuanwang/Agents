package com.example.rag;

import com.example.rag.config.FileStorageProperties;
import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder","app.file.storage-root=target/fileobject-compensation-storage","app.file.max-size-bytes=1048576","app.file.allowed-extensions=txt"})
@AutoConfigureMockMvc
class FileObjectDatabaseFailureCompensationIntegrationTest {
 @MockBean VectorStore vectorStore;
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired TenantMapper tenants;
 @Autowired KnowledgeBaseMapper kbs; @Autowired SqlSession sqlSession; @Autowired FileStorageProperties properties;
 @SpyBean FileObjectMapper fileMapper;
 UUID user,tenant,kb; String marker;
 @BeforeEach void createFixture(){marker="file-compensate-"+UUID.randomUUID();user=UUID.randomUUID();tenant=UUID.randomUUID();kb=UUID.randomUUID();LocalDateTime now=LocalDateTime.now();
  UserEntity u=new UserEntity();u.setId(user);u.setUsername(marker);u.setEmail(marker+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(marker);u.setStatus("ACTIVE");u.setCreatedAt(now);u.setUpdatedAt(now);users.insert(u);
  TenantEntity t=new TenantEntity();t.setId(tenant);t.setName(marker);t.setStatus("ACTIVE");t.setCreatedAt(now);t.setUpdatedAt(now);tenants.insert(t);
  KnowledgeBaseEntity k=new KnowledgeBaseEntity();k.setId(kb);k.setTenantId(tenant);k.setName(marker);k.setStatus("ACTIVE");k.setCreatedBy(user);k.setCreatedAt(now);k.setUpdatedAt(now);kbs.insert(k);
 }
 @AfterEach void cleanup() throws Exception {jdbc.update("DELETE FROM file_object WHERE tenant_id=?",bytes(tenant));jdbc.update("DELETE FROM knowledge_base WHERE id=?",bytes(kb));jdbc.update("DELETE FROM tenant WHERE id=?",bytes(tenant));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(user));Path root=properties.getStorageRoot().toAbsolutePath().normalize();if(Files.exists(root))try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}

 @Test void publishedFileAndRealInsertAreRolledBackThenBothCopiesAreCompensated() throws Exception {
  // Seed an unrelated existing object through the real MySQL mapper and retain it as a safety sentinel.
  byte[] keepBytes="unrelated existing file".getBytes();var keep=mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","keep.txt","text/plain",keepBytes)).param("knowledgeBaseId",kb.toString())).andExpect(status().isCreated()).andReturn();
  UUID keepId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(keep.getResponse().getContentAsString()).get("id").asText());
  String keepKey=jdbc.queryForObject("SELECT storage_key FROM file_object WHERE id=?",String.class,bytes(keepId));Path keepPath=properties.getStorageRoot().toAbsolutePath().normalize().resolve(keepKey);

  AtomicBoolean inject=new AtomicBoolean(false);AtomicReference<FileObjectEntity> attempted=new AtomicReference<>();AtomicBoolean publishedAtInsert=new AtomicBoolean(false);
  doAnswer(invocation->{
   FileObjectEntity entity=invocation.getArgument(0);
   if(!inject.get())return sqlSession.getMapper(FileObjectMapper.class).insert(entity);
   attempted.set(entity);
   Path finalPath=properties.getStorageRoot().toAbsolutePath().normalize().resolve(entity.getStorageKey()).normalize();
   assertTrue(Files.isRegularFile(finalPath),"publication must have completed before DB insert");
   publishedAtInsert.set(true);
   int inserted=sqlSession.getMapper(FileObjectMapper.class).insert(entity); // actual MyBatis/MySQL insert, inside service transaction
   assertEquals(1,inserted);
   throw new DataAccessResourceFailureException("test injected failure after real INSERT, before transaction commit");
  }).when(fileMapper).insert(any(FileObjectEntity.class));
  inject.set(true);

  mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","fail.txt","text/plain","new file to roll back".getBytes())).param("knowledgeBaseId",kb.toString())).andExpect(status().isInternalServerError());

  assertTrue(publishedAtInsert.get());FileObjectEntity failed=attempted.get();assertNotNull(failed);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE id=?",Integer.class,bytes(failed.getId())),"real insert must be rolled back");
  Path failedFinal=properties.getStorageRoot().toAbsolutePath().normalize().resolve(failed.getStorageKey()).normalize();assertFalse(Files.exists(failedFinal),"published file must be compensated");
  Path staging=properties.getStorageRoot().toAbsolutePath().normalize().resolve(".staging");
  try(var files=Files.list(staging)){assertEquals(0,files.count(),"staging copy must be removed");}
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE id=? AND storage_status='AVAILABLE'",Integer.class,bytes(keepId)));
  assertArrayEquals(keepBytes,Files.readAllBytes(keepPath),"unrelated existing file must remain intact");
 }
 private static String bytes(UUID id){return id.toString();}
}

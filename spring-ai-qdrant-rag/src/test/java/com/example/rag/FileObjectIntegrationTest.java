package com.example.rag;

import com.example.rag.config.FileStorageProperties;
import com.example.rag.dto.FileObjectResponse;
import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import com.example.rag.service.FileObjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder","app.file.storage-root=target/fileobject-integration-storage","app.file.max-size-bytes=1024","app.file.allowed-extensions=txt,md,pdf,docx","app.file.cleanup.retry-interval-ms=1000","app.file.cleanup.batch-size=100"})
@AutoConfigureMockMvc
class FileObjectIntegrationTest {
 @MockBean VectorStore vectorStore;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired TenantMapper tenants;
 @Autowired KnowledgeBaseMapper kbs; @Autowired DocumentMapper documents; @Autowired DocumentVersionMapper versions;
 @Autowired FileObjectMapper files; @Autowired FileObjectService service; @Autowired FileStorageProperties props;
 UUID user,tenant,kb; String tag;
 @BeforeEach void fixture(){tag="file-it-"+UUID.randomUUID();user=UUID.randomUUID();tenant=UUID.randomUUID();kb=UUID.randomUUID();LocalDateTime n=LocalDateTime.now();
  UserEntity u=new UserEntity();u.setId(user);u.setUsername(tag);u.setEmail(tag+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(tag);u.setStatus("ACTIVE");u.setCreatedAt(n);u.setUpdatedAt(n);users.insert(u);
  TenantEntity t=new TenantEntity();t.setId(tenant);t.setName(tag);t.setStatus("ACTIVE");t.setCreatedAt(n);t.setUpdatedAt(n);tenants.insert(t);
  KnowledgeBaseEntity k=new KnowledgeBaseEntity();k.setId(kb);k.setTenantId(tenant);k.setName(tag);k.setStatus("ACTIVE");k.setCreatedBy(user);k.setCreatedAt(n);k.setUpdatedAt(n);kbs.insert(k);
 }
 @AfterEach void clean() throws Exception {jdbc.update("DELETE FROM document_version WHERE tenant_id=? AND created_by=?",bytes(tenant),bytes(user));jdbc.update("DELETE FROM document WHERE tenant_id=? AND created_by=?",bytes(tenant),bytes(user));jdbc.update("DELETE FROM file_object WHERE tenant_id=?",bytes(tenant));jdbc.update("DELETE FROM knowledge_base WHERE id=?",bytes(kb));jdbc.update("DELETE FROM tenant WHERE id=?",bytes(tenant));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(user));Path root=props.getStorageRoot().toAbsolutePath().normalize();if(Files.exists(root))try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}

 @Test void uploadValidationDedupIsolationAndScopedMetadata() throws Exception {
  byte[] body="sha256 deterministic file payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
  var first=mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","a.txt","text/plain",body)).param("knowledgeBaseId",kb.toString())).andExpect(status().isCreated()).andReturn();
  var firstJson=json.readTree(first.getResponse().getContentAsString());UUID fileId=UUID.fromString(firstJson.get("id").asText());assertEquals(expected,firstJson.get("sha256").asText());assertEquals(body.length,firstJson.get("byteSize").asLong());
  String storageKey=jdbc.queryForObject("SELECT storage_key FROM file_object WHERE id=?",String.class,bytes(fileId));Path stored=props.getStorageRoot().toAbsolutePath().normalize().resolve(storageKey).normalize();assertTrue(stored.startsWith(props.getStorageRoot().toAbsolutePath().normalize()));assertArrayEquals(body,Files.readAllBytes(stored));
  var duplicate=mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","renamed.txt","text/plain",body)).param("knowledgeBaseId",kb.toString())).andExpect(status().isCreated()).andReturn();
  assertEquals(fileId.toString(),json.readTree(duplicate.getResponse().getContentAsString()).get("id").asText());
  mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","empty.txt","text/plain",new byte[0])).param("knowledgeBaseId",kb.toString())).andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","large.txt","text/plain",new byte[1025])).param("knowledgeBaseId",kb.toString())).andExpect(status().isPayloadTooLarge());
  mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","bad.exe","application/octet-stream",body)).param("knowledgeBaseId",kb.toString())).andExpect(status().isUnsupportedMediaType());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE tenant_id=?",Integer.class,bytes(tenant)));
  assertEquals(32,jdbc.queryForObject("SELECT OCTET_LENGTH(sha256) FROM file_object WHERE id=?",Integer.class,bytes(fileId)));
  // A new tenant with the same bytes receives an independent FileObject.
  UUID otherUser=UUID.randomUUID(),otherTenant=UUID.randomUUID(),otherKb=UUID.randomUUID();createTenantKb(otherUser,otherTenant,otherKb,"cross-"+tag);
  try {var cross=mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","same.txt","text/plain",body)).param("knowledgeBaseId",otherKb.toString())).andExpect(status().isCreated()).andReturn();assertNotEquals(fileId.toString(),json.readTree(cross.getResponse().getContentAsString()).get("id").asText());mvc.perform(get("/api/files/"+fileId).param("knowledgeBaseId",otherKb.toString())).andExpect(status().isNotFound());}
  finally {jdbc.update("DELETE FROM file_object WHERE tenant_id=?",bytes(otherTenant));jdbc.update("DELETE FROM knowledge_base WHERE id=?",bytes(otherKb));jdbc.update("DELETE FROM tenant WHERE id=?",bytes(otherTenant));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(otherUser));}
  // Files are visible through a KB only after an actual DocumentVersion reference exists.
  mvc.perform(get("/api/files/"+fileId).param("knowledgeBaseId",kb.toString())).andExpect(status().isNotFound());
  UUID doc=UUID.randomUUID(),version=UUID.randomUUID();LocalDateTime now=LocalDateTime.now();DocumentEntity d=new DocumentEntity();d.setId(doc);d.setTenantId(tenant);d.setKnowledgeBaseId(kb);d.setDisplayName(tag);d.setStatus("UPLOADED");d.setCreatedBy(user);d.setCreatedAt(now);d.setUpdatedAt(now);documents.insert(d);
  FileObjectEntity f=files.selectById(fileId);DocumentVersionEntity v=new DocumentVersionEntity();v.setId(version);v.setDocumentId(doc);v.setVersionNo(1);v.setTenantId(tenant);v.setSourceFileId(fileId);v.setSourceSha256(f.getSha256());v.setMediaType(f.getMediaType());v.setByteSize(f.getByteSize());v.setStatus("UPLOADED");v.setCreatedBy(user);v.setCreatedAt(now);versions.insert(v);
  mvc.perform(get("/api/files/"+fileId).param("knowledgeBaseId",kb.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenant.toString()));
  assertEquals(0,service.claimForDeletion(fileId),"a referenced file must not be claimed");
 }

 @Test void concurrentDuplicateUploadHasOneRowAndClaimPreventsReuse() throws Exception {
  byte[] body="concurrent payload".getBytes();int workers=8;ExecutorService pool=Executors.newFixedThreadPool(workers);CountDownLatch start=new CountDownLatch(1);
  try {List<Future<FileObjectResponse>> results=new ArrayList<>();for(int i=0;i<workers;i++)results.add(pool.submit(()->{start.await();return service.upload(kb,new MockMultipartFile("file","same.md","text/markdown",body));}));start.countDown();Set<UUID> ids=new HashSet<>();for(Future<FileObjectResponse> f:results)ids.add(f.get(30,TimeUnit.SECONDS).id());assertEquals(1,ids.size());UUID id=ids.iterator().next();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE tenant_id=?",Integer.class,bytes(tenant)));
   assertEquals(1,service.claimForDeletion(id));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.upload(kb,new MockMultipartFile("file","same.md","text/markdown",body)));
   assertTrue(service.cleanupClaimed(id));assertEquals("DELETED",jdbc.queryForObject("SELECT storage_status FROM file_object WHERE id=?",String.class,bytes(id)));
   assertEquals(0,service.claimForDeletion(id));
  } finally {pool.shutdownNow();}
 }

 @Test void deletingRetryIsIdempotentAndFailedPhysicalDeleteStaysDeleting() throws Exception {
  FileObjectResponse response=service.upload(kb,new MockMultipartFile("file","claim.txt","text/plain","claim me".getBytes()));UUID id=response.id();FileObjectEntity f=files.selectById(id);
  // Make the claimed storage key a non-empty directory: Files.deleteIfExists must fail without losing DELETING state.
  Path root=props.getStorageRoot().toAbsolutePath().normalize();Path blocked=root.resolve("blocked-dir");Files.createDirectories(blocked);Files.writeString(blocked.resolve("child"),"keep");
  jdbc.update("UPDATE file_object SET storage_key=?,storage_status='DELETING',updated_at=? WHERE id=?","blocked-dir",LocalDateTime.now().minusSeconds(3),bytes(id));
  assertFalse(service.cleanupClaimed(id));assertEquals("DELETING",jdbc.queryForObject("SELECT storage_status FROM file_object WHERE id=?",String.class,bytes(id)));
  Files.delete(blocked.resolve("child"));Files.delete(blocked);jdbc.update("UPDATE file_object SET updated_at=? WHERE id=?",LocalDateTime.now().minusSeconds(3),bytes(id));
  assertEquals(1,service.retryCleanupBatch());assertEquals("DELETED",jdbc.queryForObject("SELECT storage_status FROM file_object WHERE id=?",String.class,bytes(id)));
   assertFalse(Files.exists(blocked));
  // Missing file remains idempotent on retry/claim paths.
  assertFalse(service.cleanupClaimed(id));
 }
 private Path storagePath(String key){return props.getStorageRoot().toAbsolutePath().normalize().resolve(key).normalize();}
 private void createTenantKb(UUID uId,UUID tId,UUID kId,String name){LocalDateTime n=LocalDateTime.now();UserEntity u=new UserEntity();u.setId(uId);u.setUsername(name);u.setEmail(name+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(name);u.setStatus("ACTIVE");u.setCreatedAt(n);u.setUpdatedAt(n);users.insert(u);TenantEntity t=new TenantEntity();t.setId(tId);t.setName(name);t.setStatus("ACTIVE");t.setCreatedAt(n);t.setUpdatedAt(n);tenants.insert(t);KnowledgeBaseEntity k=new KnowledgeBaseEntity();k.setId(kId);k.setTenantId(tId);k.setName(name);k.setStatus("ACTIVE");k.setCreatedBy(uId);k.setCreatedAt(n);k.setUpdatedAt(n);kbs.insert(k);}
 private static String bytes(UUID id){return id.toString();}
}

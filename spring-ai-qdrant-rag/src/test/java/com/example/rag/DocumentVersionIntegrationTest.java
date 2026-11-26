package com.example.rag;

import com.example.rag.dto.DocumentVersionCreateRequest;
import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import com.example.rag.service.DocumentService;
import com.example.rag.service.FileObjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.ibatis.session.SqlSession;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder"})
@AutoConfigureMockMvc
class DocumentVersionIntegrationTest {
 @MockBean VectorStore vectorStore;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired TenantMapper tenants;
 @Autowired KnowledgeBaseMapper kbs; @Autowired DocumentMapper docs; @Autowired DocumentVersionMapper versions; @Autowired FileObjectMapper files;
 @Autowired DocumentService service; @Autowired FileObjectService fileService;
 @Autowired SqlSession sqlSession; @SpyBean DocumentVersionMapper versionMapper;
 UUID user,tenant,kb,otherUser,otherTenant,otherKb;String tag;List<UUID> documentIds=new ArrayList<>(),fileIds=new ArrayList<>();
 @BeforeEach void fixture(){tag="doc-version-it-"+UUID.randomUUID();user=UUID.randomUUID();tenant=UUID.randomUUID();kb=UUID.randomUUID();otherUser=UUID.randomUUID();otherTenant=UUID.randomUUID();otherKb=UUID.randomUUID();createUserTenantKb(user,tenant,kb,tag);createUserTenantKb(otherUser,otherTenant,otherKb,tag+"-other");}
 @AfterEach void cleanup(){for(UUID d:documentIds){jdbc.update("UPDATE document SET active_version_id=NULL WHERE id=?",bytes(d));jdbc.update("DELETE FROM document_version WHERE document_id=?",bytes(d));jdbc.update("DELETE FROM document WHERE id=?",bytes(d));}for(UUID f:fileIds)jdbc.update("DELETE FROM file_object WHERE id=?",bytes(f));jdbc.update("DELETE FROM knowledge_base WHERE tenant_id IN (?,?)",bytes(tenant),bytes(otherTenant));jdbc.update("DELETE FROM tenant WHERE id IN (?,?)",bytes(tenant),bytes(otherTenant));jdbc.update("DELETE FROM `user` WHERE id IN (?,?)",bytes(user),bytes(otherUser));}
 @Test void documentCrudVersionLifecycleClaimAndCrossTenantProtection() throws Exception {
  mvc.perform(post("/api/documents").contentType("application/json").content("{\"knowledgeBaseId\":\""+kb+"\",\"displayName\":\""+tag+" A\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.tenantId").value(tenant.toString()));
  UUID d1=docId(tag+" A"),d2=createDoc("B",kb),otherDoc=createDoc("Other",otherKb);documentIds.addAll(List.of(d1,d2,otherDoc));
  UUID deletedKb=UUID.randomUUID();KnowledgeBaseEntity dead=new KnowledgeBaseEntity();dead.setId(deletedKb);dead.setTenantId(tenant);dead.setName(tag+"-dead");dead.setStatus("ACTIVE");dead.setCreatedBy(user);dead.setCreatedAt(LocalDateTime.now());dead.setUpdatedAt(dead.getCreatedAt());kbs.insert(dead);jdbc.update("UPDATE knowledge_base SET is_deleted=1,deleted_at=NOW(3) WHERE id=?",bytes(deletedKb));
  mvc.perform(post("/api/documents").contentType("application/json").content("{\"knowledgeBaseId\":\""+deletedKb+"\",\"displayName\":\"fail\"}")).andExpect(status().isNotFound());
  mvc.perform(get("/api/documents").param("knowledgeBaseId",kb.toString()).param("page","1").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/documents/"+d1).param("knowledgeBaseId",kb.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.versionCount").value(0));
  mvc.perform(get("/api/documents/"+d1).param("knowledgeBaseId",otherKb.toString())).andExpect(status().isNotFound());
  mvc.perform(put("/api/documents/"+d1).param("knowledgeBaseId",kb.toString()).contentType("application/json").content("{\"displayName\":\"renamed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("renamed"));
  UUID f1=createFile(tenant,"f1"),f2=createFile(tenant,"f2"),foreign=createFile(otherTenant,"foreign"),deleting=createFile(tenant,"deleting"),deleted=createFile(tenant,"deleted");
  mvc.perform(post("/api/documents/"+UUID.randomUUID()+"/versions").contentType("application/json").content("{\"fileObjectId\":\""+f1+"\"}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/documents/"+d1+"/versions").contentType("application/json").content("{\"fileObjectId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isNotFound());
  jdbc.update("UPDATE file_object SET storage_status='DELETING' WHERE id=?",bytes(deleting));jdbc.update("UPDATE file_object SET storage_status='DELETED' WHERE id=?",bytes(deleted));
  UUID v1=createVersion(d1,f1,"one.txt"),v2=createVersion(d1,f2,"two.txt");
  assertEquals(1,versions.selectById(v1).getVersionNo());assertEquals(2,versions.selectById(v2).getVersionNo());
  assertEquals(tenant,versions.selectById(v1).getTenantId());assertArrayEquals(files.selectById(f1).getSha256(),versions.selectById(v1).getSourceSha256());
  assertNull(docs.selectById(d1).getActiveVersionId(),"unprocessed UPLOADED versions must not become active");
  mvc.perform(get("/api/documents/"+d1+"/versions")).andExpect(status().isOk()).andExpect(jsonPath("$[0].versionNo").value(2)).andExpect(jsonPath("$[0].storageKey").doesNotExist());
  mvc.perform(get("/api/documents/"+d1+"/versions/"+v1)).andExpect(status().isOk()).andExpect(jsonPath("$.fileObjectId").value(f1.toString()));
  mvc.perform(get("/api/documents/"+d2+"/versions/"+v1)).andExpect(status().isNotFound());
  mvc.perform(post("/api/documents/"+d1+"/versions").contentType("application/json").content("{\"fileObjectId\":\""+foreign+"\"}")).andExpect(status().isNotFound());
  for(UUID f:List.of(deleting,deleted))mvc.perform(post("/api/documents/"+d1+"/versions").contentType("application/json").content("{\"fileObjectId\":\""+f+"\"}")).andExpect(status().isConflict());
  assertEquals(0,fileService.claimForDeletion(f1),"DocumentVersion reference must prevent FileObject claim");
  mvc.perform(put("/api/documents/"+d1+"/active-version").contentType("application/json").content("{\"versionId\":\""+v1+"\"}")).andExpect(status().isConflict());
  jdbc.update("UPDATE document_version SET status='COMPLETED' WHERE id IN (?,?)",bytes(v1),bytes(v2));
  mvc.perform(put("/api/documents/"+d1+"/active-version").contentType("application/json").content("{\"versionId\":\""+v1+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.activeVersionId").value(v1.toString()));
  UUID otherFile=createFile(otherTenant,"other-doc-version-file"),otherVersion=createVersion(otherDoc,otherFile,"other.txt");
  mvc.perform(put("/api/documents/"+d1+"/active-version").contentType("application/json").content("{\"versionId\":\""+otherVersion+"\"}")).andExpect(status().isNotFound());
  mvc.perform(put("/api/documents/"+d1+"/active-version").contentType("application/json").content("{\"versionId\":\""+v2+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.activeVersionId").value(v2.toString()));
  assertEquals(0,fileService.claimForDeletion(f2));
  mvc.perform(delete("/api/documents/"+d1).param("knowledgeBaseId",kb.toString())).andExpect(status().isNoContent());
  mvc.perform(get("/api/documents/"+d1).param("knowledgeBaseId",kb.toString())).andExpect(status().isNotFound());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM document_version WHERE document_id=?",Integer.class,bytes(d1)));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE id=?",Integer.class,bytes(f1)));
 }

 @Test void concurrentVersionCreationSerializesNumbersAndFailureRollsBack(){
  UUID d=createDoc("parallel",kb);documentIds.add(d);UUID f1=createFile(tenant,"parallel1"),f2=createFile(tenant,"parallel2");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
  try {Future<?> a=pool.submit(()->{await(gate);return service.createVersion(d,new DocumentVersionCreateRequest(f1,"1.txt"));});Future<?> b=pool.submit(()->{await(gate);return service.createVersion(d,new DocumentVersionCreateRequest(f2,"2.txt"));});gate.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);List<DocumentVersionEntity> result=versions.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentVersionEntity>().eq(DocumentVersionEntity::getDocumentId,d));assertEquals(2,result.size());assertEquals(Set.of(1,2),Set.of(result.get(0).getVersionNo(),result.get(1).getVersionNo()));assertNull(docs.selectById(d).getActiveVersionId());}
  catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}
 }
 @Test void versionInsertFailureRollsBackAndPreservesCurrentVersion(){
  UUID d=createDoc("rollback",kb),f1=createFile(tenant,"rollback1"),f2=createFile(tenant,"rollback2");UUID current=createVersion(d,f1,"current.txt");
  jdbc.update("UPDATE document_version SET status='COMPLETED' WHERE id=?",bytes(current));service.activate(d,current);AtomicBoolean inject=new AtomicBoolean(false);
  Mockito.doAnswer(inv->{DocumentVersionEntity e=inv.getArgument(0);if(!inject.get())return sqlSession.getMapper(DocumentVersionMapper.class).insert(e);int result=sqlSession.getMapper(DocumentVersionMapper.class).insert(e);assertEquals(1,result);throw new DataAccessResourceFailureException("injected after real DocumentVersion INSERT");}).when(versionMapper).insert(ArgumentMatchers.any(DocumentVersionEntity.class));
  inject.set(true);assertThrows(DataAccessResourceFailureException.class,()->service.createVersion(d,new DocumentVersionCreateRequest(f2,"failed.txt")));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM document_version WHERE document_id=?",Integer.class,bytes(d)));
  assertEquals(current,docs.selectById(d).getActiveVersionId());assertEquals(1,fileService.claimForDeletion(f2),"failed version transaction must release its FileObject reference");
 }
 private UUID createDoc(String name,UUID knowledgeBase){try{var r=mvc.perform(post("/api/documents").contentType("application/json").content("{\"knowledgeBaseId\":\""+knowledgeBase+"\",\"displayName\":\""+name+"\"}")).andExpect(status().isCreated()).andReturn();UUID id=docId(name);documentIds.add(id);return id;}catch(Exception e){throw new RuntimeException(e);}}
 private UUID docId(String name){return docs.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentEntity>().eq(DocumentEntity::getDisplayName,name).orderByDesc(DocumentEntity::getCreatedAt).last("LIMIT 1")).getId();}
 private UUID createVersion(UUID d,UUID file,String original){try{var r=mvc.perform(post("/api/documents/"+d+"/versions").contentType("application/json").content("{\"fileObjectId\":\""+file+"\",\"originalFilename\":\""+original+"\"}")).andExpect(status().isCreated()).andReturn();return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).get("id").asText());}catch(Exception e){throw new RuntimeException(e);}}
 private UUID createFile(UUID ownerTenant,String key){FileObjectEntity f=new FileObjectEntity();f.setId(UUID.randomUUID());f.setTenantId(ownerTenant);f.setStorageKey(tag+"/"+key);f.setSha256(sha(key));f.setByteSize(10L);f.setMediaType("text/plain");f.setStorageStatus("AVAILABLE");f.setCreatedAt(LocalDateTime.now());f.setUpdatedAt(f.getCreatedAt());files.insert(f);fileIds.add(f.getId());return f.getId();}
 private void createUserTenantKb(UUID uId,UUID tId,UUID kId,String name){LocalDateTime n=LocalDateTime.now();UserEntity u=new UserEntity();u.setId(uId);u.setUsername(name);u.setEmail(name+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(name);u.setStatus("ACTIVE");u.setCreatedAt(n);u.setUpdatedAt(n);users.insert(u);TenantEntity t=new TenantEntity();t.setId(tId);t.setName(name);t.setStatus("ACTIVE");t.setCreatedAt(n);t.setUpdatedAt(n);tenants.insert(t);KnowledgeBaseEntity k=new KnowledgeBaseEntity();k.setId(kId);k.setTenantId(tId);k.setName(name);k.setStatus("ACTIVE");k.setCreatedBy(uId);k.setCreatedAt(n);k.setUpdatedAt(n);kbs.insert(k);}
 private static byte[] sha(String text){try{return java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes());}catch(Exception e){throw new RuntimeException(e);}}
 private static void await(CountDownLatch l){try{l.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
 private static String bytes(UUID id){return id.toString();}
}

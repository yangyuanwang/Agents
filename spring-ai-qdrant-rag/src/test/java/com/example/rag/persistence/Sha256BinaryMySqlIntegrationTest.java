package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.rag.persistence.entity.*;
import com.example.rag.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration","spring.ai.openai.api-key=test-placeholder"})
class Sha256BinaryMySqlIntegrationTest {
 @MockBean VectorStore vectorStore;
 @Autowired FileObjectMapper files; @Autowired DocumentVersionMapper versions; @Autowired DocumentMapper documents; @Autowired ChunkMapper chunks;
 @Autowired KnowledgeBaseMapper kbs; @Autowired TenantMapper tenants; @Autowired UserMapper users; @Autowired JdbcTemplate jdbc; @Autowired Flyway flyway;
 @Test void fullSha256Binary32RoundTripsAndQueriesWithoutCollisions(){
  assertEquals("3",flyway.info().current().getVersion().getVersion());
  UUID tenant=UUID.randomUUID(),user=UUID.randomUUID(),kb=UUID.randomUUID(),doc=UUID.randomUUID(),f1=UUID.randomUUID(),f2=UUID.randomUUID(),v1=UUID.randomUUID(),v2=UUID.randomUUID(),chunkId=UUID.randomUUID();
  String marker="sha256-test-"+UUID.randomUUID();LocalDateTime now=LocalDateTime.now();
  byte[] zero=new byte[32];byte[] high=new byte[32];for(int i=0;i<32;i++)high[i]=(byte)(0x80+i);
  try {
   UserEntity u=new UserEntity();u.setId(user);u.setUsername(marker);u.setEmail(marker+"@test.invalid");u.setPasswordHash("test");u.setDisplayName(marker);u.setStatus("ACTIVE");u.setCreatedAt(now);u.setUpdatedAt(now);users.insert(u);
   TenantEntity t=new TenantEntity();t.setId(tenant);t.setName(marker);t.setStatus("ACTIVE");t.setCreatedAt(now);t.setUpdatedAt(now);tenants.insert(t);
   KnowledgeBaseEntity k=new KnowledgeBaseEntity();k.setId(kb);k.setTenantId(tenant);k.setName(marker);k.setStatus("ACTIVE");k.setCreatedBy(user);k.setCreatedAt(now);k.setUpdatedAt(now);kbs.insert(k);
   DocumentEntity d=new DocumentEntity();d.setId(doc);d.setTenantId(tenant);d.setKnowledgeBaseId(kb);d.setDisplayName(marker);d.setStatus("UPLOADED");d.setCreatedBy(user);d.setCreatedAt(now);d.setUpdatedAt(now);documents.insert(d);
   insertFile(f1,tenant,marker+"/zero",zero,now);insertFile(f2,tenant,marker+"/high",high,now);
   DocumentVersionEntity a=version(v1,doc,tenant,f1,user,zero,1,now);versions.insert(a);
   DocumentVersionEntity b=version(v2,doc,tenant,f2,user,high,2,now);versions.insert(b);
   ChunkEntity chunk=new ChunkEntity();chunk.setId(chunkId);chunk.setDocumentVersionId(v1);chunk.setChunkIndex(0);chunk.setContent("hash mapping check");chunk.setContentSha256(high);chunk.setCreatedAt(now);chunks.insert(chunk);
   assertArrayEquals(zero,files.selectById(f1).getSha256());assertArrayEquals(high,files.selectById(f2).getSha256());
   assertArrayEquals(zero,versions.selectById(v1).getSourceSha256());assertArrayEquals(high,versions.selectById(v2).getSourceSha256());
   assertArrayEquals(high,chunks.selectById(chunkId).getContentSha256());
   assertEquals(1,files.selectCount(new QueryWrapper<FileObjectEntity>().eq("tenant_id",tenant).eq("sha256",high)));
   assertEquals(0,files.selectCount(new QueryWrapper<FileObjectEntity>().eq("tenant_id",tenant).eq("sha256",zero).eq("storage_key",marker+"/high")));
   assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT HEX(sha256)) FROM file_object WHERE tenant_id=? AND storage_key LIKE ?",Integer.class,bytes(tenant),marker+"/%"));
   assertEquals(32,jdbc.queryForObject("SELECT OCTET_LENGTH(sha256) FROM file_object WHERE id=?",Integer.class,bytes(f1)));
  } finally {
   jdbc.update("DELETE FROM chunk WHERE id=?",bytes(chunkId));jdbc.update("DELETE FROM document_version WHERE document_id=?",bytes(doc));jdbc.update("DELETE FROM document WHERE id=?",bytes(doc));
   jdbc.update("DELETE FROM file_object WHERE id IN (?,?)",bytes(f1),bytes(f2));jdbc.update("DELETE FROM knowledge_base WHERE id=?",bytes(kb));
   jdbc.update("DELETE FROM tenant WHERE id=?",bytes(tenant));jdbc.update("DELETE FROM `user` WHERE id=?",bytes(user));
  }
 }
 private void insertFile(UUID id,UUID tenant,String key,byte[] hash,LocalDateTime n){FileObjectEntity f=new FileObjectEntity();f.setId(id);f.setTenantId(tenant);f.setStorageKey(key);f.setSha256(hash);f.setByteSize(1L);f.setMediaType("text/plain");f.setStorageStatus("AVAILABLE");f.setCreatedAt(n);f.setUpdatedAt(n);assertEquals(1,files.insert(f));}
 private static DocumentVersionEntity version(UUID id,UUID doc,UUID tenant,UUID file,UUID user,byte[] hash,int version,LocalDateTime n){DocumentVersionEntity v=new DocumentVersionEntity();v.setId(id);v.setDocumentId(doc);v.setVersionNo(version);v.setTenantId(tenant);v.setSourceFileId(file);v.setSourceSha256(hash);v.setMediaType("text/plain");v.setByteSize(1L);v.setStatus("UPLOADED");v.setCreatedBy(user);v.setCreatedAt(n);return v;}
 private static String bytes(UUID id){return id.toString();}
}

package com.example.rag.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.entity.TenantEntity;
import com.example.rag.persistence.entity.UserEntity;
import com.example.rag.persistence.mapper.KnowledgeBaseMapper;
import com.example.rag.persistence.mapper.TenantMapper;
import com.example.rag.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
        "spring.ai.openai.api-key=test-placeholder"
})
class KnowledgeBaseMapperIntegrationTest {
    @MockBean private VectorStore vectorStore;
    @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void realMysqlBaseMapperCrudUuidAndRollback() {
        assertEquals("rag", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.0.29"));

        UUID tenantId = UUID.randomUUID(), userId = UUID.randomUUID(), kbId = UUID.randomUUID();
        String token = "mapper-accept-" + kbId;
        LocalDateTime now = LocalDateTime.now();
        try {
            UserEntity user = new UserEntity(); user.setId(userId); user.setUsername(token);
            user.setEmail(token + "@example.test"); user.setPasswordHash("test-only");
            user.setDisplayName("Mapper acceptance test"); user.setStatus("ACTIVE");
            user.setCreatedAt(now); user.setUpdatedAt(now);
            assertEquals(1, userMapper.insert(user));
            TenantEntity tenant = new TenantEntity(); tenant.setId(tenantId); tenant.setName(token);
            tenant.setStatus("ACTIVE"); tenant.setCreatedAt(now); tenant.setUpdatedAt(now);
            assertEquals(1, tenantMapper.insert(tenant));

            KnowledgeBaseEntity kb = new KnowledgeBaseEntity(); kb.setId(kbId); kb.setTenantId(tenantId);
            kb.setName(token); kb.setDescription("before"); kb.setStatus("ACTIVE"); kb.setCreatedBy(userId);
            kb.setCreatedAt(now); kb.setUpdatedAt(now);
            assertEquals(1, knowledgeBaseMapper.insert(kb));
            assertEquals(36, jdbc.queryForObject("SELECT CHAR_LENGTH(id) FROM knowledge_base WHERE id = ?", Integer.class, uuidBytes(kbId)));
            assertEquals(kbId, knowledgeBaseMapper.selectById(kbId).getId());
            assertEquals(1, knowledgeBaseMapper.selectCount(new QueryWrapper<KnowledgeBaseEntity>().eq("tenant_id", tenantId).eq("id", kbId)));
            kb.setDescription("after"); kb.setUpdatedAt(now.plusSeconds(1));
            assertEquals(1, knowledgeBaseMapper.updateById(kb));
            assertEquals("after", knowledgeBaseMapper.selectById(kbId).getDescription());
            assertEquals(1, knowledgeBaseMapper.deleteById(kbId));
            assertNull(knowledgeBaseMapper.selectById(kbId));

            UUID rollbackId = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(s -> {
                KnowledgeBaseEntity rollback = new KnowledgeBaseEntity(); rollback.setId(rollbackId); rollback.setTenantId(tenantId);
                rollback.setName(token + "-rollback"); rollback.setStatus("ACTIVE"); rollback.setCreatedBy(userId);
                rollback.setCreatedAt(now); rollback.setUpdatedAt(now); assertEquals(1, knowledgeBaseMapper.insert(rollback));
                throw new IllegalStateException("intentional rollback");
            }));
            assertNull(knowledgeBaseMapper.selectById(rollbackId));
        } finally {
            jdbc.update("DELETE FROM knowledge_base WHERE id = ?", uuidBytes(kbId));
            jdbc.update("DELETE FROM tenant WHERE id = ?", uuidBytes(tenantId));
            jdbc.update("DELETE FROM `user` WHERE id = ?", uuidBytes(userId));
        }
    }

    private static String uuidBytes(UUID id) { return id.toString(); }
}

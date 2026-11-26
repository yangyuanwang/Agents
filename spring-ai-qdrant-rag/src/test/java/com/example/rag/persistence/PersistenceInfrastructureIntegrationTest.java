package com.example.rag.persistence;

import com.example.rag.persistence.entity.UserEntity;
import com.example.rag.persistence.mapper.UserMapper;
import com.example.rag.persistence.mapper.CitationMapper;
import com.example.rag.persistence.mapper.ChunkVectorMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.rag.persistence.entity.CitationEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
                "spring.ai.openai.api-key=test-placeholder"
        })
class PersistenceInfrastructureIntegrationTest {
    @MockBean private VectorStore vectorStore;
    @Autowired private UserMapper userMapper;
    @Autowired private CitationMapper citationMapper;
    @Autowired private ChunkVectorMapper chunkVectorMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    @Test
    @Transactional
    void existingSchemaIsBaselinedAndUuidCrudRoundTrips() {
        assertEquals(17, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'",
                Integer.class));
        assertEquals("2", flyway.info().current().getVersion().getVersion(),
                "existing database baseline 1 should have logical-delete migration 2 applied");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'knowledge_base' AND column_name = 'is_deleted' AND column_default = '0'", Integer.class));

        UUID id = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("persistence-" + id);
        user.setEmail("persistence-" + id + "@example.test");
        user.setPasswordHash("test-hash");
        user.setDisplayName("Persistence Test");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        assertEquals(1, userMapper.insert(user));
        UserEntity loaded = userMapper.selectById(id);
        assertNotNull(loaded);
        assertEquals(id, loaded.getId());
        assertEquals(user.getUsername(), loaded.getUsername());

        loaded.setDisplayName("Updated Persistence Test");
        assertEquals(1, userMapper.updateById(loaded));
        assertEquals("Updated Persistence Test", userMapper.selectById(id).getDisplayName());
        assertEquals(1, userMapper.deleteById(id));
        assertNull(userMapper.selectById(id));

        assertEquals(Collections.emptyList(), citationMapper.selectList(new QueryWrapper<CitationEntity>().last("LIMIT 0")));
        assertNull(chunkVectorMapper.selectByCompositeId(UUID.randomUUID(), UUID.randomUUID()));
    }
}

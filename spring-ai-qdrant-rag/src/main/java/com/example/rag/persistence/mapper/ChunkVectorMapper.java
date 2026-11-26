package com.example.rag.persistence.mapper;

import com.example.rag.persistence.entity.ChunkVectorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import java.util.UUID;

@Mapper
public interface ChunkVectorMapper {
    @Select("SELECT * FROM chunk_vector WHERE chunk_id = #{chunkId} AND embedding_profile_id = #{profileId}")
    ChunkVectorEntity selectByCompositeId(@Param("chunkId") UUID chunkId, @Param("profileId") UUID profileId);

    @Insert("INSERT INTO chunk_vector (chunk_id,embedding_profile_id,index_status,last_indexed_at,last_error_code,created_at,updated_at) " +
            "VALUES (#{chunkId},#{embeddingProfileId},#{indexStatus},#{lastIndexedAt},#{lastErrorCode},#{createdAt},#{updatedAt})")
    int insert(ChunkVectorEntity entity);

    @Update("UPDATE chunk_vector SET index_status=#{indexStatus},last_indexed_at=#{lastIndexedAt},last_error_code=#{lastErrorCode},updated_at=#{updatedAt} " +
            "WHERE chunk_id=#{chunkId} AND embedding_profile_id=#{embeddingProfileId}")
    int updateByCompositeId(ChunkVectorEntity entity);

    @Delete("DELETE FROM chunk_vector WHERE chunk_id=#{chunkId} AND embedding_profile_id=#{profileId}")
    int deleteByCompositeId(@Param("chunkId") UUID chunkId, @Param("profileId") UUID profileId);
}

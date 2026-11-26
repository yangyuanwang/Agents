package com.example.rag.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    @Select("SELECT * FROM document WHERE id=#{id} FOR UPDATE")
    DocumentEntity selectByIdForUpdate(@Param("id") UUID id);
}

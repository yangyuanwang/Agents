package com.example.rag.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.FileObjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {
    @Select("SELECT * FROM file_object WHERE id=#{id} FOR UPDATE")
    FileObjectEntity selectByIdForUpdate(@Param("id") UUID id);
}

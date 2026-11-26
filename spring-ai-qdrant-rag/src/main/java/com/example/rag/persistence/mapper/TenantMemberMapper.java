package com.example.rag.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.persistence.entity.TenantMemberEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMemberMapper extends BaseMapper<TenantMemberEntity> { }

package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.*;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseMapper mapper;
    public KnowledgeBaseService(KnowledgeBaseMapper mapper) { this.mapper = mapper; }

    @Transactional
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        LocalDateTime now=LocalDateTime.now(); KnowledgeBaseEntity e=new KnowledgeBaseEntity();
        e.setId(UUID.randomUUID()); e.setTenantId(request.tenantId()); e.setCreatedBy(request.createdBy());
        e.setName(request.name()); e.setDescription(request.description()); e.setStatus("ACTIVE");
        e.setCreatedAt(now); e.setUpdatedAt(now); e.setIsDeleted(0); mapper.insert(e); return response(e);
    }
    @Transactional(readOnly=true)
    public KnowledgeBasePageResponse list(UUID tenantId, long page, long size) {
        Page<KnowledgeBaseEntity> p=mapper.selectPage(new Page<>(page,size),new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getTenantId,tenantId).orderByDesc(KnowledgeBaseEntity::getUpdatedAt));
        return new KnowledgeBasePageResponse(p.getRecords().stream().map(this::response).toList(),p.getTotal(),page,size);
    }
    @Transactional(readOnly=true)
    public KnowledgeBaseResponse get(UUID id) { return response(require(id)); }
    @Transactional
    public KnowledgeBaseResponse update(UUID id, KnowledgeBaseUpdateRequest req) {
        KnowledgeBaseEntity e=require(id); e.setName(req.name());e.setDescription(req.description());e.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(e);return response(e);
    }
    @Transactional
    public void delete(UUID id) { require(id); LocalDateTime now=LocalDateTime.now();
        mapper.update(null,new LambdaUpdateWrapper<KnowledgeBaseEntity>().eq(KnowledgeBaseEntity::getId,id)
                .set(KnowledgeBaseEntity::getIsDeleted,1).set(KnowledgeBaseEntity::getDeletedAt,now)
                .set(KnowledgeBaseEntity::getUpdatedAt,now)); }
    private KnowledgeBaseEntity require(UUID id) { KnowledgeBaseEntity e=mapper.selectById(id);if(e==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Knowledge base not found");return e; }
    private KnowledgeBaseResponse response(KnowledgeBaseEntity e) { return new KnowledgeBaseResponse(e.getId(),e.getTenantId(),e.getName(),e.getDescription(),e.getStatus(),e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt()); }
}

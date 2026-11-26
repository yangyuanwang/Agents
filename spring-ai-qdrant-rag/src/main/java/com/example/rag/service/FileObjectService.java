package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.rag.config.FileStorageProperties;
import com.example.rag.dto.FileObjectResponse;
import com.example.rag.persistence.entity.FileObjectEntity;
import com.example.rag.persistence.entity.KnowledgeBaseEntity;
import com.example.rag.persistence.mapper.FileObjectMapper;
import com.example.rag.persistence.mapper.KnowledgeBaseMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FileObjectService {
    private final FileObjectMapper files; private final KnowledgeBaseMapper knowledgeBases; private final LocalFileStorage storage;
    private final FileStorageProperties properties; private final TransactionTemplate transactions; private final JdbcTemplate jdbc;
    public FileObjectService(FileObjectMapper files, KnowledgeBaseMapper knowledgeBases, LocalFileStorage storage,
            FileStorageProperties properties, PlatformTransactionManager tx, JdbcTemplate jdbc) {
        this.files=files;this.knowledgeBases=knowledgeBases;this.storage=storage;this.properties=properties;this.transactions=new TransactionTemplate(tx);this.jdbc=jdbc;
    }
    public FileObjectResponse upload(UUID knowledgeBaseId, MultipartFile file) {
        if(file==null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件不能为空");
        if(file.getSize()>properties.getMaxSizeBytes()) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件超过大小限制");
        String name=Objects.requireNonNullElse(file.getOriginalFilename(),""); int dot=name.lastIndexOf('.');
        String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!properties.getAllowedExtensions().contains(ext)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"不支持的文件类型");
        Path staging=storage.newStagingPath(); java.util.concurrent.atomic.AtomicReference<Path> published=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean inserted=new java.util.concurrent.atomic.AtomicBoolean(false); byte[] digest; long actualSize;
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256"); actualSize=0;
            try(InputStream in=file.getInputStream();OutputStream out=Files.newOutputStream(staging,StandardOpenOption.CREATE_NEW)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){actualSize+=n;if(actualSize>properties.getMaxSizeBytes())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件超过大小限制");sha.update(buffer,0,n);out.write(buffer,0,n);}
            }
            if(actualSize==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件不能为空");digest=sha.digest();
            final long size=actualSize; final byte[] hash=digest;
            FileObjectResponse response;
            try { response=transactions.execute(status->{
                KnowledgeBaseEntity kb=knowledgeBases.selectById(knowledgeBaseId);
                if(kb==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识库不存在");
                FileObjectEntity old=findByHashForUpdate(kb.getTenantId(),hash);
                if(old!=null){if(!"AVAILABLE".equals(old.getStorageStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"文件正在清理或已删除");return map(old);}
                String storageKey=storage.newStorageKey();
                Path finalPath;
                try { finalPath=storage.resolve(storageKey); storage.publish(staging,storageKey); published.set(finalPath); }
                catch(IOException e){throw new IllegalStateException("文件存储失败",e);}
                FileObjectEntity entity=new FileObjectEntity();LocalDateTime now=LocalDateTime.now();entity.setId(UUID.randomUUID());entity.setTenantId(kb.getTenantId());entity.setStorageKey(storageKey);entity.setSha256(hash);entity.setByteSize(size);entity.setMediaType(mediaType(ext));entity.setStorageStatus("AVAILABLE");entity.setCreatedAt(now);entity.setUpdatedAt(now);
                files.insert(entity);inserted.set(true);return map(entity);
            }); }
            catch(DataAccessException race){
                // The unique (tenant_id, sha256) index arbitrates concurrent first uploads.
                UUID tenantId=transactions.execute(s->{KnowledgeBaseEntity kb=knowledgeBases.selectById(knowledgeBaseId);if(kb==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识库不存在");return kb.getTenantId();});
                FileObjectEntity winner=findByHashForUpdate(tenantId,digest);
                if(winner==null)throw race;
                if(!"AVAILABLE".equals(winner.getStorageStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"并发上传或文件清理冲突");response=map(winner);
            }
            // Existing hash reuse or unique-key loser: this request's staged/published copy is unreferenced.
            if(Files.exists(staging))storage.deleteStaging(staging);
            if(!inserted.get() && published.get()!=null)Files.deleteIfExists(published.get());
            return response;
        } catch(ResponseStatusException ex){cleanupQuiet(staging,published.get());throw ex;}
        catch(Exception ex){cleanupQuiet(staging,published.get());throw ex instanceof RuntimeException r?r:new IllegalStateException("文件上传失败",ex);}
    }
    @Transactional(readOnly=true)
    public FileObjectResponse get(UUID knowledgeBaseId,UUID fileId){
        KnowledgeBaseEntity kb=knowledgeBases.selectById(knowledgeBaseId);if(kb==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识库不存在");
        Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM document d JOIN document_version dv ON dv.document_id=d.id AND dv.tenant_id=d.tenant_id WHERE d.knowledge_base_id=? AND d.tenant_id=? AND d.deleted_at IS NULL AND dv.source_file_id=?",Integer.class,knowledgeBaseId.toString(),kb.getTenantId().toString(),fileId.toString());
        if(linked==null||linked==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识库中不存在该文件");
        FileObjectEntity f=files.selectOne(new QueryWrapper<FileObjectEntity>().eq("id",fileId).eq("tenant_id",kb.getTenantId()));
        if(f==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");return map(f);
    }
    @Transactional
    public int claimForDeletion(UUID fileId){
        FileObjectEntity f=files.selectByIdForUpdate(fileId);if(f==null||!"AVAILABLE".equals(f.getStorageStatus()))return 0;
        Integer refs=jdbc.queryForObject("SELECT COUNT(*) FROM document_version WHERE tenant_id=? AND source_file_id=?",Integer.class,f.getTenantId().toString(),fileId.toString());
        if(refs!=null&&refs>0)return 0;
        return jdbc.update("UPDATE file_object SET storage_status='DELETING',updated_at=? WHERE id=? AND storage_status='AVAILABLE'",LocalDateTime.now(),fileId.toString());
    }
    @Transactional
    public boolean cleanupClaimed(UUID fileId){
        FileObjectEntity f=files.selectByIdForUpdate(fileId);if(f==null||!"DELETING".equals(f.getStorageStatus()))return false;
        Integer refs=jdbc.queryForObject("SELECT COUNT(*) FROM document_version WHERE tenant_id=? AND source_file_id=?",Integer.class,f.getTenantId().toString(),fileId.toString());if(refs!=null&&refs>0)return false;
        try { storage.delete(f.getStorageKey()); }
        catch(IOException ex){jdbc.update("UPDATE file_object SET updated_at=? WHERE id=?",LocalDateTime.now(),fileId.toString());return false;}
        jdbc.update("UPDATE file_object SET storage_status='DELETED',deleted_at=?,updated_at=? WHERE id=?",LocalDateTime.now(),LocalDateTime.now(),fileId.toString());return true;
    }
    public int retryCleanupBatch(){
        List<UUID> ids=jdbc.query("SELECT id FROM file_object WHERE storage_status='DELETING' AND updated_at<=? ORDER BY updated_at LIMIT ?",(rs,row)->UUID.fromString(rs.getString(1)),LocalDateTime.now().minusNanos(properties.getCleanupRetryIntervalMs()*1_000_000),properties.getCleanupBatchSize());
        int done=0;for(UUID id:ids){try{if(Boolean.TRUE.equals(transactions.execute(s->cleanupClaimed(id))))done++;}catch(Exception ignored){/* retry on next scan; keep DELETING */}}return done;
    }
    private FileObjectEntity findByHashForUpdate(UUID tenant,byte[] hash){return files.selectOne(new QueryWrapper<FileObjectEntity>().eq("tenant_id",tenant).eq("sha256",hash).last("FOR UPDATE"));}
    private FileObjectResponse map(FileObjectEntity e){return new FileObjectResponse(e.getId(),e.getTenantId(),HexFormat.of().formatHex(e.getSha256()),e.getByteSize(),e.getMediaType(),e.getStorageStatus(),e.getCreatedAt());}
    private String mediaType(String ext){return switch(ext){case "pdf"->"application/pdf";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "md"->"text/markdown";default->"text/plain";};}
    private void cleanupQuiet(Path staging,Path published){try{if(published!=null)Files.deleteIfExists(published);storage.deleteStaging(staging);}catch(IOException ignored){}}
}

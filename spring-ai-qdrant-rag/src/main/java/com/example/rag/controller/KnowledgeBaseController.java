package com.example.rag.controller;

import com.example.rag.dto.*;
import com.example.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @Validated @RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;
    public KnowledgeBaseController(KnowledgeBaseService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public KnowledgeBaseResponse create(@Valid @RequestBody KnowledgeBaseCreateRequest req){return service.create(req);}
    @GetMapping public KnowledgeBasePageResponse list(@RequestParam @NotNull UUID tenantId,@RequestParam(defaultValue="1") @Min(1) long page,@RequestParam(defaultValue="20") @Min(1) @Max(100) long size){return service.list(tenantId,page,size);}
    @GetMapping("/{id}") public KnowledgeBaseResponse get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}") public KnowledgeBaseResponse update(@PathVariable UUID id,@Valid @RequestBody KnowledgeBaseUpdateRequest req){return service.update(id,req);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id);}
}

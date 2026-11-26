package com.example.rag.controller;

import com.example.rag.dto.*;
import com.example.rag.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @Validated @RequestMapping("/api/documents")
public class DocumentController {
 private final DocumentService service;public DocumentController(DocumentService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest req){return service.create(req);}
 @GetMapping public DocumentPageResponse list(@RequestParam UUID knowledgeBaseId,@RequestParam(defaultValue="1") @Min(1) long page,@RequestParam(defaultValue="20") @Min(1) @Max(100) long size,@RequestParam(required=false) String keyword){return service.list(knowledgeBaseId,keyword,page,size);}
 @GetMapping("/{id}") public DocumentResponse get(@PathVariable UUID id,@RequestParam UUID knowledgeBaseId){return service.get(knowledgeBaseId,id);}
 @PutMapping("/{id}") public DocumentResponse update(@PathVariable UUID id,@RequestParam UUID knowledgeBaseId,@Valid @RequestBody DocumentUpdateRequest req){return service.update(knowledgeBaseId,id,req);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,@RequestParam UUID knowledgeBaseId){service.delete(knowledgeBaseId,id);}
 @PostMapping("/{id}/versions") @ResponseStatus(HttpStatus.CREATED) public DocumentVersionResponse createVersion(@PathVariable UUID id,@Valid @RequestBody DocumentVersionCreateRequest req){return service.createVersion(id,req);}
 @GetMapping("/{id}/versions") public List<DocumentVersionResponse> versions(@PathVariable UUID id){return service.listVersions(id);}
 @GetMapping("/{id}/versions/{versionId}") public DocumentVersionResponse version(@PathVariable UUID id,@PathVariable UUID versionId){return service.getVersion(id,versionId);}
 @PutMapping("/{id}/active-version") public DocumentResponse activate(@PathVariable UUID id,@Valid @RequestBody SetActiveVersionRequest req){return service.activate(id,req.versionId());}
}

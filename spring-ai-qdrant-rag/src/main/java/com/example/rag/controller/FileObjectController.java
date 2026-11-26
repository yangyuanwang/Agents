package com.example.rag.controller;

import com.example.rag.dto.FileObjectResponse;
import com.example.rag.service.FileObjectService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController @Validated @RequestMapping("/api/files")
public class FileObjectController {
    private final FileObjectService service;
    public FileObjectController(FileObjectService service){this.service=service;}
    @PostMapping("/upload") @ResponseStatus(HttpStatus.CREATED)
    public FileObjectResponse upload(@RequestParam @NotNull UUID knowledgeBaseId,@RequestPart("file") MultipartFile file){return service.upload(knowledgeBaseId,file);}
    @GetMapping("/{id}")
    public FileObjectResponse get(@PathVariable UUID id,@RequestParam @NotNull UUID knowledgeBaseId){return service.get(knowledgeBaseId,id);}
}

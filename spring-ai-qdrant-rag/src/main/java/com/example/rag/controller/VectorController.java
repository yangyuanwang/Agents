package com.example.rag.controller;

import com.example.rag.dto.*;
import com.example.rag.service.VectorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vector")
public class VectorController {
    private final VectorService service;
    public VectorController(VectorService service) { this.service = service; }
    @PostMapping("/add") @ResponseStatus(HttpStatus.CREATED)
    public AddResponse add(@Valid @RequestBody VectorAddRequest request) { service.add(request); return new AddResponse(request.id(), "stored"); }
    @PostMapping("/search")
    public List<VectorSearchResult> search(@Valid @RequestBody VectorSearchRequest request) { return service.search(request); }
    public record AddResponse(String id, String status) {}
}

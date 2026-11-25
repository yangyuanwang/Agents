package com.example.rag.common;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "error", "请求参数校验失败"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> failure(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("timestamp", Instant.now(), "error", "服务处理失败", "message", ex.getMessage() == null ? "未知错误" : ex.getMessage()));
    }
}

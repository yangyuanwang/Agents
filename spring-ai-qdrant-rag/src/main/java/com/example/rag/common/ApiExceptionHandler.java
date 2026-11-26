package com.example.rag.common;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "error", "请求参数校验失败"));
    }
    /**
     * 未实现的路径必须保持 404：前端 registry.ts 用 404/405/415/501 识别「待开发」。
     * Spring 6.2 对找不到的路径抛 NoResourceFoundException，若不单独接住，
     * 会被下面的 Exception 兜底变成 500，页面就显示成「加载失败」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> notFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("timestamp", Instant.now(), "error", "接口尚未实现"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("timestamp", Instant.now(), "error", ex.getReason() == null ? "请求失败" : ex.getReason()));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "error", "请求参数校验失败"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> failure(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("timestamp", Instant.now(), "error", "服务处理失败", "message", ex.getMessage() == null ? "未知错误" : ex.getMessage()));
    }
}

package com.machining.quote.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({RuntimeException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBusinessException(Exception e) {
        return ResponseEntity.badRequest().body(error(e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception e) {
        return ResponseEntity.badRequest().body(error("请求参数不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("服务器处理失败：" + e.getMessage()));
    }

    private Map<String, Object> error(String message) {
        return Map.of(
                "success", false,
                "message", message == null || message.isBlank() ? "请求处理失败" : message,
                "data", ""
        );
    }
}

package com.machining.quote.config;

import com.machining.quote.service.auth.DataCenterSsoLoginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_ERROR_MESSAGE = "服务器处理失败，请联系管理员";
    private static final String DATA_CONFLICT_MESSAGE = "数据已存在或被重复提交，请刷新后重试";
    private static final String DATA_SCOPE_DENIED_MESSAGE = "当前账号无权访问所选询价单";
    private static final String PREVIEW_FINGERPRINT_CONFLICT_MESSAGE = "数据已发生变化，请重新预览";

    @ExceptionHandler(PreviewFingerprintConflictException.class)
    public ResponseEntity<Map<String, Object>> handlePreviewFingerprintConflict(
            PreviewFingerprintConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(PREVIEW_FINGERPRINT_CONFLICT_MESSAGE));
    }

    @ExceptionHandler(DataScopeAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDataScopeAccessDenied(DataScopeAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(DATA_SCOPE_DENIED_MESSAGE));
    }

    @ExceptionHandler(DataCenterSsoLoginException.class)
    public ResponseEntity<Map<String, Object>> handleDataCenterSsoLogin(DataCenterSsoLoginException e) {
        return ResponseEntity.status(e.status()).body(error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(error(clientMessage(e)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        if (e.getCause() == null && isClientSafeMessage(e.getMessage())) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
        log.error("Internal illegal state error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(GENERIC_ERROR_MESSAGE));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Database integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(DATA_CONFLICT_MESSAGE));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        if (e.getClass().equals(RuntimeException.class)
                && e.getCause() == null
                && isClientSafeMessage(e.getMessage())) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
        log.error("Unhandled runtime error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(GENERIC_ERROR_MESSAGE));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception e) {
        return ResponseEntity.badRequest().body(error("请求参数不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled server error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(GENERIC_ERROR_MESSAGE));
    }

    private Map<String, Object> error(String message) {
        return Map.of(
                "success", false,
                "message", message == null || message.isBlank() ? "请求处理失败" : message,
                "data", ""
        );
    }

    private String clientMessage(Exception e) {
        String message = e.getMessage();
        return isClientSafeMessage(message) ? message : "请求参数不正确";
    }

    private boolean isClientSafeMessage(String message) {
        if (message == null || message.isBlank() || message.length() > 200) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        return !text.contains("exception")
                && !text.contains("sql")
                && !text.contains("jdbc")
                && !text.contains("postgres")
                && !text.contains("org.")
                && !text.contains("java.")
                && !text.contains("\n")
                && !text.contains("\r");
    }
}

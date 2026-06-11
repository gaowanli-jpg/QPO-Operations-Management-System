package com.machining.apssystem.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(IllegalArgumentException ex) {
        return Map.of(
                "success", false,
                "message", message(ex)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleConflict(IllegalStateException ex) {
        return Map.of(
                "success", false,
                "message", message(ex)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()
                        ? error.getField() + " is invalid."
                        : error.getDefaultMessage())
                .orElse("Request validation failed.");
        return Map.of(
                "success", false,
                "message", message
        );
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleDataIntegrity(Exception ex) {
        return Map.of(
                "success", false,
                "message", dataIntegrityMessage(ex)
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception ex) {
        return Map.of(
                "success", false,
                "message", message(ex)
        );
    }

    private String message(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private String dataIntegrityMessage(Exception ex) {
        String message = message(ex);
        if (message.contains("uq_aps_part_inventory_customer_part_version")) {
            return "Part inventory already exists. Use the update endpoint to change quantities.";
        }
        if (message.contains("uq_aps_material_inventory_name_type")) {
            return "Material inventory already exists. Use the update endpoint to change weights.";
        }
        if (message.contains("uq_aps_department_name")) {
            return "Department name already exists.";
        }
        if (message.contains("uq_aps_machine_department_code")) {
            return "Machine code already exists in this department.";
        }
        if (message.contains("uq_aps_machine_process")) {
            return "Machine capability already exists.";
        }
        if (message.contains("uq_aps_user_username")) {
            return "Username already exists.";
        }
        return message;
    }
}

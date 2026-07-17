package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogDTO {
    private Integer id;
    private String username;
    private String role;
    private String module;
    private String action;
    private String actionLabel;
    private String targetType;
    private String targetId;
    private String result;
    private String message;
    private String beforeData;
    private String afterData;
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestPath;
    private String requestId;
    private String errorMessage;
    private LocalDateTime createdAt;
}

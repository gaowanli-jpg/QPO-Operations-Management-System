package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String role;
    private String action;
    private String module;
    private String result;
    private String message;
    private String targetType;
    private String targetId;
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

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
    private String targetType;
    private String targetId;
    private String beforeData;
    private String afterData;
    private LocalDateTime createdAt;
}

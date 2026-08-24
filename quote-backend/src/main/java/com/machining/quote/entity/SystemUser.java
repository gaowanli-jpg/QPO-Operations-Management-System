package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_user")
public class SystemUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer legacyUserId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long postId;
    private String legacyRole;
    private Boolean enabled;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}

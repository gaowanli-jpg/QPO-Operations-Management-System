package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_role_permission")
public class SystemRolePermission {
    private Long roleId;
    private Long permissionId;
    private LocalDateTime createdAt;
}

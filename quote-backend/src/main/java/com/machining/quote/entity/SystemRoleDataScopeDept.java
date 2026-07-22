package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_role_data_scope_dept")
public class SystemRoleDataScopeDept {
    private Long roleId;
    private Long deptId;
    private LocalDateTime createdAt;
}

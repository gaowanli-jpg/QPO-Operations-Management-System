package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_role_data_scope_customer")
public class SystemRoleDataScopeCustomer {
    private Long roleId;
    private String customerCode;
    private String customerName;
    private String customerNameSnapshot;
    private LocalDateTime createdAt;
}

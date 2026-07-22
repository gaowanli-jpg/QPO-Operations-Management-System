package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_user_role")
public class SystemUserRole {
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}

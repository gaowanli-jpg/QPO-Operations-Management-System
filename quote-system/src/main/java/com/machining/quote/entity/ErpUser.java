package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("erp_user")
public class ErpUser {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private Boolean enabled;
    private LocalDateTime createdAt;
}

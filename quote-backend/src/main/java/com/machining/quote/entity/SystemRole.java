package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_role")
public class SystemRole {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;
    private String roleName;
    private String description;
    private Boolean superAdmin;
    private String dataScope;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}

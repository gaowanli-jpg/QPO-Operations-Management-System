package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_permission")
public class SystemPermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String permissionType;
    private String permissionCode;
    private String permissionName;
    private String routePath;
    private String componentKey;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean enabled;
    private Boolean assignable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

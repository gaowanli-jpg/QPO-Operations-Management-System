package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u9_sync_department_option_cache")
public class U9SyncDepartmentOptionCache {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String u9OrgCode;
    private String u9DepartmentId;
    private String departmentCode;
    private String departmentName;
    private String parentDepartmentCode;
    private String treeDisplayName;
    private Boolean enabled;
    private LocalDateTime lastSyncedAt;
    private String sourceHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

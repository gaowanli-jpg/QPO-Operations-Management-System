package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_dept")
public class SystemDept {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String deptCode;
    private String deptName;
    private Long leaderUserId;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}

package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("aps_department_task")
public class ApsDepartmentTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private Long departmentAssignmentId;
    private Long departmentId;
    private String departmentName;
    private BigDecimal requiredHours;
    private String taskStatus;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private LocalDateTime releasedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

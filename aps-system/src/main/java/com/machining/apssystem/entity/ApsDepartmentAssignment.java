package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("aps_department_assignment")
public class ApsDepartmentAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private Long departmentId;
    private String departmentName;
    private BigDecimal requiredHours;
    private String status;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private LocalDateTime releasedAt;
    private String remark;
    private Boolean capacityCounted;
}

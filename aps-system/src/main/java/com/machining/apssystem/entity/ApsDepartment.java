package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("aps_department")
public class ApsDepartment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String departmentName;
    private String departmentType;
    private String departmentStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal monthlyAvailableHours;

    private BigDecimal occupiedHours;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private BigDecimal remainingHours;

    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

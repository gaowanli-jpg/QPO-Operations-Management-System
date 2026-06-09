package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_production_requirement")
public class ApsProductionRequirement {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private String customerName;
    private String partNo;
    private String version;
    private Integer requiredQty;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

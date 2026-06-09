package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("aps_material_shortage")
public class ApsMaterialShortage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private String rfqNo;
    private String internalProjectNo;
    private String customerName;
    private String partNo;
    private String version;
    private String materialName;
    private String materialType;
    private BigDecimal shortageWeightKg;
    private String sourceTask;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

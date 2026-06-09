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
@TableName("aps_material_inventory")
public class ApsMaterialInventory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialName;
    private String materialType;
    private BigDecimal stockWeightKg;
    private BigDecimal lockedWeightKg;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private BigDecimal availableWeightKg;

    private BigDecimal safetyStockWeightKg;
    private String unit;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

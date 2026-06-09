package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_part_inventory")
public class ApsPartInventory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerName;
    private String partNo;
    private String version;
    private Integer inStockQty;
    private Integer inProductionQty;
    private Integer lockedInStockQty;
    private Integer lockedInProductionQty;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableInStockQty;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableInProductionQty;

    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

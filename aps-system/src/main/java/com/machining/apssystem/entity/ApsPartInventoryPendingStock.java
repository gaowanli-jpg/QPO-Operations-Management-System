package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_part_inventory_pending_stock")
public class ApsPartInventoryPendingStock {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partInventoryId;
    private String pendingRefNo;
    private Integer pendingQty;
    private Integer lockedQty;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableQty;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

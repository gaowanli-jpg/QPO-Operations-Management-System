package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_part_inventory_lock")
public class ApsPartInventoryLock {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private Long partInventoryId;
    private String customerName;
    private String partNo;
    private String version;
    private Integer demandQty;
    private Integer lockedInStockQty;
    private Integer lockedInProductionQty;
    private Integer productionRequiredQty;
    private String status;
    private LocalDateTime lockedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

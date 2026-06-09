package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_part_inventory_lock_detail")
public class ApsPartInventoryLockDetail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partInventoryLockId;
    private Long workItemId;
    private Long partInventoryId;
    private String lockType;
    private Long pendingStockId;
    private String pendingRefNo;
    private Integer lockedQty;
    private String status;
    private LocalDateTime confirmedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

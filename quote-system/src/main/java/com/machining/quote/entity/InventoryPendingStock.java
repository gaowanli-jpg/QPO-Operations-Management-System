package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory_pending_stock")
public class InventoryPendingStock {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer inventoryId;
    private String pendingRefNo;
    private Integer pendingQty;
    private LocalDateTime updatedAt;
}

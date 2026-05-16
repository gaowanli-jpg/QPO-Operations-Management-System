package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory_master")
public class InventoryMaster {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String customerName;
    private String partNo;
    private Integer inStockQty;
    private Integer pendingQty;
    private String pendingRefNo;
    private LocalDateTime updatedAt;
}

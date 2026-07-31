package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sales_order_item_assembly_relation")
public class SalesOrderItemAssemblyRelation {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;
    private Long parentItemId;
    private Long childItemId;
    private BigDecimal qtyPerParent;
    private Integer sortNo;
    private Integer sourceParentItemId;
    private Integer sourceChildItemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

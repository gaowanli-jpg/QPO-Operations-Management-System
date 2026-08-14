package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wms_reservation_item")
public class WmsReservationItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String productCode;
    private BigDecimal reserveCount;
    private String remark;
    private BigDecimal stockCount;
    private BigDecimal inProductionCount;
    private BigDecimal frozenCount;
    private BigDecimal surplusCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

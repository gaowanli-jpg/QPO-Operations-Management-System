package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wms_reservation_ticket")
public class WmsReservationTicket {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Long batchItemId;
    private Integer ticketId;
    private Long salesOrderItemId;
    private Integer salesOrderLineNo;
    private String productCode;
    private BigDecimal reserveCount;
    private BigDecimal stockFrozenCount;
    private BigDecimal inProductionCount;
    private BigDecimal surplusCount;
    private String status;
    private String message;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

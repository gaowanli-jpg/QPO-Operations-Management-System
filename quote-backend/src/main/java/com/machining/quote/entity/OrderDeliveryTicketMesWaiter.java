package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_delivery_ticket_mes_waiter")
public class OrderDeliveryTicketMesWaiter {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String dedupKey;
    private Integer masterTicketId;
    private String masterMesRequestId;
    private Integer waiterTicketId;
    private String waitStatus;
    private String materialNo;
    private String waitMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}

package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_delivery_ticket_mes_request")
public class OrderDeliveryTicketMesRequest {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer ticketId;
    private String mesRequestId;
    private Long systemTaskId;
    private String requestStatus;
    private String mesExternalTaskId;
    private String materialNo;
    private String requestMessage;
    private String responseMessage;
    private String callbackMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime callbackReceivedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

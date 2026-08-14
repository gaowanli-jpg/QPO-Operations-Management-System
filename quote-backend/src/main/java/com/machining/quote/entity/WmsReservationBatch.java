package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "wms_reservation_batch", autoResultMap = true)
public class WmsReservationBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String externalBizId;
    private Long systemTaskId;
    private Long saleOrderId;
    private String saleOrderNo;
    private String requestFingerprint;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object requestPayload;

    private Integer itemCount;
    private Integer ticketCount;
    private String status;
    private Integer attemptNo;
    private String lastErrorType;
    private String responseCode;
    private String responseMsg;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime completedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

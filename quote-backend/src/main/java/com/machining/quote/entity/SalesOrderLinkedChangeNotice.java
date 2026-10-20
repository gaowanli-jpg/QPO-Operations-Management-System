package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_linked_change_notice")
public class SalesOrderLinkedChangeNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private String salesOrderNo;
    private Integer lineNo;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerOrderNo;
    private String internalProjectNo;
    private String partNo;
    private String version;
    private String description;
    private String materialName;
    private String triggerScope;
    private String linkedDiscardEventKey;
    private LocalDateTime linkedDiscardedAt;
    private Long linkedDiscardedBy;
    private String linkedDiscardReason;
    private Boolean wmsSubmitted;
    private String wmsStatus;
    private String wmsRequestId;
    private LocalDateTime wmsActionAt;
    private Boolean mesProductionSubmitted;
    private String mesProductionStatus;
    private String mesProductionRequestId;
    private LocalDateTime mesProductionActionAt;
    private Boolean stockPickingSubmitted;
    private String stockPickingStatus;
    private String stockPickingBizId;
    private LocalDateTime stockPickingActionAt;
    private Integer downstreamImpactCount;
    private String handlingStatus;
    private LocalDateTime handledAt;
    private Long handledBy;
    private String handleRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

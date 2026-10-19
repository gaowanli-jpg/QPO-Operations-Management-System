package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_change_notice")
public class SalesOrderChangeNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeId;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private Long allocationId;
    private String targetType;
    private String targetKey;
    private String targetExternalId;
    private String sourceBizId;
    private String changeType;
    private String reason;
    private String changedFieldsJson;
    private String payloadSnapshotJson;
    private String status;
    private Long autoDispatchEpoch;
    private Integer attemptCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;
    private String lastError;
    private String sourceEventKey;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isHidden;
    private String hiddenReason;
    private LocalDateTime hiddenAt;
    private Long hiddenBy;
    private String hiddenByName;
    private LocalDateTime restoredAt;
    private Long restoredBy;
}

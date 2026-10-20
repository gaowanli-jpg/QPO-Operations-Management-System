package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderChangeNoticeDTO {
    private Long id;
    private String noticeId;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long salesOrderItemId;
    private Integer lineNo;
    private String customerName;
    private String partNo;
    private String version;
    private Integer allocationId;
    private String targetType;
    private String targetKey;
    private String targetExternalId;
    private String sourceBizId;
    private String changeType;
    private String reason;
    private List<String> changedFields;
    private Map<String, Object> snapshot;
    private String status;
    private Long autoDispatchEpoch;
    private Integer attemptCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;
    private String lastError;
    private String createdByName;
    private LocalDateTime createdAt;
    private Boolean hidden;
    private String hiddenReason;
    private LocalDateTime hiddenAt;
    private String hiddenByName;
    private LocalDateTime restoredAt;
    private Long restoredBy;
}

package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangePreviewResp {
    private Long salesOrderId;
    private String salesOrderNo;
    private String sourceType;
    private String sourceLabel;
    private String documentNo;
    private String customerName;
    private String customerShortName;
    private String customerCode;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String internalProjectNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String documentTypeCode;
    private String status;
    private String u9SyncStatus;
    private String u9OrderCode;
    private String u9SyncMessage;
    private Boolean changeEditable;
    private String changeBlockedReason;
    private Integer itemCount;
    private List<SalesOrderU9SyncPreviewLine> lines = new ArrayList<>();
}

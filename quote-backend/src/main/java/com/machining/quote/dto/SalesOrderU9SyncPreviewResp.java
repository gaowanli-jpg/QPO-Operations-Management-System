package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9SyncPreviewResp {
    private Long salesOrderId;
    private String salesOrderNo;
    private String sourceType;
    private String sourceLabel;
    private String documentNo;
    private String customerName;
    private String customerShortName;
    private String customerCode;
    private String orderBy;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String internalProjectNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String documentTypeCode;
    private String status;
    private String sellerCode;
    private String sellerName;
    private String saleDepartmentCode;
    private String saleDepartmentName;
    private String u9SyncStatus;
    private String u9OrderCode;
    private String u9SyncMessage;
    private String priceMode;
    private String priceModeLabel;
    private String u9PriceField;
    private Boolean syncReady;
    private String blockedReason;
    private List<String> blockingErrors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String requestFingerprint;
    private Integer itemCount;
    private Integer materialConfirmedItemCount;
    private Integer missingMaterialItemCount;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<SalesOrderU9SyncPreviewLine> records = new ArrayList<>();
    private List<SalesOrderU9SyncPreviewLine> lines = new ArrayList<>();
}

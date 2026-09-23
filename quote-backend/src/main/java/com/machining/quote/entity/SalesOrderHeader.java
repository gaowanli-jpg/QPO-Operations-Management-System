package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sales_order_header")
public class SalesOrderHeader {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String salesOrderNo;
    private String documentNo;
    private String documentTypeCode;
    private Long customerId;
    private String customerCode;
    private String customerShortName;
    private String customerName;
    private String drawingFolderUrl;
    private String drawingFolderName;
    private Integer drawingFolderFileCount;
    private String sourceType;
    private String sourceReleaseBatchKey;
    private String sourceReleaseRequestHash;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
    private String status;
    private Boolean linkedDiscarded;
    private LocalDateTime linkedDiscardedAt;
    private Long linkedDiscardedBy;
    private String linkedDiscardReason;
    private LocalDateTime linkedRestoredAt;
    private Long linkedRestoredBy;
    private String linkedRestoreReason;
    private String u9SellerCode;
    private String u9SellerName;
    private String u9SaleDepartmentCode;
    private String u9SaleDepartmentName;
    private String u9SyncStatus;
    private String u9OrderId;
    private String u9OrderCode;
    private String u9SyncMessage;
    private String u9SyncAttemptId;
    private String u9SyncRequestHash;
    private LocalDateTime u9SyncStartedAt;
    private LocalDateTime u9SyncFinishedAt;
    private Long importBatchId;
    private Long importRecordId;
    private Integer totalItemCount;
    private Integer matchedItemCount;
    private Integer unmatchedItemCount;
    private String warningMessage;
    private Long createdBy;
    private Integer ownerUserId;
    private Long ownerDeptId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

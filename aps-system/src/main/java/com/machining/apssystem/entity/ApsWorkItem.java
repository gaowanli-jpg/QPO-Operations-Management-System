package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("aps_work_item")
public class ApsWorkItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long erpSyncRunId;
    private Integer erpTicketId;
    private Integer erpRfqId;
    private Integer erpItemId;
    private Integer erpProcessSheetId;
    private String processSnapshotSource;
    private String customerName;
    private String orderStatus;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String projectNo;
    private String orderType;
    private String itemType;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private Integer sourceItemQty;
    private String unit;
    private String materialName;
    private String materialType;
    private BigDecimal materialGrossWeightKg;
    private BigDecimal materialDemandWeightKg;
    private BigDecimal partNetWeightKg;
    private String surfaceTreatment;
    private String leadTime;
    private String quoteLeadTime;
    private String internalTargetLeadTime;
    private LocalDate startDate;
    private LocalDate deliveryDate;
    private String remark;
    private String arrangeType;
    private String drawingUrl;
    private String drawingViewUrl;
    private String materialData;
    private String sourcePayload;
    private BigDecimal totalWorkHours;
    private String partStockStatus;
    private String materialStockStatus;
    private String assignmentStatus;
    private String status;
    private Long assignedDepartmentId;
    private String assignedDepartmentName;
    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

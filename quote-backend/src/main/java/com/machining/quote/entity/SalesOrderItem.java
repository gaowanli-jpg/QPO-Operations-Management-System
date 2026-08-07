package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "sales_order_item", autoResultMap = true)
public class SalesOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private Integer sourceTicketId;
    private LocalDate salesOrderDate;
    private Integer lineNo;
    private String partNo;
    private String description;
    private Integer qty;
    private String version;
    private String orderType;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String internalProjectNo;
    private String salesMaterialNo;
    private String materialNo;
    private String materialNoStatus;
    private String materialNoSource;
    private Long materialNoSourceTicketId;
    private LocalDateTime materialNoSyncedAt;
    private String materialNoMessage;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private String materialName;
    private String engineeringMaterialName;
    private String materialShape;
    private String surfaceTreatment;
    private BigDecimal taxIncludedUnitPrice;
    private BigDecimal untaxedUnitPrice;
    private BigDecimal taxIncludedTotalPrice;
    private BigDecimal untaxedTotalPrice;
    private Integer shippedQty;
    private Integer unshippedQty;
    private Integer releasedQty;
    private Integer remainingQty;
    private String drawingUrl;
    private Boolean hasDrawing;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private String quoteMode;
    private BigDecimal assemblyFee;
    private BigDecimal baseCost;
    private String itemType;
    private String assemblyRelationStatus;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object materialData;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object feeData;

    private Integer matchedRfqId;
    private Integer matchedItemId;
    private String matchStatus;
    private String matchMessage;
    private String remark;
    private String customerRequirement;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

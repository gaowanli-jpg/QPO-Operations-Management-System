package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesOrderQpoExcelLineDTO {
    private int sourceRowNumber;
    private Integer salesOrderLineNo;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    /**
     * @deprecated 新销售订单模板不再导入销售料号；正式物料号只由 MES 回写 materialNo。
     */
    @Deprecated
    private String salesMaterialNo;
    private String internalProjectNo;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private String partNo;
    private Integer qty;
    private Integer shippedQty;
    private Integer unshippedQty;
    private String description;
    private String materialName;
    private String surfaceTreatment;
    private String version;
    private BigDecimal taxIncludedUnitPrice;
    private BigDecimal untaxedUnitPrice;
    private BigDecimal taxIncludedTotalPrice;
    private BigDecimal untaxedTotalPrice;
    private String remark;
}

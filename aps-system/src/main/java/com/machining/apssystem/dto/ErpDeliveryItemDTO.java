package com.machining.apssystem.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ErpDeliveryItemDTO {
    private Integer ticketId;
    private Integer rfqId;
    private Integer itemId;
    private Integer sourceProcessSheetId;

    private String rfqNo;
    private String customerName;
    private String orderStatus;
    private String internalProjectNo;
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
    private Double materialGrossWeight;
    private Double materialDemandWeight;
    private Double partNetWeight;
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

    private Object materialData;
    private String processSnapshotSource;
    private Double totalWorkHours;
    private List<ProcessRow> processRows;

    @Data
    public static class ProcessRow {
        private Integer sortNo;
        private String processName;
        private String calcType;
        private String sourceType;
        private Double inputVal;
        private Double workHours;
        private Double unitRate;
        private Double lineCost;
    }
}

package com.machining.quote.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SalesOrderMatchPreviewRow {
    private Long salesOrderId;
    private Long salesOrderItemId;
    private String salesOrderNo;
    private String customerOrderNo;
    private String internalProjectNo;
    private String partNo;
    private String version;
    private Integer lineNo;
    private Integer qty;
    private String currentMatchStatus;
    private Boolean matched;
    private Integer sourceRfqId;
    private Integer sourceItemId;
    private String sourcePartNo;
    private String sourceVersion;
    private String materialName;
    private String engineeringMaterialCode;
    private String engineeringMaterialName;
    private String engineeringMaterialTypeCode;
    private String engineeringMaterialTypeName;
    private String engineeringMaterialCategory;
    private String materialShape;
    private String surfaceTreatment;
    private String quoteMode;
    private String itemType;
    private String currentProcessRouteSummary;
    private String sourceProcessRouteSummary;
    private String warningMessage;
    private Map<String, Object> changedFields = new LinkedHashMap<>();
    private Map<String, Object> protectedFields = new LinkedHashMap<>();
    private String assemblyRelationStatus;
}

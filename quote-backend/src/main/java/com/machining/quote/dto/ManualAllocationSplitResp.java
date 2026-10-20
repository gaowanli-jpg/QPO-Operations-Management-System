package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ManualAllocationSplitResp {
    private Boolean success = true;
    private String message;
    private Long salesOrderId;
    private Integer sourceAllocationId;
    private Integer sourceAllocationOldQty;
    private Integer inStockQty;
    private Integer inProductionQty;
    private Integer productionRequiredQty;
    private List<Integer> createdAllocationIds = new ArrayList<>();
    private List<Long> createdSalesOrderItemIds = new ArrayList<>();
}

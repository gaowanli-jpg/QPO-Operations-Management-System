package com.machining.quote.dto;

import lombok.Data;

@Data
public class ManualAllocationSplitReq {
    private Integer allocationId;
    private Integer inStockQty;
    private Integer inProductionQty;
}

package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderMatchApplyReq {
    private List<Long> salesOrderIds = new ArrayList<>();
    private List<Long> salesOrderItemIds = new ArrayList<>();
    private boolean applyAllMatchedInSelectedOrders;
    private boolean overwriteExistingProductionSnapshot;
    private boolean overwriteTemplateMaterial;
}

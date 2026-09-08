package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class LockedItemStockPickingBatchReq {
    private List<Integer> allocationIds;
}

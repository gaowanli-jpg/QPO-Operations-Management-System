package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class LockedItemMesProductionBatchReq {
    private List<Integer> allocationIds;
}

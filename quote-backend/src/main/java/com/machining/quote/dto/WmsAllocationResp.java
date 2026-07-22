package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class WmsAllocationResp {
    private Boolean success;
    private String materialNo;
    private Integer inStockLockedQty;
    private List<InProductionLock> inProductionLocks;
    private String message;
    private String externalTraceId;

    @Data
    public static class InProductionLock {
        private String productionOrderNo;
        private Integer lockedQty;
    }
}

package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class RfqReleaseAppendSalesOrderReq {
    private Long targetSalesOrderId;
    private String appendBatchKey;
    private List<Integer> rfqIds;
    private List<ReleaseItemReq> items;

    @Data
    public static class ReleaseItemReq {
        private Integer itemId;
        private ReleaseSourceType sourceType;
        private Long sourceOrderId;
        private Long sourceItemId;
        private Integer acceptedQty;
    }
}

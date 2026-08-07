package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DeliveryGenerateReq {
    private List<Integer> rfqIds;
    private String documentTypeCode;
    private LocalDate salesOrderDate;
    private String customerOrderTitle;
    private String releaseBatchKey;
    private String orderType;
    private String projectNo;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String arrangeType;
    private List<DeliveryItemReq> items;

    @Data
    public static class DeliveryItemReq {
        private Integer itemId;
        private ReleaseSourceType sourceType;
        private Long sourceOrderId;
        private Long sourceItemId;
        private Integer acceptedQty;
    }
}

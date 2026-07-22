package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DeliveryGenerateReq {
    private Integer rfqId;
    private String projectNo;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private LocalDate startDate;
    private String arrangeType;
    private List<DeliveryItemReq> items;

    @Data
    public static class DeliveryItemReq {
        private Integer itemId;
        private Integer acceptedQty;
    }
}

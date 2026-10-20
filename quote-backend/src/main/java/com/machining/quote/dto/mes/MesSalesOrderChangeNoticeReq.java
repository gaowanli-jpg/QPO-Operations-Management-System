package com.machining.quote.dto.mes;

import lombok.Data;

import java.util.List;

@Data
public class MesSalesOrderChangeNoticeReq {
    private String bizId;
    private List<Item> items;

    @Data
    public static class Item {
        private String noticeId;
        private String changeType;
        private String targetType;
        private String targetExternalId;
        private String sourceBizId;
        private String salesOrderNo;
        private String saleOrderItemNo;
        private Long allocationId;
        private List<String> changedFields;
        private String reason;
        private Object item;
    }
}

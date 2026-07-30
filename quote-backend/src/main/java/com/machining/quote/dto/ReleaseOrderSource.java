package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseOrderSource {
    private ReleaseSourceType sourceType;
    private Long sourceOrderId;

    public static ReleaseOrderSource rfq(Integer rfqId) {
        ReleaseOrderSource source = new ReleaseOrderSource();
        source.setSourceType(ReleaseSourceType.RFQ);
        source.setSourceOrderId(rfqId == null ? null : rfqId.longValue());
        return source;
    }

    public static ReleaseOrderSource salesOrder(Long salesOrderId) {
        ReleaseOrderSource source = new ReleaseOrderSource();
        source.setSourceType(ReleaseSourceType.SALES_ORDER);
        source.setSourceOrderId(salesOrderId);
        return source;
    }
}

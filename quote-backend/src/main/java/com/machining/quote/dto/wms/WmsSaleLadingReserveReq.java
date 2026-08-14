package com.machining.quote.dto.wms;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WmsSaleLadingReserveReq {
    private String externalBizId;
    private String saleNo;
    private List<Item> items;

    @Data
    public static class Item {
        private String productCode;
        private BigDecimal reserveCount;
        private String remark;
    }
}

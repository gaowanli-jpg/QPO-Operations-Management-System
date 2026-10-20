package com.machining.quote.dto.wms;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
public class WmsSaleLadingReserveResp {
    private Integer code;
    private String msg;
    private Data data;

    @lombok.Data
    public static class Data {
        private String saleOrderNo;
        private String externalBizId;
        private List<Item> items;
    }

    @lombok.Data
    public static class Item {
        private String productCode;
        private BigDecimal reserveCount;
        private BigDecimal stockCount;
        private BigDecimal inProductionCount;
        private BigDecimal frozenCount;
        private BigDecimal surplusCount;
    }
}

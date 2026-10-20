package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MesProductionOrderCreateReq {
    private String bizId;
    private List<Item> items;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String orderType;
        private String productCode;
        private BigDecimal planCount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime planStartTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime planEndTime;
        private String remark;
        private String saleOrderNo;
        private String saleOrderItemNo;
        private String saleRemark;
        private String customerPoNo;
        private String customerProjectNo;
        private String customerOrderNo;
        private String internalProjectNo;
    }
}

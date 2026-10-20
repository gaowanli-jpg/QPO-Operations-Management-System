package com.machining.quote.dto.dashboard;

import lombok.Data;

@Data
public class DashboardCustomerMatrixRowDTO {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerShortName;
    private Long rfqUnpriced = 0L;
    private Long rfqPriced = 0L;
    private Long rfqQuoted = 0L;
    private Long u9Ready = 0L;
    private Long totalFocus = 0L;
}

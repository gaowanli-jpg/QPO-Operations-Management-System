package com.machining.quote.dto.dashboard;

import lombok.Data;

@Data
public class DashboardCustomerCountRow {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerShortName;
    private String status;
    private Long count = 0L;
}

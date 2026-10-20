package com.machining.quote.dto.dashboard;

import lombok.Data;

@Data
public class DashboardStatusCountRow {
    private String status;
    private Long count = 0L;
}

package com.machining.quote.dto.dashboard;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardOverviewDTO {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String updatedAt;
    private List<DashboardSummaryCardDTO> summaryCards = new ArrayList<>();
    private List<DashboardChartItemDTO> rfqStatusDistribution = new ArrayList<>();
    private List<DashboardChartItemDTO> u9ReadyCustomerDistribution = new ArrayList<>();
    private List<DashboardCustomerMatrixRowDTO> customerMatrix = new ArrayList<>();
    private List<DashboardTodoCardDTO> todoCards = new ArrayList<>();
    private List<DashboardAttentionItemDTO> attentionItems = new ArrayList<>();

    public static DashboardOverviewDTO empty() {
        DashboardOverviewDTO dto = new DashboardOverviewDTO();
        dto.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
        return dto;
    }
}

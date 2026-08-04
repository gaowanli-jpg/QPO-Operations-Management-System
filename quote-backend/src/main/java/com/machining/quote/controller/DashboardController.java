package com.machining.quote.controller;

import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import com.machining.quote.service.DashboardOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardOverviewService dashboardOverviewService;

    public DashboardController(DashboardOverviewService dashboardOverviewService) {
        this.dashboardOverviewService = dashboardOverviewService;
    }

    @GetMapping("/overview")
    public DashboardOverviewDTO overview() {
        return dashboardOverviewService.overview();
    }
}

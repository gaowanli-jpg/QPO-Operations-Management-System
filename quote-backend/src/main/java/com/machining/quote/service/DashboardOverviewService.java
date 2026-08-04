package com.machining.quote.service;

import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import org.springframework.stereotype.Service;

@Service
public class DashboardOverviewService {
    public DashboardOverviewDTO overview() {
        return DashboardOverviewDTO.empty();
    }
}

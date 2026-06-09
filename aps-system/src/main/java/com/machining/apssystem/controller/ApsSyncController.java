package com.machining.apssystem.controller;

import com.machining.apssystem.dto.ApsSyncResultDTO;
import com.machining.apssystem.dto.WorkItemSummaryDTO;
import com.machining.apssystem.service.ApsSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aps")
public class ApsSyncController {
    @Autowired
    private ApsSyncService apsSyncService;

    @PostMapping("/sync/erp-delivery-items")
    public ApsSyncResultDTO syncErpDeliveryItems(@RequestParam(value = "limit", required = false) Integer limit) {
        return apsSyncService.syncErpDeliveryItems(limit);
    }

    @GetMapping("/tasks/recent")
    public List<WorkItemSummaryDTO> listRecentTasks(@RequestParam(value = "limit", required = false) Integer limit) {
        return apsSyncService.listRecentTasks(limit);
    }
}

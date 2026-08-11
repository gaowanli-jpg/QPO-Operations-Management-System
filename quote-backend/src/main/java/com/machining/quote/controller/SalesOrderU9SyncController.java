package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9AssignmentReq;
import com.machining.quote.dto.SalesOrderU9AssignmentResp;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.dto.SalesOrderU9SyncReq;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9MasterDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/u9-sync")
public class SalesOrderU9SyncController {
    @Autowired
    private SalesOrderU9SyncService salesOrderU9SyncService;
    @Autowired
    private U9MasterDataService u9MasterDataService;

    @GetMapping("/options")
    @RequirePermission("market:u9-sync:view")
    public SalesOrderU9SyncOptionsResp options() {
        return u9MasterDataService.options();
    }

    @GetMapping("/orders")
    @RequirePermission("market:u9-sync:view")
    public PageResult<SalesOrderU9SyncOrderRow> listOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "u9SyncStatus", required = false) String u9SyncStatus,
            @RequestParam(value = "materialReadyStatus", required = false) String materialReadyStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderU9SyncService.listOrders(
                customerName, keyword, status, u9SyncStatus, materialReadyStatus, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/preview")
    @RequirePermission("market:u9-sync:view")
    public SalesOrderU9SyncPreviewResp preview(@PathVariable Long salesOrderId) {
        return salesOrderU9SyncService.previewSalesOrder(salesOrderId);
    }

    @PutMapping("/orders/{salesOrderId}/assignment")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9AssignmentResp saveAssignment(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderU9AssignmentReq req) {
        return salesOrderU9SyncService.saveAssignment(salesOrderId, req);
    }

    @PostMapping("/orders/{salesOrderId}/sync")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9SyncResp syncOne(@PathVariable Long salesOrderId) {
        return salesOrderU9SyncService.syncSalesOrders(List.of(salesOrderId));
    }

    @PostMapping("/sync")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9SyncResp sync(@RequestBody(required = false) SalesOrderU9SyncReq req) {
        return salesOrderU9SyncService.syncSalesOrders(req == null ? List.of() : req.getSalesOrderIds());
    }
}

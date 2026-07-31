package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SalesOrderU9SyncReq;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.service.SalesOrderU9SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Deprecated
@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderU9SyncCompatibilityController {
    @Autowired
    private SalesOrderU9SyncService salesOrderU9SyncService;

    @PostMapping("/u9-sync")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9SyncResp syncU9(@RequestBody(required = false) SalesOrderU9SyncReq req) {
        return salesOrderU9SyncService.syncSalesOrders(req == null ? null : req.getSalesOrderIds());
    }
}

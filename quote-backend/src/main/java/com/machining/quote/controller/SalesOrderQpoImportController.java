package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderU9SyncReq;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderU9SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderQpoImportController {
    @Autowired
    private SalesOrderQpoImportService salesOrderQpoImportService;
    @Autowired
    private SalesOrderU9SyncService salesOrderU9SyncService;

    @PostMapping("/import-qpo")
    @RequirePermission("market:rfq:import-sales-order")
    public SalesOrderQpoImportResp importQpo(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "documentTypeCode", required = false) String documentTypeCode,
            @RequestParam("excelFile") MultipartFile excelFile) {
        return salesOrderQpoImportService.importQpo(excelFile, customerId, documentTypeCode);
    }

    @PostMapping("/u9-sync")
    @RequirePermission("market:rfq:import-sales-order")
    public SalesOrderU9SyncResp syncU9(@RequestBody SalesOrderU9SyncReq req) {
        return salesOrderU9SyncService.syncSalesOrders(req == null ? null : req.getSalesOrderIds());
    }
}

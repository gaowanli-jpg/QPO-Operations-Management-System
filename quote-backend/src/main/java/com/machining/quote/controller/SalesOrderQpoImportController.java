package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.service.SalesOrderQpoImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderQpoImportController {
    @Autowired
    private SalesOrderQpoImportService salesOrderQpoImportService;

    @PostMapping("/import-qpo")
    @RequirePermission("market:rfq:import-sales-order")
    public SalesOrderQpoImportResp importQpo(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "documentTypeCode", required = false) String documentTypeCode,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings) {
        return salesOrderQpoImportService.importQpo(excelFile, customerId, documentTypeCode, drawings);
    }
}

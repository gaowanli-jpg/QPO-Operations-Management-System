package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.service.SalesOrderImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderImportController {
    @Autowired
    private SalesOrderImportService salesOrderImportService;

    @PostMapping("/import")
    @RequirePermission("market:rfq:import-sales-order")
    public Object importSalesOrder(
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        if (dryRun) {
            return salesOrderImportService.dryRun(excelFile, null);
        }
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "销售订单导入与U9同步将在后续阶段实现");
    }
}

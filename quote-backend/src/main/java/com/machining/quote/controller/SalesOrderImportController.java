package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.service.ImportTemplateExportService;
import com.machining.quote.service.SalesOrderImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderImportController {
    @Autowired
    private SalesOrderImportService salesOrderImportService;
    @Autowired
    private ImportTemplateExportService importTemplateExportService;

    @GetMapping("/import/template")
    @RequirePermission("market:rfq:import-sales-order")
    public void exportSalesOrderImportTemplate(HttpServletResponse response) throws IOException {
        importTemplateExportService.exportSalesOrderImportTemplate(response);
    }

    @PostMapping("/import")
    @RequirePermission("market:rfq:import-sales-order")
    public Object importSalesOrder(
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        if (dryRun) {
            return salesOrderImportService.dryRun(excelFile, null);
        }
        return salesOrderImportService.importAndSync(excelFile, null);
    }
}

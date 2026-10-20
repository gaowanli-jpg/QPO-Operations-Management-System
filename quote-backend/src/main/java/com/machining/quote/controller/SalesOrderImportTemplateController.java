package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.service.ImportTemplateExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/market/sales-orders")
public class SalesOrderImportTemplateController {
    @Autowired
    private ImportTemplateExportService importTemplateExportService;

    @GetMapping("/import/template")
    @RequirePermission("market:rfq:import-sales-order")
    public void exportSalesOrderImportTemplate(HttpServletResponse response) throws IOException {
        importTemplateExportService.exportSalesOrderImportTemplate(response);
    }
}

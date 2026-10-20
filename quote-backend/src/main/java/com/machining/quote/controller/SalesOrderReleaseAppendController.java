package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderReleaseAppendResp;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.service.SalesOrderReleaseAppendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/market/release-append")
public class SalesOrderReleaseAppendController {
    @Autowired
    private SalesOrderReleaseAppendService salesOrderReleaseAppendService;

    @GetMapping("/customers")
    @RequirePermission("market:release-append:customer-select")
    public PageResult<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderReleaseAppendService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/sales-orders")
    @RequirePermission("market:release-append:order-select")
    public PageResult<SalesOrderU9SyncOrderRow> listSalesOrders(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderReleaseAppendService.listAppendTargetSalesOrders(customerCode, customerName, keyword, page, pageSize);
    }

    @PostMapping(value = "/sales-orders/{salesOrderId}/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:release-append:create")
    public SalesOrderReleaseAppendResp appendItems(
            @PathVariable Long salesOrderId,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return salesOrderReleaseAppendService.appendItems(salesOrderId, excelFile, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
    }
}

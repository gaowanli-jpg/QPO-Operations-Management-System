package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeItemDataReplacementConfirmResp;
import com.machining.quote.dto.SalesOrderChangeItemUpdateReq;
import com.machining.quote.dto.SalesOrderChangeItemUpdateResp;
import com.machining.quote.dto.SalesOrderChangeOrderRow;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.service.SalesOrderChangeItemDataReplacementService;
import com.machining.quote.service.SalesOrderChangeItemEditService;
import com.machining.quote.service.SalesOrderChangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/change/sales-orders")
public class SalesOrderChangeController {
    @Autowired
    private SalesOrderChangeService salesOrderChangeService;
    @Autowired
    private SalesOrderChangeItemEditService salesOrderChangeItemEditService;
    @Autowired
    private SalesOrderChangeItemDataReplacementService itemDataReplacementService;

    @GetMapping("/customers")
    @RequirePermission("change:sales-order:customer-select")
    public PageResult<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderChangeService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping
    @RequirePermission("change:sales-order:view")
    public PageResult<SalesOrderChangeOrderRow> listOrders(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderChangeService.listOrders(customerCode, customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/{salesOrderId}/preview")
    @RequirePermission("change:sales-order:view")
    public SalesOrderChangePreviewResp preview(@PathVariable Long salesOrderId) {
        return salesOrderChangeService.preview(salesOrderId);
    }

    @PutMapping("/{salesOrderId}/items")
    @RequirePermission("change:sales-order:update")
    public SalesOrderChangeItemUpdateResp saveItems(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderChangeItemUpdateReq req) {
        return salesOrderChangeItemEditService.saveItems(salesOrderId, req);
    }

    @PostMapping(
            value = "/{salesOrderId}/item-data-replacement/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("change:sales-order:update")
    public SalesOrderU9ItemDataReplacementPreviewDTO previewItemDataReplacement(
            @PathVariable Long salesOrderId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        validateExcelFile(excelFile);
        try {
            return itemDataReplacementService.preview(salesOrderId, excelFile.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("销售变更表格数据替换文件读取失败", e);
        }
    }

    @PostMapping(
            value = "/{salesOrderId}/item-data-replacement/confirm",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("change:sales-order:update")
    public SalesOrderChangeItemDataReplacementConfirmResp confirmItemDataReplacement(
            @PathVariable Long salesOrderId,
            @RequestParam("previewFingerprint") String previewFingerprint,
            @RequestParam("reason") String reason,
            @RequestParam("excelFile") MultipartFile excelFile) {
        String safeFingerprint = validatePreviewFingerprint(previewFingerprint);
        String safeReason = validateReason(reason);
        validateExcelFile(excelFile);
        try {
            return itemDataReplacementService.confirm(
                    salesOrderId,
                    excelFile.getInputStream(),
                    safeFingerprint,
                    safeReason);
        } catch (IOException e) {
            throw new IllegalArgumentException("销售变更表格数据替换文件读取失败", e);
        }
    }

    private void validateExcelFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("销售变更表格数据替换文件不能为空");
        }
        String filename = excelFile.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException("销售变更表格数据替换文件仅支持 .xlsx 或 .xls");
        }
    }

    private String validatePreviewFingerprint(String previewFingerprint) {
        if (previewFingerprint == null || previewFingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("预览指纹不能为空");
        }
        return previewFingerprint.trim();
    }

    private String validateReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("变更原因不能为空");
        }
        return reason.trim();
    }
}

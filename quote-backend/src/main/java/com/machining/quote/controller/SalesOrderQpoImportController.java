package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.service.SalesOrderQpoImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
            @RequestParam(value = "salesOrderDate", required = false) String salesOrderDate,
            @RequestParam(value = "customerOrderNo", required = false) String customerOrderNo,
            @RequestParam(value = "customerProjectNo", required = false) String customerProjectNo,
            @RequestParam(value = "customerOrderTitle", required = false) String customerOrderTitle,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "internalProjectNo", required = false) String internalProjectNo,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        SalesOrderQpoImportDefaultsDTO defaults = buildDefaults(
                salesOrderDate,
                customerOrderNo,
                customerProjectNo,
                customerOrderTitle,
                orderType,
                internalProjectNo);
        return salesOrderQpoImportService.importQpo(
                excelFile,
                customerId,
                documentTypeCode,
                defaults,
                drawings,
                versionDrawings,
                drawingFolderFiles,
                drawingFolderName);
    }

    private SalesOrderQpoImportDefaultsDTO buildDefaults(
            String salesOrderDate,
            String customerOrderNo,
            String customerProjectNo,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo) {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(parseRequiredDate(salesOrderDate));
        defaults.setCustomerOrderNo(optionalText(customerOrderNo));
        defaults.setCustomerProjectNo(optionalText(customerProjectNo));
        defaults.setCustomerOrderTitle(optionalText(customerOrderTitle));
        defaults.setOrderType(requiredText(orderType, "订单类型"));
        defaults.setInternalProjectNo(optionalText(internalProjectNo));
        return defaults;
    }

    private LocalDate parseRequiredDate(String value) {
        String normalized = requiredText(value, "下单日期");
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("下单日期格式不正确，请使用 yyyy-MM-dd");
        }
    }

    private String requiredText(String value, String fieldName) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException("请填写" + fieldName);
        }
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

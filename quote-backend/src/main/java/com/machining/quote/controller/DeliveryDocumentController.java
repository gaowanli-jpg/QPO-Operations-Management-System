package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.service.DeliveryDocumentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/delivery-documents")
public class DeliveryDocumentController {
    @Autowired
    private DeliveryDocumentService deliveryDocumentService;

    @GetMapping("/customers")
    @RequirePermission("market:delivery-document:customer-select")
    public List<CustomerOptionDTO> listDocumentCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return deliveryDocumentService.listDocumentCustomers(field, keyword);
    }

    @GetMapping("/orders")
    @RequirePermission("market:delivery-document:order-select")
    public PageResult<Map<String, Object>> listDocumentOrders(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryDocumentService.listDocumentOrders(customerCode, customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/summary/{sourceOrderId}")
    @RequirePermission("market:delivery-document:view")
    public Map<String, Object> documentSummary(
            @PathVariable Long sourceOrderId,
            @RequestParam(value = "sourceType", required = false) String sourceType) {
        return deliveryDocumentService.documentSummary(sourceType, sourceOrderId);
    }

    @GetMapping("/tickets/{sourceOrderId}")
    @RequirePermission("market:delivery-document:view")
    public PageResult<OrderDeliveryTicket> pageTickets(
            @PathVariable Long sourceOrderId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryDocumentService.pageTickets(sourceType, sourceOrderId, page, pageSize);
    }

    @GetMapping("/process-sheets/{sourceOrderId}")
    @RequirePermission("market:delivery-document:view")
    public PageResult<ProcessSheetDTO> pageProcessSheets(
            @PathVariable Long sourceOrderId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryDocumentService.pageProcessSheets(sourceType, sourceOrderId, page, pageSize);
    }

    @GetMapping("/export/tickets/{sourceOrderId}")
    @RequirePermission("market:delivery-document:export")
    public void exportTickets(
            @PathVariable Long sourceOrderId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            HttpServletResponse response) throws IOException {
        deliveryDocumentService.exportTickets(sourceType, sourceOrderId, response);
    }

    @GetMapping("/export/process-sheets/{sourceOrderId}")
    @RequirePermission("market:delivery-document:export")
    public void exportProcessSheets(
            @PathVariable Long sourceOrderId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            HttpServletResponse response) throws IOException {
        deliveryDocumentService.exportProcessSheets(sourceType, sourceOrderId, response);
    }
}

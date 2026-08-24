package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeItemUpdateReq;
import com.machining.quote.dto.SalesOrderChangeItemUpdateResp;
import com.machining.quote.dto.SalesOrderChangeOrderRow;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.service.SalesOrderChangeItemEditService;
import com.machining.quote.service.SalesOrderChangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/change/sales-orders")
public class SalesOrderChangeController {
    @Autowired
    private SalesOrderChangeService salesOrderChangeService;
    @Autowired
    private SalesOrderChangeItemEditService salesOrderChangeItemEditService;

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
}

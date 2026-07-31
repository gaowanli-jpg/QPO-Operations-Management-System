package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderMatchApplyReq;
import com.machining.quote.dto.SalesOrderMatchApplyResp;
import com.machining.quote.dto.SalesOrderMatchItemRow;
import com.machining.quote.dto.SalesOrderMatchOrderRow;
import com.machining.quote.dto.SalesOrderMatchPreviewReq;
import com.machining.quote.dto.SalesOrderMatchPreviewResp;
import com.machining.quote.service.SalesOrderMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/sales-match")
public class SalesOrderMatchController {
    @Autowired
    private SalesOrderMatchService salesOrderMatchService;

    @GetMapping("/customers")
    @RequirePermission("market:sales-match:customer-select")
    public List<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return salesOrderMatchService.listCustomerOptions(field, keyword);
    }

    @GetMapping("/orders")
    @RequirePermission("market:sales-match:order-select")
    public PageResult<SalesOrderMatchOrderRow> listOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "matchStatus", required = false) String matchStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderMatchService.listOrders(customerName, keyword, status, matchStatus, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/items")
    @RequirePermission("market:sales-match:view")
    public PageResult<SalesOrderMatchItemRow> listItems(
            @PathVariable Long salesOrderId,
            @RequestParam(value = "matchStatus", required = false) String matchStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderMatchService.listItems(salesOrderId, matchStatus, page, pageSize);
    }

    @PostMapping("/preview")
    @RequirePermission("market:sales-match:view")
    public SalesOrderMatchPreviewResp preview(@RequestBody(required = false) SalesOrderMatchPreviewReq req) {
        return salesOrderMatchService.preview(req == null ? new SalesOrderMatchPreviewReq() : req);
    }

    @PostMapping("/apply")
    @RequirePermission("market:sales-match:apply")
    public SalesOrderMatchApplyResp apply(@RequestBody(required = false) SalesOrderMatchApplyReq req) {
        return salesOrderMatchService.apply(req == null ? new SalesOrderMatchApplyReq() : req);
    }
}

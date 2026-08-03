package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.service.SalesOrderManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/sales-order-management")
public class SalesOrderManageController {
    @Autowired
    private SalesOrderManageService salesOrderManageService;

    @GetMapping("/customers")
    @RequirePermission("market:sales-order:customer-select")
    public List<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return salesOrderManageService.listCustomerOptions(field, keyword);
    }

    @GetMapping("/orders")
    @RequirePermission("market:sales-order:view")
    public PageResult<Map<String, Object>> listOrders(
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.listOrderSummaries(customerId, customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}")
    @RequirePermission("market:sales-order:view")
    public Map<String, Object> getOrderDetail(@PathVariable Long salesOrderId) {
        return salesOrderManageService.getOrderDetail(salesOrderId);
    }

    @GetMapping("/orders/{salesOrderId}/items")
    @RequirePermission("market:sales-order:view")
    public PageResult<SalesOrderItem> listOrderItems(
            @PathVariable Long salesOrderId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.pageOrderItems(salesOrderId, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/tickets")
    @RequirePermission("market:sales-order:view")
    public PageResult<OrderDeliveryTicket> listOrderTickets(
            @PathVariable Long salesOrderId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.pageOrderTickets(salesOrderId, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/process-sheets")
    @RequirePermission("market:sales-order:view")
    public PageResult<ProcessSheetDTO> listDynamicProcessSheets(
            @PathVariable Long salesOrderId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.pageDynamicProcessSheets(salesOrderId, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/drawing-summary")
    @RequirePermission("market:sales-order:view")
    public Map<String, Object> getDrawingSummary(@PathVariable Long salesOrderId) {
        return salesOrderManageService.getDrawingSummary(salesOrderId);
    }

    @PostMapping("/orders/{salesOrderId}/discard")
    @RequirePermission("market:sales-order:discard")
    public OrderStatusChangeResult discard(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderManageService.discard(salesOrderId, req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{salesOrderId}/restore")
    @RequirePermission("market:sales-order:restore")
    public OrderStatusChangeResult restore(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderManageService.restore(salesOrderId, req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping(value = "/orders/{salesOrderId}/supplement-drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:sales-order:supplement-drawing")
    public Map<String, Object> supplementOrderDrawings(
            @PathVariable Long salesOrderId,
            @RequestParam("drawings") List<MultipartFile> drawings) {
        return salesOrderManageService.supplementOrderDrawings(salesOrderId, drawings);
    }
}

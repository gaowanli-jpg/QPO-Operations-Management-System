package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.OrderStatusManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order-management")
public class OrderManagementController {
    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private OrderStatusManagementService orderStatusManagementService;

    @GetMapping("/orders")
    @RequirePermission("market:order:view")
    public PageResult<Map<String, Object>> listOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.listOrderSummaries(customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/orders/{rfqId}")
    @RequirePermission("market:order:view")
    public Map<String, Object> getOrderDetail(@PathVariable Integer rfqId) {
        return orderManagementService.getOrderDetail(rfqId);
    }

    @GetMapping("/orders/{rfqId}/items")
    @RequirePermission("market:order:view")
    public PageResult<ItemQuoteVersion> listOrderItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageOrderItems(rfqId, page, pageSize);
    }

    @GetMapping("/orders/{rfqId}/tickets")
    @RequirePermission("market:order:view")
    public PageResult<OrderDeliveryTicket> listOrderTickets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageOrderTickets(rfqId, page, pageSize);
    }

    @GetMapping("/orders/{rfqId}/process-sheets")
    @RequirePermission("market:order:view")
    public PageResult<ProcessSheetDTO> listDynamicProcessSheets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageDynamicProcessSheets(rfqId, page, pageSize);
    }

    @GetMapping("/orders/{rfqId}/drawing-summary")
    @RequirePermission("market:order:view")
    public Map<String, Object> getDrawingSummary(@PathVariable Integer rfqId) {
        return orderManagementService.getDrawingSummary(rfqId);
    }

    @GetMapping("/customers")
    @RequirePermission("market:order:customer-select")
    public List<String> listCustomers() {
        return orderManagementService.listCustomers();
    }

    @GetMapping("/history-orders/customers")
    @RequirePermission("market:history-order:customer-select")
    public List<String> listHistoryCustomers() {
        return orderManagementService.listCustomers();
    }

    @PostMapping(value = "/orders/{rfqId}/supplement-drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:supplement-drawing")
    public Map<String, Object> supplementDrawings(
            @PathVariable Integer rfqId,
            @RequestParam("drawings") List<MultipartFile> drawings) {
        return orderManagementService.supplementDrawings(rfqId, drawings);
    }

    @DeleteMapping("/orders/{rfqId}")
    @RequirePermission("market:order:delete")
    public Map<String, Object> deleteOrder(
            @PathVariable Integer rfqId,
            @RequestParam(value = "deleteFiles", defaultValue = "true") boolean deleteFiles) {
        orderManagementService.deleteOrderCompletely(rfqId, deleteFiles);
        return Map.of("success", true, "message", "订单及关联数据已彻底删除");
    }

    @PostMapping("/orders/{rfqId}/rollback-quoted")
    @RequirePermission("market:order:rollback-quoted")
    public OrderStatusChangeResult rollbackQuoted(
            @PathVariable Integer rfqId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return orderStatusManagementService.rollbackQuoted(rfqId, req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{rfqId}/rollback-priced")
    @RequirePermission("market:order:rollback-priced")
    public OrderStatusChangeResult rollbackPriced(
            @PathVariable Integer rfqId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return orderStatusManagementService.rollbackPriced(rfqId, req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{rfqId}/discard")
    @RequirePermission("market:order:discard")
    public OrderStatusChangeResult discard(
            @PathVariable Integer rfqId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return orderStatusManagementService.discard(rfqId, req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{rfqId}/restore")
    @RequirePermission("market:order:restore")
    public OrderStatusChangeResult restore(
            @PathVariable Integer rfqId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return orderStatusManagementService.restore(rfqId, req == null ? new OrderStatusChangeReq() : req);
    }
}

package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.service.OrderManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequireRole({"MARKET", "ENGINEER", "WAREHOUSE", "ADMIN"})
@RequestMapping("/api/order-management")
public class OrderManagementController {
    @Autowired
    private OrderManagementService orderManagementService;

    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders(
            @RequestParam(value = "customerName", required = false) String customerName) {
        return orderManagementService.listOrderSummaries(customerName);
    }

    @GetMapping("/orders/{rfqId}")
    public Map<String, Object> getOrderDetail(@PathVariable Integer rfqId) {
        return orderManagementService.getOrderDetail(rfqId);
    }

    @GetMapping("/customers")
    public List<String> listCustomers() {
        return orderManagementService.listCustomers();
    }

    @PostMapping("/orders/{rfqId}/reset-production")
    public Map<String, Object> resetProductionData(@PathVariable Integer rfqId) {
        orderManagementService.resetProductionData(rfqId);
        return Map.of("success", true, "message", "订单提单和工艺表已重置");
    }

    @PostMapping(value = "/orders/{rfqId}/supplement-drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> supplementDrawings(
            @PathVariable Integer rfqId,
            @RequestParam("drawings") List<MultipartFile> drawings) {
        return orderManagementService.supplementDrawings(rfqId, drawings);
    }

    @DeleteMapping("/orders/{rfqId}")
    @RequireRole({"ADMIN"})
    public Map<String, Object> deleteOrder(
            @PathVariable Integer rfqId,
            @RequestParam(value = "deleteFiles", defaultValue = "true") boolean deleteFiles) {
        orderManagementService.deleteOrderCompletely(rfqId, deleteFiles);
        return Map.of("success", true, "message", "订单及关联数据已彻底删除，库存不会回滚");
    }
}

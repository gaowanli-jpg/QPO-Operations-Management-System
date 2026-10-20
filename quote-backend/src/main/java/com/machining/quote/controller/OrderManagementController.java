package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderItemMaintenanceUpdateReq;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.OrderStatusManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.listOrderSummaries(customerCode, customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/orders/{rfqId}")
    @RequirePermission("market:order:view")
    public Map<String, Object> getOrderDetail(@PathVariable Integer rfqId) {
        return orderManagementService.getOrderDetail(rfqId);
    }

    @GetMapping("/orders/{rfqId}/items")
    @RequirePermission("market:order:view")
    public PageResult<Map<String, Object>> listOrderItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageOrderItemRows(rfqId, page, pageSize);
    }

    @PostMapping("/orders/{rfqId}/items/batch")
    @RequirePermission("market:order:update-item")
    public Map<String, Object> saveOrderItems(
            @PathVariable Integer rfqId,
            @RequestBody OrderItemMaintenanceUpdateReq req) {
        return orderManagementService.saveOrderItems(rfqId, req);
    }

    @PostMapping(value = "/orders/{rfqId}/drawings/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:update-item")
    public Map<String, Object> replaceDrawings(
            @PathVariable Integer rfqId,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return orderManagementService.replaceDrawings(rfqId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
    }

    @PostMapping("/orders/{rfqId}/items/{itemId}/discard")
    @RequirePermission("market:order:update-item")
    public Map<String, Object> discardOrderItem(
            @PathVariable Integer rfqId,
            @PathVariable Integer itemId) {
        return orderManagementService.discardOrderItem(rfqId, itemId);
    }

    @PostMapping("/orders/{rfqId}/items/{itemId}/restore")
    @RequirePermission("market:order:update-item")
    public Map<String, Object> restoreOrderItem(
            @PathVariable Integer rfqId,
            @PathVariable Integer itemId) {
        return orderManagementService.restoreOrderItem(rfqId, itemId);
    }

    @PostMapping(value = "/orders/{rfqId}/items/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:update-item")
    public Map<String, Object> appendOrderItems(
            @PathVariable Integer rfqId,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return orderManagementService.appendOrderItems(rfqId, excelFile, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
    }

    @PostMapping(value = "/orders/{rfqId}/item-data-replacement/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:update-item")
    public Map<String, Object> previewItemDataReplacement(
            @PathVariable Integer rfqId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        return orderManagementService.previewItemDataReplacement(rfqId, excelFile);
    }

    @PostMapping(value = "/orders/{rfqId}/item-data-replacement/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:update-item")
    public Map<String, Object> confirmItemDataReplacement(
            @PathVariable Integer rfqId,
            @RequestParam("previewFingerprint") String previewFingerprint,
            @RequestParam("excelFile") MultipartFile excelFile) {
        return orderManagementService.confirmItemDataReplacement(rfqId, excelFile, previewFingerprint);
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
    public PageResult<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @PostMapping(value = "/orders/{rfqId}/supplement-drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:order:supplement-drawing")
    public Map<String, Object> supplementDrawings(
            @PathVariable Integer rfqId,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return orderManagementService.supplementDrawings(rfqId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
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

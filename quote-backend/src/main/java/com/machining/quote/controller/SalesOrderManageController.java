package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.SalesOrderItemDataReplacementConfirmResp;
import com.machining.quote.dto.SalesOrderItemFieldReplacementReq;
import com.machining.quote.dto.SalesOrderItemUpdateReq;
import com.machining.quote.dto.SalesOrderLinkedDiscardResult;
import com.machining.quote.dto.SalesOrderMaintenanceOrderUpdateReq;
import com.machining.quote.dto.SalesOrderMaintenanceUpdateResp;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ResyncResetResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.service.SalesOrderItemDataReplacementService;
import com.machining.quote.service.SalesOrderItemDetailExportService;
import com.machining.quote.service.SalesOrderLinkedDiscardService;
import com.machining.quote.service.SalesOrderManageService;
import com.machining.quote.service.SalesOrderMaintenanceService;
import com.machining.quote.service.SalesOrderProductionTaskNoticeExportService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/sales-order-management")
public class SalesOrderManageController {
    @Autowired
    private SalesOrderManageService salesOrderManageService;
    @Autowired
    private SalesOrderProductionTaskNoticeExportService productionTaskNoticeExportService;
    @Autowired
    private SalesOrderItemDetailExportService itemDetailExportService;
    @Autowired
    private SalesOrderMaintenanceService salesOrderMaintenanceService;
    @Autowired
    private SalesOrderItemDataReplacementService itemDataReplacementService;
    @Autowired
    private SalesOrderU9SyncStateService salesOrderU9SyncStateService;
    @Autowired
    private SalesOrderLinkedDiscardService salesOrderLinkedDiscardService;

    @GetMapping("/customers")
    @RequirePermission("market:sales-order:customer-select")
    public PageResult<CustomerOptionDTO> listCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    @GetMapping("/orders")
    @RequirePermission("market:sales-order:view")
    public PageResult<Map<String, Object>> listOrders(
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderManageService.listOrderSummaries(customerId, customerCode, customerName, keyword, status, page, pageSize);
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

    @GetMapping("/orders/{salesOrderId}/items/export")
    @RequirePermission("market:sales-order:item-detail-export")
    public void exportItemDetails(@PathVariable Long salesOrderId, HttpServletResponse response) throws IOException {
        SalesOrderItemDetailExportService.ExportFile exportFile = itemDetailExportService.export(salesOrderId);
        writeXlsxDownload(response, exportFile.fileName(), exportFile.bytes());
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

    @PutMapping("/orders/{salesOrderId}")
    @RequirePermission("market:sales-order:update")
    public SalesOrderMaintenanceUpdateResp saveOrder(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderMaintenanceOrderUpdateReq req) {
        return salesOrderMaintenanceService.saveOrder(salesOrderId, req);
    }

    @PostMapping("/orders/{salesOrderId}/items/batch")
    @RequirePermission("market:sales-order:update")
    public SalesOrderMaintenanceUpdateResp saveItems(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderItemUpdateReq req) {
        return salesOrderMaintenanceService.saveItems(salesOrderId, req);
    }

    @PostMapping("/orders/{salesOrderId}/items/field-replacement")
    @RequirePermission("market:sales-order:update")
    public SalesOrderMaintenanceUpdateResp replaceItemField(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderItemFieldReplacementReq req) {
        return salesOrderMaintenanceService.replaceItemField(salesOrderId, req);
    }

    @PostMapping("/orders/{salesOrderId}/items/convert-untaxed-to-tax-included")
    @RequirePermission("market:sales-order:update")
    public SalesOrderMaintenanceUpdateResp convertUntaxedToTaxIncluded(@PathVariable Long salesOrderId) {
        return salesOrderMaintenanceService.convertUntaxedToTaxIncluded(salesOrderId);
    }

    @PostMapping("/orders/{salesOrderId}/items/convert-tax-included-to-untaxed")
    @RequirePermission("market:sales-order:update")
    public SalesOrderMaintenanceUpdateResp convertTaxIncludedToUntaxed(@PathVariable Long salesOrderId) {
        return salesOrderMaintenanceService.convertTaxIncludedToUntaxed(salesOrderId);
    }

    @PostMapping("/orders/{salesOrderId}/u9-resync-reset")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9ResyncResetResp resetU9SyncStatus(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderU9SyncStateService.resetForResync(
                salesOrderId,
                req == null ? null : req.getReason());
    }

    @PostMapping("/orders/{salesOrderId}/items/{itemId}/discard")
    @RequirePermission("market:sales-order:update")
    public OrderStatusChangeResult discardItemForU9Change(
            @PathVariable Long salesOrderId,
            @PathVariable Long itemId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderManageService.discardItemForU9Change(
                salesOrderId,
                itemId,
                req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{salesOrderId}/items/{itemId}/restore")
    @RequirePermission("market:sales-order:update")
    public OrderStatusChangeResult restoreItemForU9Change(
            @PathVariable Long salesOrderId,
            @PathVariable Long itemId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderManageService.restoreItemForU9Change(
                salesOrderId,
                itemId,
                req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{salesOrderId}/items/{itemId}/linked-discard")
    @RequirePermission("market:sales-order:update")
    public SalesOrderLinkedDiscardResult discardItemLinked(
            @PathVariable Long salesOrderId,
            @PathVariable Long itemId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderLinkedDiscardService.discard(
                salesOrderId,
                itemId,
                req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping("/orders/{salesOrderId}/items/{itemId}/linked-restore")
    @RequirePermission("market:sales-order:update")
    public SalesOrderLinkedDiscardResult restoreItemLinked(
            @PathVariable Long salesOrderId,
            @PathVariable Long itemId,
            @RequestBody(required = false) OrderStatusChangeReq req) {
        return salesOrderLinkedDiscardService.restore(
                salesOrderId,
                itemId,
                req == null ? new OrderStatusChangeReq() : req);
    }

    @PostMapping(
            value = "/orders/{salesOrderId}/items/data-replacement/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:sales-order:update")
    public SalesOrderU9ItemDataReplacementPreviewDTO previewItemDataReplacement(
            @PathVariable Long salesOrderId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        validateExcelFile(excelFile);
        try {
            return itemDataReplacementService.preview(salesOrderId, excelFile.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("销售订单表格数据替换文件读取失败", e);
        }
    }

    @PostMapping(
            value = "/orders/{salesOrderId}/items/data-replacement/confirm",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:sales-order:update")
    public SalesOrderItemDataReplacementConfirmResp confirmItemDataReplacement(
            @PathVariable Long salesOrderId,
            @RequestParam("previewFingerprint") String previewFingerprint,
            @RequestParam("excelFile") MultipartFile excelFile) {
        String safeFingerprint = validatePreviewFingerprint(previewFingerprint);
        validateExcelFile(excelFile);
        try {
            return itemDataReplacementService.confirm(salesOrderId, excelFile.getInputStream(), safeFingerprint);
        } catch (IOException e) {
            throw new IllegalArgumentException("销售订单表格数据替换文件读取失败", e);
        }
    }

    @GetMapping("/orders/{salesOrderId}/production-task-notice/export")
    @RequirePermission("market:sales-order:production-task-notice-export")
    public void exportProductionTaskNotice(@PathVariable Long salesOrderId, HttpServletResponse response) throws IOException {
        byte[] bytes = productionTaskNoticeExportService.export(salesOrderId);
        String fileName = salesOrderId + "-生产任务通知单.xlsx";
        writeXlsxDownload(response, fileName, bytes);
    }

    private void writeXlsxDownload(HttpServletResponse response, String fileName, byte[] bytes) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        response.getOutputStream().write(bytes);
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
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return salesOrderManageService.supplementOrderDrawings(salesOrderId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
    }

    @PostMapping(value = "/orders/{salesOrderId}/drawings/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:sales-order:supplement-drawing")
    public Map<String, Object> replaceOrderDrawings(
            @PathVariable Long salesOrderId,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings,
            @RequestParam(value = "versionDrawings", required = false) List<MultipartFile> versionDrawings,
            @RequestParam(value = "drawingFolderFiles", required = false) List<MultipartFile> drawingFolderFiles,
            @RequestParam(value = "drawingFolderName", required = false) String drawingFolderName) {
        return salesOrderManageService.replaceOrderDrawings(salesOrderId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
    }

    private void validateExcelFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("销售订单表格数据替换文件不能为空");
        }
        String filename = excelFile.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException("销售订单表格数据替换文件仅支持 .xlsx 或 .xls");
        }
    }

    private String validatePreviewFingerprint(String previewFingerprint) {
        if (previewFingerprint == null || previewFingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("预览指纹不能为空");
        }
        return previewFingerprint.trim();
    }
}

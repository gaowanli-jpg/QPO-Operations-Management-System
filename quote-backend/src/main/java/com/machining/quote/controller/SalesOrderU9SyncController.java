package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9AssignmentReq;
import com.machining.quote.dto.SalesOrderU9AssignmentResp;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ItemUpdateReq;
import com.machining.quote.dto.SalesOrderU9ItemUpdateResp;
import com.machining.quote.dto.SalesOrderU9ReconcileResp;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.dto.SalesOrderU9SyncReq;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementService;
import com.machining.quote.service.SalesOrderU9ItemEditService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9MasterDataService;
import com.machining.quote.service.u9.U9MasterDataSyncService;
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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market/u9-sync")
public class SalesOrderU9SyncController {
    @Autowired
    private SalesOrderU9SyncService salesOrderU9SyncService;
    @Autowired
    private SalesOrderU9ItemEditService salesOrderU9ItemEditService;
    @Autowired
    private SalesOrderU9ItemDataReplacementService itemDataReplacementService;
    @Autowired
    private U9MasterDataService u9MasterDataService;
    @Autowired
    private U9MasterDataSyncService u9MasterDataSyncService;
    @Autowired
    private AuditService auditService;

    @GetMapping("/options")
    @RequirePermission("market:u9-sync:view")
    public SalesOrderU9SyncOptionsResp options() {
        return u9MasterDataService.options();
    }

    @PostMapping("/options/refresh")
    @RequirePermission("market:u9-sync:submit")
    public Map<String, Object> refreshOptions() {
        U9MasterDataSyncService.SyncResult result = u9MasterDataSyncService.syncOptionsManual(currentUserId());
        Map<String, Object> summary = syncSummary(result);
        auditService.record(
                "U9_SYNC_OPTIONS_REFRESH",
                "u9_master_data_sync_run",
                result.syncRunId(),
                null,
                summary,
                "MARKET",
                result.success() ? "SUCCESS" : "FAILED",
                "刷新 U9 同步页选项缓存",
                result.success() ? null : result.message()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("message", result.success() ? "U9同步页选项缓存已刷新" : result.message());
        response.putAll(summary);
        return response;
    }

    @GetMapping("/orders")
    @RequirePermission("market:u9-sync:view")
    public PageResult<SalesOrderU9SyncOrderRow> listOrders(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "u9SyncStatus", required = false) String u9SyncStatus,
            @RequestParam(value = "materialReadyStatus", required = false) String materialReadyStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return salesOrderU9SyncService.listOrders(
                customerCode, customerName, keyword, status, u9SyncStatus, materialReadyStatus, page, pageSize);
    }

    @GetMapping("/orders/{salesOrderId}/preview")
    @RequirePermission("market:u9-sync:view")
    public SalesOrderU9SyncPreviewResp preview(@PathVariable Long salesOrderId) {
        return salesOrderU9SyncService.previewSalesOrder(salesOrderId);
    }

    @PutMapping("/orders/{salesOrderId}/assignment")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9AssignmentResp saveAssignment(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderU9AssignmentReq req) {
        return salesOrderU9SyncService.saveAssignment(salesOrderId, req);
    }

    @PutMapping("/orders/{salesOrderId}/items")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9ItemUpdateResp saveItems(
            @PathVariable Long salesOrderId,
            @RequestBody(required = false) SalesOrderU9ItemUpdateReq req) {
        return salesOrderU9ItemEditService.saveItems(salesOrderId, req);
    }

    @PostMapping(
            value = "/orders/{salesOrderId}/item-data-replacement/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9ItemDataReplacementPreviewDTO previewItemDataReplacement(
            @PathVariable Long salesOrderId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        validateExcelFile(excelFile);
        try {
            return itemDataReplacementService.preview(salesOrderId, excelFile.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("U9同步表格数据替换文件读取失败", e);
        }
    }

    @PostMapping("/orders/{salesOrderId}/sync")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9SyncResp syncOne(@PathVariable Long salesOrderId) {
        return salesOrderU9SyncService.syncSalesOrders(List.of(salesOrderId));
    }

    @PostMapping("/orders/{salesOrderId}/reconcile")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9ReconcileResp reconcile(@PathVariable Long salesOrderId) {
        return salesOrderU9SyncService.reconcileSalesOrder(salesOrderId);
    }

    @PostMapping("/sync")
    @RequirePermission("market:u9-sync:submit")
    public SalesOrderU9SyncResp sync(@RequestBody(required = false) SalesOrderU9SyncReq req) {
        return salesOrderU9SyncService.syncSalesOrders(req == null ? List.of() : req.getSalesOrderIds());
    }

    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }

    private Map<String, Object> syncSummary(U9MasterDataSyncService.SyncResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("syncRunId", result.syncRunId());
        summary.put("syncType", "U9_SYNC_OPTIONS");
        summary.put("status", result.success() ? "SUCCESS" : "FAILED");
        summary.put("fetchedCount", result.fetchedCount());
        summary.put("upsertedCount", result.upsertedCount());
        summary.put("disabledCount", result.disabledCount());
        if (result.message() != null && !result.message().isBlank()) {
            summary.put("safeMessage", result.message());
        }
        return summary;
    }

    private void validateExcelFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("U9同步表格数据替换文件不能为空");
        }
        String filename = excelFile.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException("U9同步表格数据替换文件仅支持 .xlsx 或 .xls");
        }
    }
}

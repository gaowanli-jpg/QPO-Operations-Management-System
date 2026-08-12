package com.machining.quote.controller;

import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.U9CustomerSyncScheduleConfigDTO;
import com.machining.quote.dto.U9CustomerSyncScheduleConfigReq;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.CustomerMasterService;
import com.machining.quote.service.u9.U9CustomerSyncScheduleConfigService;
import com.machining.quote.service.u9.U9MasterDataSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/customers")
public class CustomerMasterController {
    @Autowired
    private CustomerMasterService customerMasterService;
    @Autowired
    private U9MasterDataSyncService u9MasterDataSyncService;
    @Autowired
    private U9CustomerSyncScheduleConfigService u9CustomerSyncScheduleConfigService;
    @Autowired
    private AuditService auditService;

    @GetMapping
    @RequirePermission("system:customer:view")
    public PageResult<CustomerMasterDTO> pageCustomers(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return customerMasterService.pageCustomers(field, keyword, page, pageSize);
    }

    @GetMapping("/options")
    @RequirePermission("system:customer:view")
    public List<CustomerOptionDTO> listOptions(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return customerMasterService.listOptions(field, keyword);
    }

    @PostMapping("/sync")
    @RequirePermission("system:customer:sync")
    public Map<String, Object> syncCustomers() {
        U9MasterDataSyncService.SyncResult result = u9MasterDataSyncService.syncCustomersManual(currentUserId());
        Map<String, Object> summary = syncSummary(result);
        auditService.record(
                "U9_CUSTOMER_SYNC",
                "u9_master_data_sync_run",
                result.syncRunId(),
                null,
                summary,
                "SYSTEM",
                result.success() ? "SUCCESS" : "FAILED",
                "同步 U9 客户主数据",
                result.success() ? null : result.message()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("message", result.success() ? "U9 客户同步完成" : result.message());
        response.putAll(summary);
        return response;
    }

    @GetMapping("/sync-schedule")
    @RequirePermission("system:customer:sync")
    public U9CustomerSyncScheduleConfigDTO customerSyncSchedule() {
        return u9CustomerSyncScheduleConfigService.getSetting();
    }

    @PutMapping("/sync-schedule")
    @RequirePermission("system:customer:sync")
    public U9CustomerSyncScheduleConfigDTO updateCustomerSyncSchedule(@RequestBody U9CustomerSyncScheduleConfigReq req) {
        return u9CustomerSyncScheduleConfigService.updateSetting(req);
    }

    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }

    private Map<String, Object> syncSummary(U9MasterDataSyncService.SyncResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("syncRunId", result.syncRunId());
        summary.put("syncType", "CUSTOMER");
        summary.put("status", result.success() ? "SUCCESS" : "FAILED");
        summary.put("fetchedCount", result.fetchedCount());
        summary.put("upsertedCount", result.upsertedCount());
        summary.put("disabledCount", result.disabledCount());
        if (result.message() != null && !result.message().isBlank()) {
            summary.put("safeMessage", result.message());
        }
        return summary;
    }
}

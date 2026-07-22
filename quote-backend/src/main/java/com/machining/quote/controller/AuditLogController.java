package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.AuditLogDTO;
import com.machining.quote.dto.AuditLogQueryReq;
import com.machining.quote.dto.AuditLogRetentionConfigDTO;
import com.machining.quote.dto.AuditLogRetentionConfigReq;
import com.machining.quote.dto.AuditLogRetentionRunDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.AuditLogQueryService;
import com.machining.quote.service.AuditLogRetentionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {
    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @Autowired
    private AuditLogRetentionService auditLogRetentionService;

    @GetMapping
    @RequirePermission("system:audit:view")
    public PageResult<AuditLogDTO> listLogs(@ModelAttribute AuditLogQueryReq req) {
        return auditLogQueryService.listLogs(req);
    }

    @GetMapping("/export")
    @RequirePermission("system:audit:export")
    public void export(@ModelAttribute AuditLogQueryReq req, HttpServletResponse response) throws IOException {
        byte[] csvBytes = auditLogQueryService.exportCsv(req);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''audit-logs.csv");
        response.setHeader("X-Export-Limit", String.valueOf(AuditLogQueryService.EXPORT_LIMIT));
        response.getOutputStream().write(csvBytes);
    }

    @GetMapping("/retention")
    @RequirePermission("system:audit:retention")
    public AuditLogRetentionConfigDTO retentionSetting() {
        return auditLogRetentionService.getSetting();
    }

    @PutMapping("/retention")
    @RequirePermission("system:audit:retention")
    public Map<String, Object> updateRetentionSetting(@RequestBody AuditLogRetentionConfigReq req) {
        return Map.of("success", true, "message", "操作日志保留策略已更新", "data", auditLogRetentionService.updateSetting(req));
    }

    @PostMapping("/retention/run")
    @RequirePermission("system:audit:retention")
    public Map<String, Object> runRetentionCleanup() {
        AuditLogRetentionRunDTO result = auditLogRetentionService.runCleanupNow();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", result.getMessage());
        response.put("data", result);
        return response;
    }

    @GetMapping("/{id}")
    @RequirePermission("system:audit:view")
    public AuditLogDTO detail(@PathVariable Integer id) {
        return auditLogQueryService.detail(id);
    }

    @GetMapping("/options")
    @RequirePermission("system:audit:view")
    public Map<String, Object> options() {
        return auditLogQueryService.options();
    }
}

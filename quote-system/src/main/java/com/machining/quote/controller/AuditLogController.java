package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.dto.AuditLogDTO;
import com.machining.quote.dto.AuditLogQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.AuditLogQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequireRole({"ADMIN"})
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {
    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @GetMapping
    public PageResult<AuditLogDTO> listLogs(@ModelAttribute AuditLogQueryReq req) {
        return auditLogQueryService.listLogs(req);
    }

    @GetMapping("/{id}")
    public AuditLogDTO detail(@PathVariable Integer id) {
        return auditLogQueryService.detail(id);
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return auditLogQueryService.options();
    }
}

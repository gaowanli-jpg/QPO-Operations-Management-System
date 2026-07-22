package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemDeptDTO;
import com.machining.quote.dto.SystemDeptSaveReq;
import com.machining.quote.service.SystemDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/departments")
public class SystemDeptController {
    @Autowired
    private SystemDeptService systemDeptService;

    @GetMapping("/tree")
    @RequirePermission("system:dept:view")
    public List<SystemDeptDTO> listTree() {
        return systemDeptService.listDeptTree();
    }

    @GetMapping("/options")
    @RequirePermission("system:user:view")
    public List<SystemDeptDTO> listOptions() {
        return systemDeptService.listDeptOptions();
    }

    @PostMapping
    @RequirePermission("system:dept:manage")
    public Map<String, Object> createDept(@RequestBody SystemDeptSaveReq req) {
        return Map.of("success", true, "message", "Department created", "data", systemDeptService.saveDept(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:dept:manage")
    public Map<String, Object> updateDept(@PathVariable Long id, @RequestBody SystemDeptSaveReq req) {
        req.setId(id);
        return Map.of("success", true, "message", "Department updated", "data", systemDeptService.saveDept(req));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("system:dept:manage")
    public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody SystemDeptSaveReq req) {
        return Map.of("success", true, "message", "Department status updated", "data", systemDeptService.updateStatus(id, req.getEnabled()));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:dept:manage")
    public Map<String, Object> deleteDept(@PathVariable Long id) {
        systemDeptService.deleteDept(id);
        return Map.of("success", true, "message", "Department deleted");
    }
}

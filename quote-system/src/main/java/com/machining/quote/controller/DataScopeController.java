package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.service.DataScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/data-scope")
public class DataScopeController {
    @Autowired
    private DataScopeService dataScopeService;

    @GetMapping("/departments")
    @RequirePermission("system:role:view")
    public List<Map<String, Object>> listDepartments() {
        return dataScopeService.listDepartmentTree();
    }

    @GetMapping("/customers")
    @RequirePermission("system:role:view")
    public List<String> listCustomers() {
        return dataScopeService.listCustomerOptions();
    }
}
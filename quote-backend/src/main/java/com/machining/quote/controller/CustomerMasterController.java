package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerMasterSaveReq;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.service.CustomerMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/customers")
public class CustomerMasterController {
    @Autowired
    private CustomerMasterService customerMasterService;

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

    @PostMapping
    @RequirePermission("system:customer:create")
    public Map<String, Object> createCustomer(@RequestBody CustomerMasterSaveReq req) {
        return Map.of("success", true, "message", "Customer created", "data", customerMasterService.saveCustomer(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:customer:update")
    public Map<String, Object> updateCustomer(@PathVariable Long id, @RequestBody CustomerMasterSaveReq req) {
        if (req == null) {
            req = new CustomerMasterSaveReq();
        }
        req.setId(id);
        return Map.of("success", true, "message", "Customer updated", "data", customerMasterService.saveCustomer(req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:customer:delete")
    public Map<String, Object> deleteCustomer(@PathVariable Long id) {
        customerMasterService.deleteCustomer(id);
        return Map.of("success", true, "message", "Customer deleted");
    }
}

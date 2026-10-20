package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemDataCenterEndpointConfigDTO;
import com.machining.quote.dto.SystemDataCenterEndpointConfigSaveReq;
import com.machining.quote.service.DataCenterEndpointConfigService;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/system/data-center-endpoints")
public class SystemDataCenterEndpointConfigController {
    @Autowired
    private DataCenterEndpointConfigService endpointConfigService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemDataCenterEndpointConfigDTO> listEndpoints() {
        return endpointConfigService.listEndpoints();
    }

    @PutMapping("/{endpointCode}")
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> updateEndpoint(
            @PathVariable String endpointCode,
            @RequestBody SystemDataCenterEndpointConfigSaveReq req) {
        return Map.of(
                "success", true,
                "message", "Data center endpoint config saved",
                "data", endpointConfigService.saveEndpoint(endpointCode, req)
        );
    }

    @PostMapping("/{endpointCode}/test")
    @RequirePermission("system:config:integration:test")
    public Map<String, Object> testEndpoint(@PathVariable String endpointCode) {
        return endpointConfigService.testEndpoint(endpointCode);
    }
}

package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemDataCenterConfigDTO;
import com.machining.quote.dto.SystemDataCenterConfigSaveReq;
import com.machining.quote.service.DataCenterConfigService;
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
@RequestMapping("/api/system/data-center-configs")
public class SystemDataCenterConfigController {
    @Autowired
    private DataCenterConfigService dataCenterConfigService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemDataCenterConfigDTO> listConfigs() {
        return dataCenterConfigService.listConfigs();
    }

    @PostMapping
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> createConfig(@RequestBody SystemDataCenterConfigSaveReq req) {
        return Map.of("success", true, "message", "Data center config saved", "data", dataCenterConfigService.saveConfig(null, req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody SystemDataCenterConfigSaveReq req) {
        return Map.of("success", true, "message", "Data center config saved", "data", dataCenterConfigService.saveConfig(id, req));
    }

    @PostMapping("/{id}/test-token")
    @RequirePermission("system:config:integration:test")
    public Map<String, Object> testToken(@PathVariable Long id) {
        return dataCenterConfigService.testToken(id);
    }

    @PutMapping("/{id}/activate")
    @RequirePermission("system:config:integration:activate")
    public Map<String, Object> activateConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Data center config activated", "data", dataCenterConfigService.activateConfig(id));
    }

    @PutMapping("/{id}/disable")
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> disableConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Data center config disabled", "data", dataCenterConfigService.disableConfig(id));
    }
}

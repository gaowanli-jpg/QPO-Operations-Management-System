package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemIntegrationConfigDTO;
import com.machining.quote.dto.SystemIntegrationConfigSaveReq;
import com.machining.quote.service.SystemIntegrationConfigManageService;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/system/integration-configs")
public class SystemIntegrationConfigController {
    @Autowired
    private SystemIntegrationConfigManageService integrationConfigManageService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemIntegrationConfigDTO> listConfigs(@RequestParam(required = false) String systemCode) {
        return integrationConfigManageService.listConfigs(systemCode);
    }

    @GetMapping("/active")
    @RequirePermission("system:config:view")
    public List<SystemIntegrationConfigDTO> activeConfigs() {
        return integrationConfigManageService.activeConfigs();
    }

    @PostMapping
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> createConfig(@RequestBody SystemIntegrationConfigSaveReq req) {
        return Map.of("success", true, "message", "Integration config saved", "data", integrationConfigManageService.saveConfig(null, req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody SystemIntegrationConfigSaveReq req) {
        return Map.of("success", true, "message", "Integration config saved", "data", integrationConfigManageService.saveConfig(id, req));
    }

    @PostMapping("/{id}/test")
    @RequirePermission("system:config:integration:test")
    public Map<String, Object> testConfig(@PathVariable Long id) {
        return integrationConfigManageService.testConfig(id);
    }

    @PutMapping("/{id}/activate")
    @RequirePermission("system:config:integration:activate")
    public Map<String, Object> activateConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Integration config activated", "data", integrationConfigManageService.activateConfig(id));
    }

    @PutMapping("/{id}/disable")
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> disableConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Integration config disabled", "data", integrationConfigManageService.disableConfig(id));
    }
}

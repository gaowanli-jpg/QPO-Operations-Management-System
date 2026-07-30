package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemStorageConfigDTO;
import com.machining.quote.dto.SystemStorageConfigSaveReq;
import com.machining.quote.service.SystemStorageConfigManageService;
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
@RequestMapping("/api/system/storage-configs")
public class SystemStorageConfigController {
    @Autowired
    private SystemStorageConfigManageService storageConfigManageService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemStorageConfigDTO> listConfigs() {
        return storageConfigManageService.listConfigs();
    }

    @GetMapping("/active")
    @RequirePermission("system:config:view")
    public SystemStorageConfigDTO activeConfig() {
        return storageConfigManageService.activeConfig();
    }

    @PostMapping
    @RequirePermission("system:config:storage:update")
    public Map<String, Object> createConfig(@RequestBody SystemStorageConfigSaveReq req) {
        return Map.of("success", true, "message", "Storage config saved", "data", storageConfigManageService.saveConfig(null, req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:storage:update")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody SystemStorageConfigSaveReq req) {
        return Map.of("success", true, "message", "Storage config saved", "data", storageConfigManageService.saveConfig(id, req));
    }

    @PostMapping("/{id}/test")
    @RequirePermission("system:config:storage:test")
    public Map<String, Object> testConfig(@PathVariable Long id) {
        return storageConfigManageService.testConfig(id);
    }

    @PutMapping("/{id}/activate")
    @RequirePermission("system:config:storage:activate")
    public Map<String, Object> activateConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Storage config activated", "data", storageConfigManageService.activateConfig(id));
    }

    @PutMapping("/{id}/disable")
    @RequirePermission("system:config:storage:update")
    public Map<String, Object> disableConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Storage config disabled", "data", storageConfigManageService.disableConfig(id));
    }
}

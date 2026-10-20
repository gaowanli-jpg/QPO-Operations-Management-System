package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigDTO;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigSaveReq;
import com.machining.quote.service.SystemDataCenterSsoConfigService;
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
@RequestMapping("/api/system/data-center-sso-configs")
public class SystemDataCenterSsoConfigController {
    @Autowired
    private SystemDataCenterSsoConfigService ssoConfigService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemDataCenterSsoConfigDTO> listConfigs() {
        return ssoConfigService.listConfigs();
    }

    @PostMapping
    @RequirePermission("system:config:sso:update")
    public Map<String, Object> createConfig(@RequestBody SystemDataCenterSsoConfigSaveReq req) {
        return Map.of("success", true, "message", "Data center SSO config saved", "data", ssoConfigService.saveConfig(null, req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:sso:update")
    public Map<String, Object> updateConfig(@PathVariable Long id, @RequestBody SystemDataCenterSsoConfigSaveReq req) {
        return Map.of("success", true, "message", "Data center SSO config saved", "data", ssoConfigService.saveConfig(id, req));
    }

    @PostMapping("/{id}/test")
    @RequirePermission("system:config:sso:test")
    public Map<String, Object> testConfig(@PathVariable Long id) {
        return ssoConfigService.testConfig(id);
    }

    @PutMapping("/{id}/activate")
    @RequirePermission("system:config:sso:activate")
    public Map<String, Object> activateConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Data center SSO config activated", "data", ssoConfigService.activateConfig(id));
    }

    @PutMapping("/{id}/disable")
    @RequirePermission("system:config:sso:update")
    public Map<String, Object> disableConfig(@PathVariable Long id) {
        return Map.of("success", true, "message", "Data center SSO config disabled", "data", ssoConfigService.disableConfig(id));
    }
}

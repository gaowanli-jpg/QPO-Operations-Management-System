package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.U9RuntimeConfigDTO;
import com.machining.quote.dto.U9RuntimeConfigReq;
import com.machining.quote.service.u9.U9RuntimeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/u9-runtime-config")
public class U9RuntimeConfigController {
    @Autowired
    private U9RuntimeConfigService u9RuntimeConfigService;

    @GetMapping
    @RequirePermission("system:config:view")
    public U9RuntimeConfigDTO getConfig() {
        return u9RuntimeConfigService.getSetting();
    }

    @PutMapping
    @RequirePermission("system:config:integration:update")
    public Map<String, Object> updateConfig(@RequestBody U9RuntimeConfigReq req) {
        return Map.of("success", true, "message", "U9组织编码已保存", "data", u9RuntimeConfigService.updateSetting(req));
    }
}

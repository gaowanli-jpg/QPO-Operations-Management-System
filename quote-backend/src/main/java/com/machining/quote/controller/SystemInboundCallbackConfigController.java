package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.SystemInboundCallbackConfigDTO;
import com.machining.quote.dto.SystemInboundCallbackConfigSaveReq;
import com.machining.quote.dto.SystemInboundCallbackTokenResp;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/inbound-callback-configs")
public class SystemInboundCallbackConfigController {
    @Autowired
    private SystemInboundCallbackConfigService inboundCallbackConfigService;

    @GetMapping
    @RequirePermission("system:config:view")
    public List<SystemInboundCallbackConfigDTO> listConfigs() {
        return inboundCallbackConfigService.listConfigs();
    }

    @GetMapping("/open-api-token")
    @RequirePermission("system:config:view")
    public Map<String, Object> getOpenApiTokenStatus() {
        return Map.of(
                "success", true,
                "message", "Inbound open API token status loaded",
                "data", inboundCallbackConfigService.getOpenApiTokenStatus()
        );
    }

    @PostMapping("/open-api-token")
    @RequirePermission("system:config:inbound:token-rotate")
    public Map<String, Object> rotateOpenApiToken() {
        SystemInboundCallbackTokenResp data = inboundCallbackConfigService.rotateOpenApiToken();
        return Map.of("success", true, "message", "Inbound open API token rotated", "data", data);
    }

    @GetMapping("/mes-material-callback")
    @RequirePermission("system:config:view")
    public SystemInboundCallbackConfigDTO getMesMaterialCallbackConfig() {
        return inboundCallbackConfigService.getMesMaterialCallbackConfig();
    }

    @PutMapping("/mes-material-callback")
    @RequirePermission("system:config:inbound:update")
    public Map<String, Object> saveMesMaterialCallbackConfig(@RequestBody SystemInboundCallbackConfigSaveReq req) {
        return Map.of(
                "success", true,
                "message", "Inbound callback config saved",
                "data", inboundCallbackConfigService.saveMesMaterialCallbackConfig(req)
        );
    }

    @PostMapping("/mes-material-callback/token")
    @RequirePermission("system:config:inbound:token-rotate")
    public Map<String, Object> rotateMesMaterialCallbackToken() {
        SystemInboundCallbackTokenResp data = inboundCallbackConfigService.rotateMesMaterialCallbackToken();
        return Map.of("success", true, "message", "Inbound callback token rotated", "data", data);
    }

    @PostMapping("/mes-material-callback/validate")
    @RequirePermission("system:config:inbound:update")
    public Map<String, Object> validateMesMaterialCallbackConfig() {
        return inboundCallbackConfigService.validateMesMaterialCallbackConfig();
    }
}

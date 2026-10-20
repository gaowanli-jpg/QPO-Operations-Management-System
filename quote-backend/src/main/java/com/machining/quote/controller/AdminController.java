package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.QuoteTemplateConfigReq;
import com.machining.quote.dto.UserSaveReq;
import com.machining.quote.service.QuoteTemplateService;
import com.machining.quote.service.UserAdminService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private QuoteTemplateService quoteTemplateService;

    @GetMapping("/users")
    @RequirePermission("system:user:view")
    public List<Map<String, Object>> listUsers() {
        return userAdminService.listUsers();
    }

    @PostMapping("/users")
    @RequirePermission("system:user:create")
    public Map<String, Object> createUser(@RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "用户已创建", "data", userAdminService.createUser(req));
    }

    @PutMapping("/users/{id}")
    @RequirePermission("system:user:update")
    public Map<String, Object> updateUser(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "用户已更新", "data", userAdminService.updateUser(id, req));
    }

    @PutMapping("/users/me/password")
    public Map<String, Object> changeMyPassword(@RequestBody PasswordChangeReq req) {
        userAdminService.changeMyPassword(req);
        return Map.of("success", true, "message", "密码已修改");
    }

    @DeleteMapping("/users/{id}")
    @RequirePermission("system:user:delete")
    public Map<String, Object> deleteUser(@PathVariable Integer id) {
        userAdminService.deleteUser(id);
        return Map.of("success", true, "message", "用户已删除");
    }

    @GetMapping("/quote-template")
    @RequirePermission("market:template:view")
    public Map<String, Object> quoteTemplateStatus() {
        return quoteTemplateService.status();
    }

    @PostMapping("/quote-template")
    @RequirePermission("market:template:upload")
    public Map<String, Object> uploadQuoteTemplate(@RequestParam("templateFile") MultipartFile templateFile) {
        return Map.of("success", true, "message", "报价单模板已更新", "data", quoteTemplateService.uploadTemplate(templateFile));
    }

    @PutMapping("/quote-template/config")
    @RequirePermission("market:template:upload")
    public Map<String, Object> updateQuoteTemplateConfig(@RequestBody QuoteTemplateConfigReq req) {
        return Map.of("success", true, "message", "报价单模板配置已保存", "data", quoteTemplateService.updateConfig(req));
    }
}

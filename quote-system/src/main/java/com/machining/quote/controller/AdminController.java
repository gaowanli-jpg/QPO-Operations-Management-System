package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.UserSaveReq;
import com.machining.quote.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequireRole({"ADMIN"})
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired
    private UserAdminService userAdminService;

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return userAdminService.listUsers();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "用户已创建", "data", userAdminService.createUser(req));
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "用户已更新", "data", userAdminService.updateUser(id, req));
    }

    @PutMapping("/users/me/password")
    public Map<String, Object> changeMyPassword(@RequestBody PasswordChangeReq req) {
        userAdminService.changeMyPassword(req);
        return Map.of("success", true, "message", "密码已修改");
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> disableUser(@PathVariable Integer id) {
        UserSaveReq req = new UserSaveReq();
        req.setEnabled(false);
        userAdminService.updateUser(id, req);
        return Map.of("success", true, "message", "用户已禁用");
    }
}

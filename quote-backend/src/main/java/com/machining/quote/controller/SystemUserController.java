package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
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
@RequestMapping("/api/system/users")
public class SystemUserController {
    @Autowired
    private UserAdminService userAdminService;

    @GetMapping
    @RequirePermission("system:user:view")
    public List<Map<String, Object>> listUsers() {
        return userAdminService.listUsers();
    }

    @PostMapping
    @RequirePermission("system:user:create")
    public Map<String, Object> createUser(@RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "User created", "data", userAdminService.createUser(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:user:update")
    public Map<String, Object> updateUser(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "User updated", "data", userAdminService.updateUser(id, req));
    }

    @PutMapping("/{id}/reset-password")
    @RequirePermission("system:user:reset-password")
    public Map<String, Object> resetPassword(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "Password reset", "data", userAdminService.resetPassword(id, req));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("system:user:disable")
    public Map<String, Object> updateStatus(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "User status updated", "data", userAdminService.updateStatus(id, req.getEnabled()));
    }

    @PostMapping("/{id}/roles")
    @RequirePermission("system:user:update")
    public Map<String, Object> assignRoles(@PathVariable Integer id, @RequestBody UserSaveReq req) {
        return Map.of("success", true, "message", "User roles updated", "data", userAdminService.updateUser(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:user:delete")
    public Map<String, Object> deleteUser(@PathVariable Integer id) {
        userAdminService.deleteUser(id);
        return Map.of("success", true, "message", "User deleted");
    }
}

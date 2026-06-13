package com.machining.apssystem.controller;

import com.machining.apssystem.config.RequireRole;
import com.machining.apssystem.dto.ApsUserDTO;
import com.machining.apssystem.dto.ApsUserSaveReq;
import com.machining.apssystem.service.ApsUserAdminService;
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
@RequestMapping("/api/aps/admin")
public class ApsAdminController {
    @Autowired
    private ApsUserAdminService userAdminService;

    @GetMapping("/users")
    public List<ApsUserDTO> listUsers() {
        return userAdminService.listUsers();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody ApsUserSaveReq req) {
        return Map.of("success", true, "message", "用户已创建", "data", userAdminService.createUser(req));
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody ApsUserSaveReq req) {
        return Map.of("success", true, "message", "用户已更新", "data", userAdminService.updateUser(id, req));
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        userAdminService.deleteUser(id);
        return Map.of("success", true, "message", "用户已删除");
    }
}

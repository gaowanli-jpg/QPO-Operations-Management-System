package com.machining.apssystem.controller;

import com.machining.apssystem.dto.LoginReq;
import com.machining.apssystem.dto.PasswordChangeReq;
import com.machining.apssystem.service.ApsAuthService;
import com.machining.apssystem.service.ApsUserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class ApsAuthController {
    @Autowired
    private ApsAuthService authService;

    @Autowired
    private ApsUserAdminService userAdminService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(resolveToken(authorization));
        return Map.of("success", true, "message", "已退出登录");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        var user = authService.findByToken(resolveToken(authorization));
        if (user == null) {
            return Map.of("success", false, "message", "未登录");
        }
        return Map.of("success", true, "data", Map.of(
                "username", user.username(),
                "displayName", user.displayName() == null ? user.username() : user.displayName(),
                "role", user.role(),
                "forcePasswordChange", authService.mustChangePassword(user.id())
        ));
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody PasswordChangeReq req) {
        userAdminService.changeMyPassword(req);
        return Map.of("success", true, "message", "密码已修改");
    }

    private String resolveToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length());
        }
        return authorization;
    }
}

package com.machining.quote.controller;

import com.machining.quote.dto.LoginReq;
import com.machining.quote.dto.MyProfileUpdateReq;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.UserAdminService;
import com.machining.quote.service.auth.DataCenterSsoAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private DataCenterSsoAuthService dataCenterSsoAuthService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    @PostMapping("/datacenter-sso/exchange")
    public Map<String, Object> exchangeDataCenterSso(@RequestBody DataCenterSsoExchangeReq req) {
        return dataCenterSsoAuthService.exchange(req);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        authService.logout(token);
        return Map.of("success", true, "message", "已退出登录");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        var user = authService.findByToken(token);
        if (user == null) {
            return Map.of("success", false, "message", "未登录");
        }
        return Map.of("success", true, "data", userAdminService.currentUserProfile(user.id()));
    }

    @PutMapping("/me/profile")
    public Map<String, Object> updateMyProfile(@RequestBody MyProfileUpdateReq req) {
        return Map.of("success", true, "message", "Profile updated", "data", userAdminService.updateMyProfile(req));
    }

    @GetMapping("/permission-info")
    public Map<String, Object> permissionInfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        var user = authService.findByToken(token);
        if (user == null) {
            return Map.of("success", false, "message", "未登录");
        }
        return Map.of("success", true, "data", authService.permissionInfo(user));
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody PasswordChangeReq req) {
        userAdminService.changeMyPassword(req);
        return Map.of("success", true, "message", "密码已修改");
    }
}

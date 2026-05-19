package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.ErpUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
public class AuthService {
    @Autowired
    private ErpUserMapper userMapper;

    @Autowired
    private JwtService jwtService;

    public Map<String, Object> login(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.isEmpty() || password == null || password.isEmpty()) {
            throw new RuntimeException("请输入用户名和密码");
        }

        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.eq("username", safeUsername).last("LIMIT 1");
        ErpUser user = userMapper.selectOne(qw);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())
                || !hashPassword(password).equalsIgnoreCase(user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        AuthContext.SessionUser sessionUser = new AuthContext.SessionUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
        String token = jwtService.createToken(sessionUser);
        return Map.of(
                "success", true,
                "message", "登录成功",
                "token", token,
                "user", Map.of(
                        "username", sessionUser.username(),
                        "displayName", sessionUser.displayName() == null ? sessionUser.username() : sessionUser.displayName(),
                        "role", sessionUser.role()
                )
        );
    }

    public AuthContext.SessionUser findByToken(String token) {
        return jwtService.parseToken(token);
    }

    public void logout(String token) {
        // JWT 无状态，不需要服务端注销
    }

    public String hashPassword(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

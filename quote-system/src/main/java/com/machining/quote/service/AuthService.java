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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final Map<String, AuthContext.SessionUser> sessions = new ConcurrentHashMap<>();

    @Autowired
    private ErpUserMapper userMapper;

    public Map<String, Object> login(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.isEmpty() || password == null || password.isEmpty()) {
            throw new RuntimeException("请输入用户名和密码");
        }

        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.eq("username", safeUsername).last("LIMIT 1");
        ErpUser user = userMapper.selectOne(qw);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())
                || !sha256(password).equalsIgnoreCase(user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        AuthContext.SessionUser sessionUser = new AuthContext.SessionUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
        sessions.put(token, sessionUser);
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
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessions.get(token.trim());
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token.trim());
        }
    }

    private String sha256(String value) {
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

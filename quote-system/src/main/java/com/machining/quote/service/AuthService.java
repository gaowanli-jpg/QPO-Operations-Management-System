package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.ErpUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 10;
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    @Autowired
    private ErpUserMapper userMapper;

    @Autowired
    private JwtService jwtService;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.isEmpty() || password == null || password.isEmpty()) {
            throw new RuntimeException("请输入用户名和密码");
        }

        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.eq("username", safeUsername).last("LIMIT 1");
        ErpUser user = userMapper.selectOne(qw);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (isLocked(user)) {
            throw new RuntimeException("登录失败次数过多，请稍后再试");
        }
        if (!matchesPassword(password, user.getPasswordHash())) {
            recordFailedLogin(user);
            throw new RuntimeException("用户名或密码错误");
        }

        boolean passwordUpgraded = upgradeLegacyPasswordIfNeeded(user, password);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

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
                        "role", sessionUser.role(),
                        "forcePasswordChange", Boolean.TRUE.equals(user.getForcePasswordChange())
                )
        );
    }

    public AuthContext.SessionUser findByToken(String token) {
        return jwtService.parseToken(token);
    }

    public boolean mustChangePassword(Integer userId) {
        if (userId == null) {
            return false;
        }
        ErpUser user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getForcePasswordChange());
    }

    public void logout(String token) {
        // JWT 无状态，不需要服务端注销
    }

    public String hashPassword(String value) {
        return PASSWORD_ENCODER.encode(value);
    }

    public boolean matchesPassword(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        if (isBcryptHash(passwordHash)) {
            return PASSWORD_ENCODER.matches(rawPassword, passwordHash);
        }
        return legacySha256(rawPassword).equalsIgnoreCase(passwordHash);
    }

    private boolean upgradeLegacyPasswordIfNeeded(ErpUser user, String rawPassword) {
        if (user == null || isBcryptHash(user.getPasswordHash())) {
            return false;
        }
        user.setPasswordHash(hashPassword(rawPassword));
        return true;
    }

    private void recordFailedLogin(ErpUser user) {
        int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        attempts += 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
        userMapper.updateById(user);
    }

    private boolean isLocked(ErpUser user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(LocalDateTime.now())) {
            return true;
        }
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userMapper.updateById(user);
        return false;
    }

    private boolean isBcryptHash(String passwordHash) {
        return passwordHash != null && passwordHash.startsWith("$2");
    }

    private String legacySha256(String value) {
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

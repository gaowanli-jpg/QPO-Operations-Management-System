package com.machining.quote.service;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.PermissionInfoDTO;
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

    @Autowired(required = false)
    private PermissionService permissionService;

    @Autowired
    private AuditService auditService;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.isEmpty() || password == null || password.isEmpty()) {
            recordAuthAudit(
                    "AUTH_LOGIN_FAILED",
                    "auth",
                    safeUsername.isEmpty() ? null : safeUsername,
                    null,
                    Map.of("username", safeUsername, "reason", "missing_credentials"),
                    "FAILED",
                    "AUTH_LOGIN_FAILED " + (safeUsername.isEmpty() ? "unknown" : safeUsername)
            );
            throw new RuntimeException("请输入用户名和密码");
        }

        ErpUser user = userMapper.selectByUsernameForUpdate(safeUsername);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            recordAuthAudit(
                    "AUTH_LOGIN_FAILED",
                    "auth",
                    safeUsername,
                    null,
                    Map.of("username", safeUsername, "reason", user == null ? "user_not_found" : "user_disabled"),
                    "FAILED",
                    "AUTH_LOGIN_FAILED " + safeUsername
            );
            throw new RuntimeException("用户名或密码错误");
        }
        if (isLocked(user)) {
            recordAccountLocked(user, "already_locked");
            throw new RuntimeException("登录失败次数过多，请稍后再试");
        }
        if (!matchesPassword(password, user.getPasswordHash())) {
            recordFailedLogin(user);
            recordLoginFailed(user);
            if (isLockedNow(user)) {
                recordAccountLocked(user, "max_failed_attempts");
            }
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
        recordAuthAudit(
                "AUTH_LOGIN_SUCCESS",
                "erp_user",
                user.getId(),
                null,
                publicUser(user),
                "SUCCESS",
                "AUTH_LOGIN_SUCCESS " + safeUsername
        );
        PermissionInfoDTO permissionInfo = permissionInfo(sessionUser);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        Map<String, Object> userPayload = new java.util.LinkedHashMap<>();
        userPayload.put("username", sessionUser.username());
        userPayload.put("displayName", sessionUser.displayName() == null ? sessionUser.username() : sessionUser.displayName());
        userPayload.put("role", sessionUser.role());

        result.put("success", true);
        result.put("message", "登录成功");
        result.put("token", token);
        result.put("user", userPayload);
        result.put("roles", permissionInfo.getRoles());
        result.put("permissions", permissionInfo.getPermissions());
        result.put("menus", permissionInfo.getMenus());
        return result;
    }

    public PermissionInfoDTO permissionInfo(AuthContext.SessionUser user) {
        if (user == null || permissionService == null) {
            return new PermissionInfoDTO();
        }
        return permissionService.getPermissionInfo(user.id(), user.role());
    }

    public AuthContext.SessionUser findByToken(String token) {
        AuthContext.SessionUser tokenUser = jwtService.parseToken(token);
        if (tokenUser == null) {
            return null;
        }
        if (userMapper == null || tokenUser.id() == null) {
            return tokenUser;
        }
        ErpUser user = userMapper.selectById(tokenUser.id());
        if (user == null) {
            return tokenUser.role() == null ? null : tokenUser;
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return null;
        }
        return new AuthContext.SessionUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    public void logout(String token) {
        AuthContext.SessionUser current = AuthContext.get();
        boolean temporaryContext = false;
        if (current == null) {
            current = findByToken(token);
            if (current != null) {
                AuthContext.set(current);
                temporaryContext = true;
            }
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("username", current == null ? null : current.username());
            payload.put("role", current == null ? null : current.role());
            recordAuthAudit(
                    "AUTH_LOGOUT",
                    current == null ? "auth" : "erp_user",
                    current == null ? null : current.id(),
                    null,
                    payload,
                    "SUCCESS",
                    "AUTH_LOGOUT " + (current == null ? "unknown" : current.username())
            );
        } finally {
            if (temporaryContext) {
                AuthContext.clear();
            }
        }
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

    private boolean isLockedNow(ErpUser user) {
        LocalDateTime lockedUntil = user == null ? null : user.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    private void recordLoginFailed(ErpUser user) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("username", user.getUsername());
        payload.put("failedLoginAttempts", user.getFailedLoginAttempts());
        payload.put("lockedUntil", user.getLockedUntil());
        recordAuthAudit(
                "AUTH_LOGIN_FAILED",
                "erp_user",
                user.getId(),
                null,
                payload,
                "FAILED",
                "AUTH_LOGIN_FAILED " + user.getUsername()
        );
    }

    private void recordAccountLocked(ErpUser user, String reason) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("username", user.getUsername());
        payload.put("failedLoginAttempts", user.getFailedLoginAttempts());
        payload.put("lockedUntil", user.getLockedUntil());
        payload.put("reason", reason);
        recordAuthAudit(
                "AUTH_ACCOUNT_LOCKED",
                "erp_user",
                user.getId(),
                null,
                payload,
                "FAILED",
                "AUTH_ACCOUNT_LOCKED " + user.getUsername()
        );
    }

    private Map<String, Object> publicUser(ErpUser user) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("displayName", user.getDisplayName());
        payload.put("role", user.getRole());
        return payload;
    }

    private void recordAuthAudit(
            String action,
            String targetType,
            Object targetId,
            Object beforeData,
            Object afterData,
            String result,
            String message) {
        if (auditService != null) {
            auditService.record(action, targetType, targetId, beforeData, afterData, "AUTH", result, message, null);
        }
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

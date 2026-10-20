package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionAuthServiceTest {
    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void successfulLoginKeepsTokenAndCurrentUserShape() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser user = user("market", authService.hashPassword("correct-password"), "MARKET", true);
        when(userMapper.selectByUsernameForUpdate("market")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-market");

        Map<String, Object> result = authService.login(" market ", "correct-password");

        assertThat(result).containsEntry("success", true).containsEntry("token", "token-market");
        assertThat(result.get("user"))
                .isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                        .containsEntry("username", "market")
                        .containsEntry("displayName", "Market User")
                        .containsEntry("role", "MARKET")
                        .doesNotContainKey("forcePasswordChange"));
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userMapper).updateById(user);
        verify(auditService).record(
                eq("AUTH_LOGIN_SUCCESS"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("SUCCESS"),
                contains("market"),
                isNull()
        );
    }

    @Test
    void disabledUserStillCannotLogin() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser disabled = user("engineer", authService.hashPassword("correct-password"), "ENGINEER", false);
        when(userMapper.selectByUsernameForUpdate("engineer")).thenReturn(disabled);

        assertThatThrownBy(() -> authService.login("engineer", "correct-password"))
                .isInstanceOf(RuntimeException.class);

        verify(auditService).record(
                eq("AUTH_LOGIN_FAILED"),
                eq("auth"),
                eq("engineer"),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("FAILED"),
                contains("engineer"),
                isNull()
        );
    }

    @Test
    void legacySha256PasswordStillUpgradesAfterSuccessfulLogin() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser user = user("legacy", sha256("legacy-password"), "ADMIN", true);
        when(userMapper.selectByUsernameForUpdate("legacy")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-legacy");

        authService.login("legacy", "legacy-password");

        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(authService.matchesPassword("legacy-password", user.getPasswordHash())).isTrue();
        verify(userMapper).updateById(user);
    }

    private AuthService authService(ErpUserMapper userMapper, JwtService jwtService, AuditService auditService) {
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "auditService", auditService);
        return authService;
    }

    private ErpUser user(String username, String passwordHash, String role, boolean enabled) {
        ErpUser user = new ErpUser();
        user.setId(7);
        user.setUsername(username);
        user.setDisplayName("Market User");
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setFailedLoginAttempts(3);
        return user;
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
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

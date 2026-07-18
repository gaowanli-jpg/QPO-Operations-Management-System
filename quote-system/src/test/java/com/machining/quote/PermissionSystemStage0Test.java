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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSystemStage0Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path CONTROLLER_DIR = MAIN_JAVA.resolve(Path.of("com", "machining", "quote", "controller"));
    private static final Path MAPPING_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Pattern PERMISSION_CODE = Pattern.compile("`([a-z]+:[a-z0-9-]+:[a-z0-9-]+)`");
    private static final Pattern REQUIRE_ROLE = Pattern.compile("@RequireRole\\(\\{([^}]*)}\\)");

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void permissionCatalogFreezesUniquePermissionCodes() throws IOException {
        String doc = readMappingDoc();
        Matcher matcher = PERMISSION_CODE.matcher(doc);
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                duplicates.add(code);
            }
        }

        assertThat(doc).contains("权限清单冻结版本：Stage 0");
        assertThat(codes)
                .as("Stage 0 must freeze the target permission catalog before RBAC migrations start")
                .contains(
                        "market:rfq:import",
                        "engineer:pricing:save-item",
                        "market:release:create",
                        "system:user:update",
                        "system:role:assign-permission",
                        "system:audit:export"
                );
        assertThat(duplicates)
                .as("Permission codes must stay unique")
                .isEmpty();
    }

    @Test
    void requireRoleMigrationCatalogCoversAllCurrentAnnotations() throws IOException {
        String doc = readMappingDoc();
        List<String> missing = new ArrayList<>();

        try (Stream<Path> files = Files.walk(CONTROLLER_DIR)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectMissingMigrationRows(path, doc, missing));
        }

        assertThat(missing)
                .as("Every current @RequireRole occurrence needs an explicit migration row")
                .isEmpty();
    }

    @Test
    void roleMigrationKeepsCurrentBuiltInRolesExplicit() throws IOException {
        String doc = readMappingDoc();

        assertThat(doc)
                .contains("| `ADMIN` | `SUPER_ADMIN` |")
                .contains("| `MARKET` | `MARKET_USER` |")
                .contains("| `ENGINEER` | `ENGINEER_USER` |")
                .contains("管理员继续拥有全部功能权限")
                .contains("市场与工程账号迁移后默认保持现有可访问页面不变");
    }

    @Test
    void successfulLoginKeepsTokenAndCurrentUserShape() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = authService(userMapper, jwtService, auditService);
        ErpUser user = user("market", authService.hashPassword("correct-password"), "MARKET", true, true);
        when(userMapper.selectByUsernameForUpdate("market")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-market");

        Map<String, Object> result = authService.login(" market ", "correct-password");

        assertThat(result).containsEntry("success", true).containsEntry("token", "token-market");
        assertThat(result.get("user"))
                .isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                        .containsEntry("username", "market")
                        .containsEntry("displayName", "Market User")
                        .containsEntry("role", "MARKET")
                        .containsEntry("forcePasswordChange", true));
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
        ErpUser disabled = user("engineer", authService.hashPassword("correct-password"), "ENGINEER", false, false);
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
        ErpUser user = user("legacy", sha256("legacy-password"), "ADMIN", true, false);
        when(userMapper.selectByUsernameForUpdate("legacy")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-legacy");

        authService.login("legacy", "legacy-password");

        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(authService.matchesPassword("legacy-password", user.getPasswordHash())).isTrue();
        verify(userMapper).updateById(user);
    }

    private void collectMissingMigrationRows(Path path, String doc, List<String> missing) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = REQUIRE_ROLE.matcher(source);
            String relativePath = "controller/" + path.getFileName();
            while (matcher.find()) {
                String roleSet = normalizeRoleSet(matcher.group(1));
                if (!doc.contains(relativePath) || !doc.contains(roleSet)) {
                    missing.add(relativePath + " " + roleSet);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan " + path, e);
        }
    }

    private String normalizeRoleSet(String rawRoles) {
        return rawRoles.replace("\"", "")
                .replace(" ", "")
                .replace("\r", "")
                .replace("\n", "");
    }

    private String readMappingDoc() throws IOException {
        assertThat(MAPPING_DOC)
                .as("Stage 0 mapping document must exist")
                .exists();
        return Files.readString(MAPPING_DOC, StandardCharsets.UTF_8);
    }

    private AuthService authService(ErpUserMapper userMapper, JwtService jwtService, AuditService auditService) {
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "auditService", auditService);
        return authService;
    }

    private ErpUser user(String username, String passwordHash, String role, boolean enabled, boolean forcePasswordChange) {
        ErpUser user = new ErpUser();
        user.setId(7);
        user.setUsername(username);
        user.setDisplayName("Market User");
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setForcePasswordChange(forcePasswordChange);
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

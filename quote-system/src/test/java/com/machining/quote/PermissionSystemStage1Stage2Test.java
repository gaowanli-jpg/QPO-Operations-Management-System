package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.MenuTreeDTO;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.SystemPermissionMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.JwtService;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionSystemStage1Stage2Test {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V25__enterprise_permission_system.sql");

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void enterprisePermissionMigrationCreatesTablesSeedsRolesAndMigratesLegacyUsers() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS system_user")
                .contains("CREATE TABLE IF NOT EXISTS system_dept")
                .contains("CREATE TABLE IF NOT EXISTS system_post")
                .contains("CREATE TABLE IF NOT EXISTS system_role")
                .contains("CREATE TABLE IF NOT EXISTS system_user_role")
                .contains("CREATE TABLE IF NOT EXISTS system_permission")
                .contains("CREATE TABLE IF NOT EXISTS system_role_permission")
                .contains("CREATE TABLE IF NOT EXISTS system_role_data_scope_dept")
                .contains("CREATE TABLE IF NOT EXISTS system_role_data_scope_customer")
                .contains("'SUPER_ADMIN'")
                .contains("'MARKET_USER'")
                .contains("'ENGINEER_USER'")
                .contains("'MARKET_MANAGER'")
                .contains("'ENGINEER_MANAGER'")
                .contains("'AUDITOR'")
                .contains("'READONLY'")
                .contains("legacy_user_id")
                .contains("FROM erp_user")
                .contains("WHEN 'ADMIN' THEN 'SUPER_ADMIN'")
                .contains("WHEN 'MARKET' THEN 'MARKET_USER'")
                .contains("WHEN 'ENGINEER' THEN 'ENGINEER_USER'");
    }

    @Test
    void permissionServiceBuildsRolesPermissionsAndVisibleMenuTree() {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        PermissionService permissionService = new PermissionService();
        ReflectionTestUtils.setField(permissionService, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(permissionService, "permissionMapper", permissionMapper);

        when(roleMapper.selectActiveRolesByLegacyUserId(7)).thenReturn(List.of(
                role("SUPER_ADMIN", true),
                role("MARKET_USER", false)
        ));
        when(permissionMapper.selectEnabledPermissionsByLegacyUserId(7)).thenReturn(List.of(
                permission(1L, 0L, "MENU", "market:rfq:view", "新单导入", "/market", "MarketDashboard", true, 10),
                permission(2L, 0L, "BUTTON", "market:rfq:import", "导入 RFQ", null, null, false, 20),
                permission(3L, 1L, "MENU", "market:order:view", "订单管理", "/orders", "OrderManage", false, 30)
        ));

        PermissionInfoDTO info = permissionService.getPermissionInfo(7, "ADMIN");

        assertThat(info.getRoles()).containsExactly("SUPER_ADMIN", "MARKET_USER");
        assertThat(info.getPermissions()).contains("market:rfq:view", "market:rfq:import", "market:order:view");
        assertThat(info.isSuperAdmin()).isTrue();
        assertThat(info.getMenus())
                .extracting(MenuTreeDTO::getPath)
                .containsExactly("/market");
    }

    @Test
    void loginReturnsPermissionPayloadWhileKeepingLegacyUserShape() {
        AuditService auditService = mock(AuditService.class);
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuthService authService = authService(userMapper, jwtService, auditService, permissionService);
        ErpUser user = user("market", authService.hashPassword("correct-password"), "MARKET");
        when(userMapper.selectByUsernameForUpdate("market")).thenReturn(user);
        when(jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("token-market");
        when(permissionService.getPermissionInfo(7, "MARKET")).thenReturn(permissionInfo(
                List.of("MARKET_USER"),
                List.of("market:rfq:view", "common:order:select"),
                List.of(menu("/market", "新单导入", "market:rfq:view"))
        ));

        Map<String, Object> result = authService.login("market", "correct-password");

        assertThat(result.get("user"))
                .isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                        .containsEntry("username", "market")
                        .containsEntry("role", "MARKET"));
        assertThat(result.get("roles")).isEqualTo(List.of("MARKET_USER"));
        assertThat(result.get("permissions")).isEqualTo(List.of("market:rfq:view", "common:order:select"));
        assertThat(result.get("menus"))
                .isInstanceOfSatisfying(List.class, menus -> assertThat(menus).hasSize(1));
    }

    @Test
    void jwtKeepsIdentityAndTokenVersionButNotPermissionPayload() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "stage-two-secret");
        ReflectionTestUtils.setField(jwtService, "ttlMinutes", 30L);

        String token = jwtService.createToken(new AuthContext.SessionUser(7, "admin", "管理员", "ADMIN"));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload)
                .contains("\"uid\":7")
                .contains("\"sub\":\"admin\"")
                .contains("\"tokenVersion\":1")
                .doesNotContain("displayName")
                .doesNotContain("role")
                .doesNotContain("permissions")
                .doesNotContain("menus");
    }

    private AuthService authService(
            ErpUserMapper userMapper,
            JwtService jwtService,
            AuditService auditService,
            PermissionService permissionService) {
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "auditService", auditService);
        ReflectionTestUtils.setField(authService, "permissionService", permissionService);
        return authService;
    }

    private ErpUser user(String username, String passwordHash, String role) {
        ErpUser user = new ErpUser();
        user.setId(7);
        user.setUsername(username);
        user.setDisplayName("Market User");
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(true);
        user.setForcePasswordChange(false);
        user.setFailedLoginAttempts(0);
        return user;
    }

    private SystemRole role(String code, boolean superAdmin) {
        SystemRole role = new SystemRole();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setSuperAdmin(superAdmin);
        return role;
    }

    private SystemPermission permission(
            Long id,
            Long parentId,
            String type,
            String code,
            String name,
            String routePath,
            String componentKey,
            boolean visible,
            int sortOrder) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setPermissionType(type);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setRoutePath(routePath);
        permission.setComponentKey(componentKey);
        permission.setVisible(visible);
        permission.setSortOrder(sortOrder);
        return permission;
    }

    private PermissionInfoDTO permissionInfo(List<String> roles, List<String> permissions, List<MenuTreeDTO> menus) {
        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setRoles(roles);
        info.setPermissions(permissions);
        info.setMenus(menus);
        info.setSuperAdmin(roles.contains("SUPER_ADMIN"));
        return info;
    }

    private MenuTreeDTO menu(String path, String title, String permissionCode) {
        MenuTreeDTO menu = new MenuTreeDTO();
        menu.setPath(path);
        menu.setTitle(title);
        menu.setPermission(permissionCode);
        return menu;
    }
}

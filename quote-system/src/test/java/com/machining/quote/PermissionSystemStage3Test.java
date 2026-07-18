package com.machining.quote;

import com.machining.quote.dto.RolePermissionAssignReq;
import com.machining.quote.dto.SystemUserSaveReq;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.SystemPermission;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.SystemPermissionMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemRolePermissionMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.mapper.SystemUserRoleMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.SystemRoleService;
import com.machining.quote.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSystemStage3Test {
    @Test
    void userAdminServiceCreatesLegacyUserAndBindsMultipleSystemRoles() {
        ErpUserMapper erpUserMapper = mock(ErpUserMapper.class);
        SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        SystemRoleMapper systemRoleMapper = mock(SystemRoleMapper.class);
        SystemUserRoleMapper systemUserRoleMapper = mock(SystemUserRoleMapper.class);
        AuditService auditService = mock(AuditService.class);

        UserAdminService service = new UserAdminService();
        ReflectionTestUtils.setField(service, "userMapper", erpUserMapper);
        ReflectionTestUtils.setField(service, "authService", new AuthService());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "systemUserMapper", systemUserMapper);
        ReflectionTestUtils.setField(service, "systemRoleMapper", systemRoleMapper);
        ReflectionTestUtils.setField(service, "systemUserRoleMapper", systemUserRoleMapper);

        when(erpUserMapper.selectCount(any())).thenReturn(0L);
        when(systemRoleMapper.selectByRoleCodes(List.of("MARKET_USER", "AUDITOR")))
                .thenReturn(List.of(role(20L, "MARKET_USER"), role(60L, "AUDITOR")));
        doAnswer(invocation -> {
            ErpUser user = invocation.getArgument(0);
            user.setId(77);
            return 1;
        }).when(erpUserMapper).insert(any(ErpUser.class));
        doAnswer(invocation -> {
            SystemUser user = invocation.getArgument(0);
            user.setId(177L);
            return 1;
        }).when(systemUserMapper).insert(any(SystemUser.class));

        SystemUserSaveReq req = new SystemUserSaveReq();
        req.setUsername("multi-role");
        req.setPassword("password-123");
        req.setDisplayName("Multi Role");
        req.setRoleCodes(List.of("MARKET_USER", "AUDITOR"));
        req.setEnabled(true);

        Map<String, Object> result = service.createUser(req);

        assertThat(result)
                .containsEntry("id", 77)
                .containsEntry("systemUserId", 177L)
                .containsEntry("role", "MARKET");
        assertThat(result.get("roleCodes")).isEqualTo(List.of("MARKET_USER", "AUDITOR"));
        verify(systemUserRoleMapper).deleteByUserId(177L);
        verify(systemUserRoleMapper).insertIgnore(177L, 20L);
        verify(systemUserRoleMapper).insertIgnore(177L, 60L);
        verify(auditService).record(eq("SYSTEM_USER_CREATE"), eq("system_user"), eq(177L), eq(null), any());
        verify(auditService).record(eq("SYSTEM_USER_ASSIGN_ROLE"), eq("system_user"), eq(177L), any(), any());
    }

    @Test
    void systemRoleServiceReplacesPermissionsAndAuditsTheChange() {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SystemRolePermissionMapper rolePermissionMapper = mock(SystemRolePermissionMapper.class);
        AuditService auditService = mock(AuditService.class);

        SystemRoleService service = new SystemRoleService();
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);
        ReflectionTestUtils.setField(service, "rolePermissionMapper", rolePermissionMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        when(roleMapper.selectById(20L)).thenReturn(role(20L, "MARKET_USER"));
        when(rolePermissionMapper.selectPermissionCodesByRoleId(20L)).thenReturn(List.of("market:rfq:view"));
        when(permissionMapper.selectByPermissionCodes(List.of("market:rfq:view", "market:rfq:import")))
                .thenReturn(List.of(permission(1L, "market:rfq:view"), permission(2L, "market:rfq:import")));

        RolePermissionAssignReq req = new RolePermissionAssignReq();
        req.setPermissionCodes(List.of("market:rfq:view", "market:rfq:import"));
        service.assignPermissions(20L, req);

        verify(rolePermissionMapper).deleteByRoleId(20L);
        verify(rolePermissionMapper).insertIgnore(20L, 1L);
        verify(rolePermissionMapper).insertIgnore(20L, 2L);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("SYSTEM_ROLE_ASSIGN_PERMISSION"), eq("system_role"), eq(20L), any(), afterCaptor.capture());
        assertThat(afterCaptor.getValue()).containsEntry("permissionCount", 2);
    }

    @Test
    void disabledRolesAreExcludedFromPermissionResolutionQueries() throws Exception {
        String roleMapper = Files.readString(
                Path.of("src/main/java/com/machining/quote/mapper/SystemRoleMapper.java"),
                StandardCharsets.UTF_8
        );
        String permissionMapper = Files.readString(
                Path.of("src/main/java/com/machining/quote/mapper/SystemPermissionMapper.java"),
                StandardCharsets.UTF_8
        );

        assertThat(roleMapper).contains("COALESCE(r.enabled, TRUE) = TRUE");
        assertThat(permissionMapper).contains("COALESCE(r.enabled, TRUE) = TRUE");
        assertThat(permissionMapper).contains("COALESCE(p.enabled, TRUE) = TRUE");
    }

    private SystemRole role(Long id, String code) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setEnabled(true);
        role.setDeleted(false);
        return role;
    }

    private SystemPermission permission(Long id, String code) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setPermissionName(code);
        permission.setEnabled(true);
        return permission;
    }
}

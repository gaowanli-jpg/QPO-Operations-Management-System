package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.MenuTreeDTO;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.AuthDataCenterSsoNonceMapper;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.JwtService;
import com.machining.quote.service.PermissionService;
import com.machining.quote.service.auth.DataCenterSsoAuthService;
import com.machining.quote.service.auth.DataCenterSsoConfigResolver;
import com.machining.quote.service.auth.DataCenterSsoRuntimeConfig;
import com.machining.quote.service.auth.DataCenterSsoSignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCenterSsoControllerTest {
    private static final DataCenterSsoRuntimeConfig RUNTIME_CONFIG = new DataCenterSsoRuntimeConfig(
            "DB",
            true,
            "QPO",
            "shared-secret",
            120,
            10,
            "/dashboard",
            List.of("/", "/dashboard", "/orders")
    );

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void controllerAndInterceptorExposePublicDataCenterSsoExchangeEndpoint() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "AuthController.java"
        ), StandardCharsets.UTF_8);
        String interceptor = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "config", "AuthInterceptor.java"
        ), StandardCharsets.UTF_8);

        assertThat(controller)
                .contains("DataCenterSsoAuthService")
                .contains("@PostMapping(\"/datacenter-sso/exchange\")")
                .contains("exchangeDataCenterSso");
        assertThat(interceptor)
                .contains("/api/auth/datacenter-sso/exchange");
    }

    @Test
    void successfulExchangeReturnsExistingQpoLoginPayloadShape() {
        Fixture fixture = fixture();
        DataCenterSsoExchangeReq req = signedRequest("admin", "nonce-ok");
        ErpUser user = user("admin", "ADMIN", true);
        when(fixture.userMapper.selectByUsernameForUpdate("admin")).thenReturn(user);
        when(fixture.nonceMapper.insertIgnore(any())).thenReturn(1);
        when(fixture.jwtService.createToken(any(AuthContext.SessionUser.class))).thenReturn("jwt-admin");
        when(fixture.permissionService.getPermissionInfo(7, "ADMIN")).thenReturn(permissionInfo(
                List.of("SUPER_ADMIN"),
                List.of("system:config:view", "market:rfq:view"),
                List.of(menu("/market"))
        ));

        Map<String, Object> result = fixture.ssoService.exchange(req);

        assertThat(result).containsEntry("success", true).containsEntry("token", "jwt-admin");
        assertThat(result.get("user"))
                .isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                        .containsEntry("username", "admin")
                        .containsEntry("displayName", "Admin User")
                        .containsEntry("role", "ADMIN"));
        assertThat(result.get("roles")).isEqualTo(List.of("SUPER_ADMIN"));
        assertThat(result.get("permissions")).isEqualTo(List.of("system:config:view", "market:rfq:view"));
        assertThat(result.get("menus")).isInstanceOfSatisfying(List.class, menus -> assertThat(menus).hasSize(1));
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(fixture.userMapper).updateById(user);
        verify(fixture.auditService).record(
                eq("AUTH_DATACENTER_SSO_SUCCESS"),
                eq("erp_user"),
                eq(7),
                isNull(),
                any(Map.class),
                eq("AUTH"),
                eq("SUCCESS"),
                contains("admin"),
                isNull()
        );
    }

    @Test
    void exchangeRejectsMissingDisabledAndNoPermissionUsers() {
        Fixture missing = fixture();
        when(missing.nonceMapper.insertIgnore(any())).thenReturn(1);
        when(missing.userMapper.selectByUsernameForUpdate("missing")).thenReturn(null);

        assertThatThrownBy(() -> missing.ssoService.exchange(signedRequest("missing", "nonce-missing")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("user");
        verify(missing.auditService).record(eq("AUTH_DATACENTER_SSO_FAILED"), eq("auth"), isNull(), isNull(), any(Map.class), eq("AUTH"), eq("FAILED"), contains("missing"), any());

        Fixture disabled = fixture();
        when(disabled.nonceMapper.insertIgnore(any())).thenReturn(1);
        when(disabled.userMapper.selectByUsernameForUpdate("disabled")).thenReturn(user("disabled", "MARKET", false));

        assertThatThrownBy(() -> disabled.ssoService.exchange(signedRequest("disabled", "nonce-disabled")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("disabled");

        Fixture noPermission = fixture();
        when(noPermission.nonceMapper.insertIgnore(any())).thenReturn(1);
        when(noPermission.userMapper.selectByUsernameForUpdate("readonly")).thenReturn(user("readonly", "READONLY", true));
        when(noPermission.permissionService.getPermissionInfo(7, "READONLY")).thenReturn(new PermissionInfoDTO());

        assertThatThrownBy(() -> noPermission.ssoService.exchange(signedRequest("readonly", "nonce-no-permission")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("permission");
        verify(noPermission.jwtService, never()).createToken(any());
    }

    @Test
    void exchangeRejectsBadSignatureAndDuplicateNonce() {
        Fixture badSignature = fixture();
        DataCenterSsoExchangeReq signed = signedRequest("admin", "nonce-bad-signature");
        signed.setSignature("bad");

        assertThatThrownBy(() -> badSignature.ssoService.exchange(signed))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("signature");
        verify(badSignature.nonceMapper, never()).insertIgnore(any());

        Fixture duplicate = fixture();
        when(duplicate.nonceMapper.insertIgnore(any())).thenReturn(0);

        assertThatThrownBy(() -> duplicate.ssoService.exchange(signedRequest("admin", "nonce-duplicate")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonce");
        verify(duplicate.jwtService, never()).createToken(any());
    }

    private Fixture fixture() {
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        JwtService jwtService = mock(JwtService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditService auditService = mock(AuditService.class);
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "permissionService", permissionService);
        ReflectionTestUtils.setField(authService, "auditService", auditService);

        DataCenterSsoConfigResolver resolver = mock(DataCenterSsoConfigResolver.class);
        when(resolver.resolve()).thenReturn(RUNTIME_CONFIG);
        AuthDataCenterSsoNonceMapper nonceMapper = mock(AuthDataCenterSsoNonceMapper.class);
        DataCenterSsoAuthService ssoService = new DataCenterSsoAuthService();
        ReflectionTestUtils.setField(ssoService, "configResolver", resolver);
        ReflectionTestUtils.setField(ssoService, "signatureService", new DataCenterSsoSignatureService());
        ReflectionTestUtils.setField(ssoService, "nonceMapper", nonceMapper);
        ReflectionTestUtils.setField(ssoService, "userMapper", userMapper);
        ReflectionTestUtils.setField(ssoService, "authService", authService);
        ReflectionTestUtils.setField(ssoService, "auditService", auditService);
        return new Fixture(ssoService, userMapper, nonceMapper, jwtService, permissionService, auditService);
    }

    private DataCenterSsoExchangeReq signedRequest(String username, String nonce) {
        DataCenterSsoExchangeReq req = new DataCenterSsoExchangeReq();
        req.setAppCode("QPO");
        req.setUsername(username);
        req.setDisplayName(username + " display");
        req.setTimestamp(Instant.now().toEpochMilli());
        req.setNonce(nonce);
        req.setRedirect("/dashboard");
        req.setSignature(new DataCenterSsoSignatureService().sign(req, "shared-secret", "/dashboard"));
        return req;
    }

    private ErpUser user(String username, String role, boolean enabled) {
        ErpUser user = new ErpUser();
        user.setId(7);
        user.setUsername(username);
        user.setDisplayName("Admin User");
        user.setRole(role);
        user.setEnabled(enabled);
        user.setFailedLoginAttempts(2);
        return user;
    }

    private PermissionInfoDTO permissionInfo(List<String> roles, List<String> permissions, List<MenuTreeDTO> menus) {
        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setRoles(roles);
        info.setPermissions(permissions);
        info.setMenus(menus);
        info.setSuperAdmin(roles.contains("SUPER_ADMIN"));
        return info;
    }

    private MenuTreeDTO menu(String path) {
        MenuTreeDTO menu = new MenuTreeDTO();
        menu.setPath(path);
        menu.setTitle("menu");
        menu.setPermission("market:rfq:view");
        return menu;
    }

    private record Fixture(
            DataCenterSsoAuthService ssoService,
            ErpUserMapper userMapper,
            AuthDataCenterSsoNonceMapper nonceMapper,
            JwtService jwtService,
            PermissionService permissionService,
            AuditService auditService
    ) {
    }
}

package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.config.AuthInterceptor;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionSystemStage5Test {
    private static final Path CONTROLLER_DIR = Path.of("src", "main", "java", "com", "machining", "quote", "controller");

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void controllersUsePermissionPointAnnotationsInsteadOfRoleAnnotations() throws Exception {
        assertThat(Path.of("src", "main", "java", "com", "machining", "quote", "config", "RequirePermission.java"))
                .exists();

        try (Stream<Path> files = Files.walk(CONTROLLER_DIR)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(this::containsRequireRole)
                    .toList();

            assertThat(offenders)
                    .as("Stage 5 should migrate controller interface guards from roles to permission points")
                    .isEmpty();
        }
    }

    @Test
    void migratedControllerSourcesContainRepresentativePermissionPoints() throws Exception {
        String market = source("MarketController.java");
        String engineer = source("EngineerController.java");
        String dict = source("DictController.java");
        String quoteProcess = source("QuoteProcessController.java");
        String order = source("OrderManagementController.java");
        String admin = source("AdminController.java");
        String users = source("SystemUserController.java");
        String roles = source("SystemRoleController.java");
        String audit = source("AuditLogController.java");
        String internalLeadTime = source("InternalTargetLeadTimeController.java");
        String systemTask = source("SystemTaskController.java");
        String apsSource = source("ApsSourceController.java");

        assertThat(market)
                .contains("@RequirePermission(\"market:rfq:import\")")
                .contains("@RequirePermission(\"market:release:create\")")
                .contains("@RequirePermission(\"market:quote-process:export\")");
        assertThat(engineer)
                .contains("@RequirePermission(\"engineer:pricing:save-item\")")
                .contains("@RequirePermission(\"engineer:pricing:refresh\")")
                .contains("@RequirePermission(\"engineer:setup:save-config\")");
        assertThat(dict)
                .contains("@RequirePermission(\"engineer:dict:view\")")
                .contains("@RequirePermission(\"engineer:dict:material-manage\")")
                .contains("@RequirePermission(\"engineer:dict:process-manage\")")
                .contains("@RequirePermission(\"engineer:dict:fee-manage\")");
        assertThat(quoteProcess)
                .contains("@RequirePermission(\"market:quote-process:view\")")
                .contains("@RequirePermission(\"market:quote-process:update-lead-time\")");
        assertThat(order)
                .contains("@RequirePermission(\"market:order:view\")")
                .contains("@RequirePermission(\"common:customer:select\")")
                .contains("@RequirePermission(\"market:order:supplement-drawing\")")
                .contains("@RequirePermission(\"market:order:delete\")");
        assertThat(admin)
                .contains("@RequirePermission(\"system:user:view\")")
                .contains("@RequirePermission(\"market:template:view\")")
                .contains("@RequirePermission(\"market:template:upload\")");
        assertThat(users)
                .contains("@RequirePermission(\"system:user:view\")")
                .contains("@RequirePermission(\"system:user:create\")")
                .contains("@RequirePermission(\"system:user:update\")")
                .contains("@RequirePermission(\"system:user:reset-password\")")
                .contains("@RequirePermission(\"system:user:disable\")")
                .contains("@RequirePermission(\"system:user:delete\")");
        assertThat(roles)
                .contains("@RequirePermission(\"system:role:view\")")
                .contains("@RequirePermission(\"system:role:assign-permission\")")
                .contains("@RequirePermission(\"system:role:assign-data-scope\")");
        assertThat(audit)
                .contains("@RequirePermission(\"system:audit:view\")")
                .contains("@RequirePermission(\"system:audit:export\")")
                .contains("@RequirePermission(\"system:audit:retention\")");
        assertThat(internalLeadTime)
                .contains("@RequirePermission(\"market:release:view\")")
                .contains("@RequirePermission(\"market:release:create\")");
        assertThat(systemTask)
                .contains("@RequirePermission(\"engineer:pricing:refresh\")")
                .contains("@RequirePermission(\"market:quote-process:export\")")
                .contains("@RequirePermission(\"common:task:view\")");
        assertThat(apsSource)
                .contains("@RequirePermission(\"common:task:view\")");
    }

    @Test
    void interceptorRejectsAuthenticatedUserWhenPermissionIsMissing() throws Exception {
        AuthInterceptor interceptor = interceptor(permissionInfo(false, "market:rfq:view"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handler("importRfq"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(AuthContext.get()).isNull();
    }

    @Test
    void interceptorAllowsAuthenticatedUserWhenPermissionIsPresent() throws Exception {
        AuthInterceptor interceptor = interceptor(permissionInfo(false, "market:rfq:import"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handler("importRfq"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(AuthContext.get()).isNotNull();
    }

    @Test
    void interceptorKeepsSuperAdminBypassForPermissionGuards() throws Exception {
        AuthInterceptor interceptor = interceptor(permissionInfo(true));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handler("importRfq"));

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private AuthInterceptor interceptor(PermissionInfoDTO permissionInfo) {
        AuthService authService = mock(AuthService.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuthContext.SessionUser user = new AuthContext.SessionUser(7, "market", "Market", "MARKET");
        when(authService.findByToken("token")).thenReturn(user);
        when(authService.mustChangePassword(7)).thenReturn(false);
        when(permissionService.getPermissionInfo(7, "MARKET")).thenReturn(permissionInfo);

        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authEnabled", true);
        ReflectionTestUtils.setField(interceptor, "authService", authService);
        ReflectionTestUtils.setField(interceptor, "permissionService", permissionService);
        return interceptor;
    }

    private PermissionInfoDTO permissionInfo(boolean superAdmin, String... permissions) {
        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setSuperAdmin(superAdmin);
        info.setPermissions(List.of(permissions));
        info.setRoles(superAdmin ? List.of("SUPER_ADMIN") : List.of("MARKET_USER"));
        return info;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/market/import-rfq");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = SecuredController.class.getMethod(methodName);
        return new HandlerMethod(new SecuredController(), method);
    }

    private boolean containsRequireRole(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains("RequireRole");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String source(String fileName) throws Exception {
        return Files.readString(CONTROLLER_DIR.resolve(fileName), StandardCharsets.UTF_8);
    }

    static class SecuredController {
        @RequirePermission("market:rfq:import")
        public void importRfq() {
        }
    }
}

package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.SystemDept;
import com.machining.quote.entity.SystemPost;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.SystemDeptMapper;
import com.machining.quote.mapper.SystemPostMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.mapper.SystemUserRoleMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.UserAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileSelfServiceTest {
    private static final Path AUTH_INTERCEPTOR = Path.of(
            "src", "main", "java", "com", "machining", "quote", "config", "AuthInterceptor.java");
    private static final Path USER_ADMIN_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "UserAdminService.java");
    private static final Path AUTH_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "AuthController.java");
    private static final Path MY_PROFILE_UPDATE_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "MyProfileUpdateReq.java");
    private static final Path PROFILE_PLAN = Path.of(
            "..", "docs", "QPO权限用户体系", "07-个人入口与我的资料优化开发文档.md");

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void authInterceptorNoLongerHasForcedPasswordChangeGate() throws IOException {
        String source = Files.readString(AUTH_INTERCEPTOR, StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("PASSWORD_CHANGE_ALLOWED_PATHS")
                .doesNotContain("mustChangePassword")
                .doesNotContain("428")
                .doesNotContain("请先修改初始密码");
    }

    @Test
    void selfServiceProfileBoundaryIsFrozenInDevelopmentPlan() throws IOException {
        String plan = Files.readString(PROFILE_PLAN, StandardCharsets.UTF_8);

        assertThat(plan)
                .contains("GET /api/auth/me")
                .contains("PUT /api/auth/me/profile")
                .contains("只能更新当前登录用户自己的手机号和邮箱")
                .contains("不需要 `system:user:update` 权限")
                .contains("不新增可分配权限点")
                .contains("USER_PROFILE_UPDATE");
    }

    @Test
    void reusableUserProfileSourceAlreadyContainsOrganizationIdentityFields() throws IOException {
        String userAdminService = Files.readString(USER_ADMIN_SERVICE, StandardCharsets.UTF_8);
        String authController = Files.readString(AUTH_CONTROLLER, StandardCharsets.UTF_8);

        assertThat(userAdminService)
                .contains("row.put(\"phone\"")
                .contains("row.put(\"email\"")
                .contains("row.put(\"deptName\"")
                .contains("row.put(\"postName\"")
                .contains("row.put(\"roleNames\"");
        assertThat(authController)
                .contains("@GetMapping(\"/me\")")
                .contains("@PostMapping(\"/change-password\")");
    }

    @Test
    void authMeDelegatesToReusableCurrentUserProfileAssembler() throws IOException {
        String authController = Files.readString(AUTH_CONTROLLER, StandardCharsets.UTF_8);

        assertThat(authController)
                .contains("userAdminService.currentUserProfile(user.id())")
                .doesNotContain("\"forcePasswordChange\", authService.mustChangePassword(user.id())");
    }

    @Test
    void authControllerExposesSelfProfileUpdateEndpointWithoutAdminPermission() throws IOException {
        String authController = Files.readString(AUTH_CONTROLLER, StandardCharsets.UTF_8);

        assertThat(authController)
                .contains("@PutMapping(\"/me/profile\")")
                .contains("MyProfileUpdateReq")
                .contains("userAdminService.updateMyProfile(req)")
                .doesNotContain("@RequirePermission(\"system:user:update\")");
    }

    @Test
    void myProfileUpdateRequestOnlyAllowsContactFields() throws IOException {
        String source = Files.readString(MY_PROFILE_UPDATE_REQ, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("phone")
                .contains("email")
                .doesNotContain("deptId")
                .doesNotContain("postId")
                .doesNotContain("role")
                .doesNotContain("roleCodes")
                .doesNotContain("enabled");
    }

    @Test
    void currentUserProfileReturnsOrganizationAndRoleNames() throws Exception {
        UserAdminService service = new UserAdminService();
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        SystemUserRoleMapper systemUserRoleMapper = mock(SystemUserRoleMapper.class);
        SystemDeptMapper deptMapper = mock(SystemDeptMapper.class);
        SystemPostMapper postMapper = mock(SystemPostMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "systemUserMapper", systemUserMapper);
        ReflectionTestUtils.setField(service, "systemUserRoleMapper", systemUserRoleMapper);
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "postMapper", postMapper);

        ErpUser legacyUser = new ErpUser();
        legacyUser.setId(7);
        legacyUser.setUsername("market01");
        legacyUser.setDisplayName("市场张三");
        legacyUser.setRole("MARKET");

        SystemUser systemUser = new SystemUser();
        systemUser.setId(70L);
        systemUser.setLegacyUserId(7);
        systemUser.setRealName("张三");
        systemUser.setPhone("13800000000");
        systemUser.setEmail("market01@example.com");
        systemUser.setDeptId(2L);
        systemUser.setPostId(3L);

        SystemDept dept = new SystemDept();
        dept.setId(2L);
        dept.setDeptName("市场部");

        SystemPost post = new SystemPost();
        post.setId(3L);
        post.setPostName("市场专员");

        when(userMapper.selectById(7)).thenReturn(legacyUser);
        when(systemUserMapper.selectByLegacyUserId(7)).thenReturn(systemUser);
        when(systemUserRoleMapper.selectRoleCodesByUserId(70L)).thenReturn(List.of("MARKET_USER"));
        when(systemUserRoleMapper.selectRoleNamesByUserId(70L)).thenReturn(List.of("市场专员"));
        when(deptMapper.selectById(2L)).thenReturn(dept);
        when(postMapper.selectById(3L)).thenReturn(post);

        Map<String, Object> profile = invokeCurrentUserProfile(service, 7);

        assertThat(profile)
                .containsEntry("id", 7)
                .containsEntry("systemUserId", 70L)
                .containsEntry("username", "market01")
                .containsEntry("displayName", "市场张三")
                .containsEntry("realName", "张三")
                .containsEntry("phone", "13800000000")
                .containsEntry("email", "market01@example.com")
                .containsEntry("deptName", "市场部")
                .containsEntry("postName", "市场专员")
                .doesNotContainKey("forcePasswordChange");
        assertThat(profile.get("roleCodes")).isEqualTo(List.of("MARKET_USER"));
        assertThat(profile.get("roles")).isEqualTo(List.of("MARKET_USER"));
        assertThat(profile.get("roleNames")).isEqualTo(List.of("市场专员"));
    }

    @Test
    void currentUserProfileFallsBackToLegacyFieldsWithoutSystemUser() throws Exception {
        UserAdminService service = new UserAdminService();
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "systemUserMapper", systemUserMapper);

        ErpUser legacyUser = new ErpUser();
        legacyUser.setId(1);
        legacyUser.setUsername("admin");
        legacyUser.setDisplayName("管理员");
        legacyUser.setRole("ADMIN");

        when(userMapper.selectById(1)).thenReturn(legacyUser);
        when(systemUserMapper.selectByLegacyUserId(1)).thenReturn(null);

        Map<String, Object> profile = invokeCurrentUserProfile(service, 1);

        assertThat(profile)
                .containsEntry("id", 1)
                .containsEntry("systemUserId", null)
                .containsEntry("username", "admin")
                .containsEntry("displayName", "管理员")
                .containsEntry("role", "ADMIN")
                .doesNotContainKey("forcePasswordChange")
                .containsEntry("phone", null)
                .containsEntry("email", null)
                .containsEntry("deptName", null)
                .containsEntry("postName", null);
        assertThat(profile.get("roleCodes")).isEqualTo(List.of("SUPER_ADMIN"));
        assertThat(profile.get("roleNames")).isEqualTo(List.of("SUPER_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeCurrentUserProfile(UserAdminService service, Integer legacyUserId) throws Exception {
        Method method = UserAdminService.class.getMethod("currentUserProfile", Integer.class);
        return (Map<String, Object>) method.invoke(service, legacyUserId);
    }

    @Test
    void updateMyProfileUpdatesOnlyContactFieldsAndRecordsAudit() throws Exception {
        UserAdminService service = new UserAdminService();
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        SystemUserRoleMapper systemUserRoleMapper = mock(SystemUserRoleMapper.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "systemUserMapper", systemUserMapper);
        ReflectionTestUtils.setField(service, "systemUserRoleMapper", systemUserRoleMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        AuthContext.set(new AuthContext.SessionUser(7, "market01", "market01", "MARKET"));
        ErpUser legacyUser = legacyUser(7, "market01", "MARKET");
        SystemUser systemUser = systemUser(70L, 7, "old-phone", "old@example.com");
        systemUser.setDeptId(2L);
        systemUser.setPostId(3L);
        when(userMapper.selectByIdForUpdate(7)).thenReturn(legacyUser);
        when(systemUserMapper.selectByLegacyUserId(7)).thenReturn(systemUser);
        when(systemUserRoleMapper.selectRoleCodesByUserId(70L)).thenReturn(List.of("MARKET_USER"));
        when(systemUserRoleMapper.selectRoleNamesByUserId(70L)).thenReturn(List.of("Market User"));

        Map<String, Object> profile = invokeUpdateMyProfile(service, newProfileUpdateReq(" 13800000000 ", " new@example.com "));

        assertThat(systemUser.getPhone()).isEqualTo("13800000000");
        assertThat(systemUser.getEmail()).isEqualTo("new@example.com");
        assertThat(systemUser.getDeptId()).isEqualTo(2L);
        assertThat(systemUser.getPostId()).isEqualTo(3L);
        assertThat(profile)
                .containsEntry("phone", "13800000000")
                .containsEntry("email", "new@example.com")
                .containsEntry("deptId", 2L)
                .containsEntry("postId", 3L);
        verify(systemUserMapper).updateById(systemUser);
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("USER_PROFILE_UPDATE"),
                eq("system_user"),
                eq(70L),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );
        assertThat(beforeCaptor.getValue()).isEqualTo(Map.of("phone", "old-phone", "email", "old@example.com"));
        assertThat(afterCaptor.getValue()).isEqualTo(Map.of("phone", "13800000000", "email", "new@example.com"));
    }

    @Test
    void updateMyProfileCanClearPhoneAndEmail() throws Exception {
        UserAdminService service = new UserAdminService();
        ErpUserMapper userMapper = mock(ErpUserMapper.class);
        SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "systemUserMapper", systemUserMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        AuthContext.set(new AuthContext.SessionUser(7, "market01", "market01", "MARKET"));
        ErpUser legacyUser = legacyUser(7, "market01", "MARKET");
        SystemUser systemUser = systemUser(70L, 7, "13800000000", "old@example.com");
        when(userMapper.selectByIdForUpdate(7)).thenReturn(legacyUser);
        when(systemUserMapper.selectByLegacyUserId(7)).thenReturn(systemUser);

        Map<String, Object> profile = invokeUpdateMyProfile(service, newProfileUpdateReq(" ", ""));

        assertThat(systemUser.getPhone()).isNull();
        assertThat(systemUser.getEmail()).isNull();
        assertThat(profile).containsEntry("phone", null).containsEntry("email", null);
    }

    @Test
    void updateMyProfileRejectsInvalidContactFields() throws Exception {
        UserAdminService service = new UserAdminService();
        AuthContext.set(new AuthContext.SessionUser(7, "market01", "market01", "MARKET"));

        assertThatThrownBy(() -> invokeUpdateMyProfile(service, newProfileUpdateReq("abc", "valid@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
        assertThatThrownBy(() -> invokeUpdateMyProfile(service, newProfileUpdateReq("13800000000", "bad-email")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeUpdateMyProfile(UserAdminService service, Object req) throws Exception {
        Class<?> reqClass = Class.forName("com.machining.quote.dto.MyProfileUpdateReq");
        Method method = UserAdminService.class.getMethod("updateMyProfile", reqClass);
        try {
            return (Map<String, Object>) method.invoke(service, req);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    private Object newProfileUpdateReq(String phone, String email) throws Exception {
        Class<?> reqClass = Class.forName("com.machining.quote.dto.MyProfileUpdateReq");
        Object req = reqClass.getConstructor().newInstance();
        reqClass.getMethod("setPhone", String.class).invoke(req, phone);
        reqClass.getMethod("setEmail", String.class).invoke(req, email);
        return req;
    }

    private ErpUser legacyUser(Integer id, String username, String role) {
        ErpUser user = new ErpUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private SystemUser systemUser(Long id, Integer legacyUserId, String phone, String email) {
        SystemUser user = new SystemUser();
        user.setId(id);
        user.setLegacyUserId(legacyUserId);
        user.setUsername("market01");
        user.setDisplayName("market01");
        user.setPhone(phone);
        user.setEmail(email);
        user.setEnabled(true);
        user.setDeleted(false);
        return user;
    }

}

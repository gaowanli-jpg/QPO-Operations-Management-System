package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileSelfServiceTest {
    private static final Path AUTH_INTERCEPTOR = Path.of(
            "src", "main", "java", "com", "machining", "quote", "config", "AuthInterceptor.java");
    private static final Path USER_ADMIN_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "UserAdminService.java");
    private static final Path AUTH_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "AuthController.java");
    private static final Path PROFILE_PLAN = Path.of(
            "..", "docs", "QPO权限用户体系", "07-个人入口与我的资料优化开发文档.md");

    @Test
    void profileUpdateIsNotAllowedBeforeForcedPasswordChange() throws IOException {
        String source = Files.readString(AUTH_INTERCEPTOR, StandardCharsets.UTF_8);
        String whitelist = extractPasswordChangeWhitelist(source);

        assertThat(whitelist)
                .contains("/api/auth/change-password")
                .contains("/api/auth/me")
                .contains("/api/auth/permission-info")
                .doesNotContain("/api/auth/me/profile");
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

    private String extractPasswordChangeWhitelist(String source) {
        Matcher matcher = Pattern.compile(
                "PASSWORD_CHANGE_ALLOWED_PATHS\\s*=\\s*Set\\.of\\((.*?)\\);",
                Pattern.DOTALL
        ).matcher(source);
        assertThat(matcher.find())
                .as("AuthInterceptor should centralize paths allowed during forced password change")
                .isTrue();
        return matcher.group(1);
    }
}

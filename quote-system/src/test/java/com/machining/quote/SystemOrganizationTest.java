package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOrganizationTest {
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path SYSTEM_ROLE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SystemRoleService.java");
    private static final Pattern FALLBACK_PERMISSION_CODE = Pattern.compile("permission\\([^,]+,\\s*\"(?:MENU|BUTTON|API)\"\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern DATA_SCOPES = Pattern.compile("DATA_SCOPES\\s*=\\s*Set\\.of\\(([^;]+)\\);", Pattern.DOTALL);
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"");

    @Test
    void organizationPermissionCodesStayUniqueAndAssignable() throws IOException {
        Set<String> codes = fallbackPermissionCodes();

        assertThat(codes)
                .as("Organization identity permissions must be present in the backend fallback catalog")
                .contains(
                        "system:org:view",
                        "system:dept:view",
                        "system:dept:manage",
                        "system:post:view",
                        "system:post:manage"
                );
    }

    @Test
    void departmentAndPostPermissionsDoNotBecomeDataScopes() throws IOException {
        Set<String> scopes = dataScopes();

        assertThat(scopes)
                .as("QPO order data scopes stay customer/self/all/none; department and post are identity attributes")
                .containsExactlyInAnyOrder("ALL", "SELF", "CUSTOMER", "NONE");
        assertThat(scopes)
                .doesNotContain("DEPT", "DEPT_AND_CHILD", "CUSTOM", "system:dept:view", "system:post:view");
    }

    @Test
    void organizationFieldsAlreadyExistAsUserIdentityAttributes() throws IOException {
        String systemUser = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SystemUser.java"),
                StandardCharsets.UTF_8);
        String userSaveReq = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "dto", "UserSaveReq.java"),
                StandardCharsets.UTF_8);

        assertThat(systemUser)
                .contains("private Long deptId;")
                .contains("private Long postId;");
        assertThat(userSaveReq)
                .contains("private Long deptId;")
                .contains("private Long postId;");
    }

    private Set<String> fallbackPermissionCodes() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        Matcher matcher = FALLBACK_PERMISSION_CODE.matcher(source);
        Set<String> codes = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        while (matcher.find()) {
            String code = matcher.group(1);
            if (!codes.add(code)) {
                duplicates.add(code);
            }
        }
        assertThat(duplicates)
                .as("Fallback permission codes must remain unique")
                .isEmpty();
        return codes;
    }

    private Set<String> dataScopes() throws IOException {
        String source = Files.readString(SYSTEM_ROLE_SERVICE, StandardCharsets.UTF_8);
        Matcher scopesMatcher = DATA_SCOPES.matcher(source);
        assertThat(scopesMatcher.find())
                .as("SystemRoleService should centralize assignable data scope codes")
                .isTrue();

        Matcher valueMatcher = QUOTED_VALUE.matcher(scopesMatcher.group(1));
        Set<String> values = new LinkedHashSet<>();
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }
}

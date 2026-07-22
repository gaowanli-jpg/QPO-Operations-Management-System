package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSystemStage7Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    @Test
    void legacyRequireRoleAnnotationIsRemovedFromMainCode() throws Exception {
        assertThat(MAIN_JAVA.resolve(Path.of("com", "machining", "quote", "config", "RequireRole.java")))
                .doesNotExist();

        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> source(path).contains("RequireRole"))
                    .toList();

            assertThat(offenders)
                    .as("Stage 7 removes role-annotation compatibility from production code")
                    .isEmpty();
        }
    }

    @Test
    void authInterceptorUsesPermissionInfoOnlyForAuthorizationBypass() throws Exception {
        String interceptor = source(MAIN_JAVA.resolve(Path.of("com", "machining", "quote", "config", "AuthInterceptor.java")));

        assertThat(interceptor)
                .doesNotContain("resolveRequireRole")
                .doesNotContain("hasRole(")
                .doesNotContain("\"ADMIN\".equalsIgnoreCase")
                .contains("info.isSuperAdmin()")
                .contains("info.getPermissions()");
    }

    private String source(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

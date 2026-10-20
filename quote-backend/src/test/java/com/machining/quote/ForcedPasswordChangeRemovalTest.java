package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ForcedPasswordChangeRemovalTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Path V21 = MIGRATION_DIR.resolve("V21__remove_force_password_change.sql");
    private static final List<Path> CURRENT_DOCS = List.of(
            Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md"),
            Path.of("..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md"),
            Path.of("..", "docs", "QPO权限用户体系", "08-数据中心统一入口SSO登录开发文档.md"),
            Path.of("..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md")
    );

    @Test
    void runtimeJavaNoLongerReferencesForcedPasswordChange() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsForcedPasswordRuntimeReference)
                    .toList();

            assertThat(offenders)
                    .as("QPO 登录后不再有强制改密运行时语义")
                    .isEmpty();
        }
    }

    @Test
    void v21DropsLegacyForcedPasswordColumnsWithoutTouchingBaseline() throws IOException {
        String baseline = read(MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql"));
        String v21 = read(V21);

        assertThat(baseline)
                .as("历史 baseline 已执行，不为本次需求改写")
                .contains("force_password_change");
        assertThat(v21)
                .contains(
                        "ALTER TABLE erp_user DROP COLUMN IF EXISTS force_password_change",
                        "ALTER TABLE system_user DROP COLUMN IF EXISTS force_password_change")
                .doesNotContain("V1__qpo_baseline_schema");
    }

    @Test
    void currentPermissionAndSsoDocsUseLoginEnabledMeansUsablePolicy() throws IOException {
        for (Path doc : CURRENT_DOCS) {
            String source = read(doc);
            assertThat(source)
                    .as(doc + " should not describe forced password change as current behavior")
                    .doesNotContain("forcePasswordChange")
                    .doesNotContain("force_password_change")
                    .doesNotContain("强制改密")
                    .doesNotContain("428");
        }
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsForcedPasswordRuntimeReference(Path path) {
        try {
            return containsAny(read(path),
                    "forcePasswordChange",
                    "force_password_change",
                    "mustChangePassword",
                    "PASSWORD_CHANGE_ALLOWED_PATHS",
                    "请先修改初始密码");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

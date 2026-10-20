package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationGovernanceDocumentationTest {
    private static final Path PERMISSION_LIST_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_GUIDE_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path LOG_DEV_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");

    @Test
    void permissionDocsExplainOrganizationArchiveRoleGovernance() throws IOException {
        String permissionList = read(PERMISSION_LIST_DOC);
        String adminGuide = read(ADMIN_GUIDE_DOC);

        assertThat(permissionList)
                .contains("system:org:view")
                .contains("system:dept:view")
                .contains("system:post:view")
                .contains("组织档案授权");
        assertThat(adminGuide)
                .contains("只能维护用户组织资料")
                .contains("组织档案")
                .contains("部门管理")
                .contains("岗位管理");
    }

    @Test
    void loggingDocsListOrganizationAuditAndFileLogEvents() throws IOException {
        String opsDoc = read(LOG_OPS_DOC);
        String devDoc = read(LOG_DEV_DOC);

        assertThat(opsDoc)
                .contains("SYSTEM_DEPT_CREATE")
                .contains("SYSTEM_DEPT_UPDATE")
                .contains("SYSTEM_DEPT_DELETE")
                .contains("SYSTEM_POST_CREATE")
                .contains("SYSTEM_POST_UPDATE")
                .contains("SYSTEM_POST_DELETE")
                .contains("SYSTEM_USER_ORG_UPDATE");
        assertThat(devDoc)
                .contains("System governance action completed")
                .contains("Department created/updated/deleted")
                .contains("Post created/updated/deleted")
                .contains("User organization updated");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

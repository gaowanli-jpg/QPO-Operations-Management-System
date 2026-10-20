package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class U9CustomerMasterContractTest {
    private static final Path DOC_43 = Path.of("..", "docs", "QPO报价状态流转", "43-U9客户主数据与U9同步选项缓存开发文档.md");
    private static final Path U9_COMMON_ENTITY_CLIENT = Path.of("src", "main", "java", "com", "machining", "quote", "service", "u9", "U9CommonEntityClient.java");

    @Test
    void stageOneLocksU9CustomerEntityFieldsAndKnownCustomerProbe() throws IOException {
        String doc = read(DOC_43);

        assertThat(doc)
                .contains(
                        "阶段 1 已完成",
                        "UFIDA.U9.CBO.SCM.Customer.Customer",
                        "客户实体字段已锁定",
                        "客户编码字段：`Code`",
                        "客户名称字段：`Name`",
                        "客户有效状态字段：`Effective.IsEffective`",
                        "后台过滤：`Org.Code eq 101`",
                        "已确认 `GSS1` 可查询",
                        "`customerCode` 作为 U9 销售订单 `OrderBy` 来源",
                        "不在 QPO 暴露 U9 组织选择");
    }

    @Test
    void commonEntityClientKeepsSanitizingSensitiveErrors() throws IOException {
        String source = read(U9_COMMON_ENTITY_CLIENT);

        assertThat(source)
                .contains("sanitize(error.getMessage())")
                .contains("AUTHORIZATION_FIELD")
                .contains("SENSITIVE_ASSIGNMENT")
                .doesNotContain("Data.Headers", "Authorization: Bearer", "client_secret=");
    }

    @Test
    void stageOneDocumentDoesNotPersistRealSecrets() throws IOException {
        String doc = read(DOC_43);

        assertThat(doc)
                .doesNotContain("Authorization: Bearer " + "eyJ", "access_token=" + "eyJ", "client_secret=")
                .doesNotContainPattern("(?i)(password|secret|token)\\s*[:=]\\s*[A-Za-z0-9+/=_-]{16,}");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

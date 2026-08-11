package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMasterStage5DocumentationTest {
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path U9_CUSTOMER_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "43-U9客户主数据与U9同步选项缓存开发文档.md");

    @Test
    void permissionDocsCloseReadOnlyU9CustomerMasterGovernanceBoundary() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(permissionDoc)
                .contains(
                        "客户资料阶段 4 U9 只读收口",
                        "客户资料只读展示 U9 客户缓存",
                        "`system:customer:view` 控制 `/customers` 入口和客户资料分页查询",
                        "`system:customer:sync` 控制手动同步 U9 客户缓存",
                        "system:customer:create/update/delete` 已下线",
                        "不新增 `common:customer:*` 权限"
                )
                .doesNotContain("新增客户主数据")
                .doesNotContain("修改客户编码、客户简称、客户名称")
                .doesNotContain("删除未被订单或数据权限引用的客户资料");
    }

    @Test
    void loggingDocsCloseReadOnlyU9CustomerAuditAndNoiseRules() throws IOException {
        String logDoc = read(LOG_DOC);

        assertThat(logDoc)
                .contains(
                        "客户资料阶段 4 U9 只读收口",
                        "普通客户资料分页查询不写操作日志",
                        "客户选项查询不写操作日志",
                        "手动同步 U9 客户写入 `U9_CUSTOMER_SYNC`",
                        "u9_master_data_sync_run",
                        "不记录完整 U9 请求、响应、token、Authorization、Cookie、client secret 或 Data.Headers"
                )
                .doesNotContain("Customer master created customerId={}")
                .doesNotContain("Customer master updated customerId={}")
                .doesNotContain("Customer master deleted customerId={}");
    }

    @Test
    void u9CustomerDocMarksStage4AsCompletedAndTracksCurrentImplementationStatus() throws IOException {
        String doc = read(U9_CUSTOMER_DOC);

        assertThat(doc)
                .contains(
                        "设计状态：阶段 1-7 已完成",
                        "### 阶段 4：客户资料只读模块改造",
                        "状态：已完成",
                        "[x] 客户资料页面只能查看、筛选和同步",
                        "[x] 构造请求无法新增、编辑、删除客户",
                        "[x] 页面展示客户编码、客户名称、有效状态和同步时间"
                );
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

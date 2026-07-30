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
    private static final Path SALES_ORDER_DISCUSSION_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "10-销售订单直提流程方案讨论文档.md");

    @Test
    void permissionDocsCloseCustomerMasterGovernanceBoundary() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(permissionDoc)
                .contains(
                        "客户资料阶段 5 收口",
                        "客户资料属于系统主数据能力",
                        "`system:customer:view` 控制 `/customers` 入口和客户资料分页查询",
                        "`system:customer:create`、`system:customer:update`、`system:customer:delete` 控制客户资料写操作",
                        "不新增 `common:customer:*` 权限",
                        "旧页面客户选择继续沿用模块级 `*:customer-select` 权限",
                        "旧页面筛选和数据权限继续使用 `customerName` 兼容"
                );
    }

    @Test
    void loggingDocsCloseCustomerMasterAuditAndNoiseRules() throws IOException {
        String logDoc = read(LOG_DOC);

        assertThat(logDoc)
                .contains(
                        "客户资料阶段 5 收口",
                        "新增、编辑、删除写入操作日志",
                        "`CUSTOMER_MASTER_CREATE`、`CUSTOMER_MASTER_UPDATE`、`CUSTOMER_MASTER_DELETE`",
                        "普通分页查询、客户选项查询、客户选择弹窗不写操作日志、不新增业务 INFO",
                        "Customer master created customerId={} customerCode={}",
                        "Customer master updated customerId={} customerCode={}",
                        "Customer master deleted customerId={} customerCode={}",
                        "不记录完整请求体或敏感上下文"
                );
    }

    @Test
    void salesOrderDiscussionDocRecordsImplementedCustomerMasterBoundary() throws IOException {
        String discussionDoc = read(SALES_ORDER_DISCUSSION_DOC);

        assertThat(discussionDoc)
                .contains(
                        "### 2.7 阶段 5 更新：客户主数据已落地",
                        "客户主数据模块已经落地",
                        "`customerCode` 可作为后续 U9 `OrderBy` 来源",
                        "`customerName` 仍作为 QPO 订单筛选和数据权限兼容快照",
                        "销售订单单据类型编码暂不进客户主数据，后续页面默认 `SO01`",
                        "本阶段仍不让销售订单进入议价提单"
                );
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDocumentDocumentationTest {
    private static final Path PERMISSION_LIST = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_GUIDE = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_GUIDE = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_GUIDE = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");

    @Test
    void permissionDocsSeparateReleaseGenerationFromDeliveryDocuments() throws IOException {
        String permissionList = read(PERMISSION_LIST);
        String adminGuide = read(ADMIN_GUIDE);

        assertThat(permissionList)
                .contains(
                        "报价状态流转 Stage 6 / 提单表据模块拆分版",
                        "market:delivery-document:view",
                        "market:delivery-document:customer-select",
                        "market:delivery-document:order-select",
                        "market:delivery-document:export",
                        "只读账号默认不授予"
                )
                .doesNotContain("`market:release:view`：进入议价提单页面，查看候选零件、已生成提单行和已生成工艺表行");
        assertThat(adminGuide)
                .contains(
                        "报价状态流转 Stage 6 / 提单表据模块拆分版",
                        "提单表据",
                        "导出提单表据"
                )
                .doesNotContain("用户可以查看候选零件、已生成提单行和已生成工艺表行");
    }

    @Test
    void loggingDocsFreezeDeliveryDocumentAuditAndFileLogRules() throws IOException {
        String operationsGuide = read(LOG_OPS_GUIDE);
        String fileLogGuide = read(FILE_LOG_GUIDE);

        assertThat(operationsGuide)
                .contains(
                        "DELIVERY_TICKET_EXPORT",
                        "DELIVERY_PROCESS_SHEET_EXPORT",
                        "targetType` 为 `order_rfq",
                        "提单表据页面打开、客户/订单选择、摘要和分页查询不新增操作日志",
                        "Delivery document export"
                );
        assertThat(fileLogGuide)
                .contains(
                        "Delivery document export started",
                        "Delivery document export completed",
                        "Delivery document export rejected",
                        "Delivery document export failed",
                        "rfqId={} exportType={} rowCount={} durationMs={}"
                );
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

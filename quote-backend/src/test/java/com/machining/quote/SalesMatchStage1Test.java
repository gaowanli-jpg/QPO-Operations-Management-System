package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesMatchStage1Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V68__sales_match_module.sql");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");

    @Test
    void migrationAddsSalesMatchPermissionAndAssemblySchema() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "market:sales-match:view",
                        "market:sales-match:customer-select",
                        "market:sales-match:order-select",
                        "market:sales-match:apply",
                        "market:sales-match:file-preview",
                        "/sales-match",
                        "SalesMatch",
                        "assembly_relation_status",
                        "CREATE TABLE IF NOT EXISTS sales_order_item_assembly_relation",
                        "parent_item_id BIGINT NOT NULL REFERENCES sales_order_item(id)",
                        "child_item_id BIGINT NOT NULL REFERENCES sales_order_item(id)",
                        "qty_per_parent",
                        "sort_no",
                        "source_parent_item_id",
                        "source_child_item_id"
                )
                .doesNotContain(
                        "common:sales-match",
                        "sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id)"
                );
    }

    @Test
    void fallbackPermissionCatalogExposesSalesMatchMenuBetweenImportAndQuoteProcess() throws IOException {
        String permissionService = read(PERMISSION_SERVICE);

        assertThat(permissionService)
                .contains(
                        "permission(181L, \"MENU\", \"market:sales-match:view\", \"销售匹配\", \"/sales-match\", \"SalesMatch\", true, 120)",
                        "permission(182L, \"API\", \"market:sales-match:customer-select\", \"销售匹配选择客户\", null, null, false, 121, 181L)",
                        "permission(183L, \"API\", \"market:sales-match:order-select\", \"销售匹配选择订单\", null, null, false, 122, 181L)",
                        "permission(184L, \"BUTTON\", \"market:sales-match:apply\", \"确认销售匹配补全\", null, null, false, 123, 181L)",
                        "permission(185L, \"API\", \"market:sales-match:file-preview\", \"销售匹配预览图纸\", null, null, false, 124, 181L)",
                        "Map.entry(\"market:sales-match:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-match:apply\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))"
                );

        assertThat(permissionService.indexOf("\"market:rfq:view\"")).isLessThan(permissionService.indexOf("\"market:sales-match:view\""));
        assertThat(permissionService.indexOf("\"market:sales-match:view\"")).isLessThan(permissionService.indexOf("\"market:quote-process:view\""));
    }

    @Test
    void permissionDocumentationListsSalesMatchModule() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(permissionDoc)
                .contains(
                        "| 销售匹配 | `market:sales-match:view` |",
                        "| `market:sales-match:view` | 销售匹配 |",
                        "| `market:sales-match:customer-select` | 销售匹配选择客户 |",
                        "| `market:sales-match:order-select` | 销售匹配选择订单 |",
                        "| `market:sales-match:apply` | 确认销售匹配补全 |",
                        "| `market:sales-match:file-preview` | 销售匹配预览图纸 |",
                        "| `/sales-match` | MARKET, READONLY, ADMIN | market:sales-match:view |"
                )
                .doesNotContain("common:sales-match");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesMatchStage1Test {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path CONTROLLER_DIR = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller");
    private static final Path SERVICE_DIR = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service");
    private static final Path DTO_DIR = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");

    @Test
    void baselineOmitsManualSalesMatchPermissionsWithoutTouchingAssemblySchema() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match")
                .doesNotContain("common:sales-match");
        assertThat(schema)
                .contains(
                        "assembly_relation_status",
                        "CREATE TABLE sales_order_item_assembly_relation",
                        "parent_item_id bigint NOT NULL",
                        "child_item_id bigint NOT NULL",
                        "FOREIGN KEY (parent_item_id) REFERENCES sales_order_item(id) ON DELETE CASCADE",
                        "FOREIGN KEY (child_item_id) REFERENCES sales_order_item(id) ON DELETE CASCADE",
                        "qty_per_parent",
                        "sort_no",
                        "source_parent_item_id",
                        "source_child_item_id"
                );
    }

    @Test
    void backendNoLongerExposesManualSalesMatchControllerServiceOrDtos() throws IOException {
        assertThat(CONTROLLER_DIR.resolve("SalesOrderMatchController.java")).doesNotExist();
        assertThat(SERVICE_DIR.resolve("SalesOrderMatchService.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchApplyReq.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchApplyResp.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchApplyRow.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchItemRow.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchOrderRow.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchPreviewReq.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchPreviewResp.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("SalesOrderMatchPreviewRow.java")).doesNotExist();
    }

    @Test
    void fallbackPermissionCatalogAndDocsRemoveManualSalesMatchCodes() throws IOException {
        String permissionService = read(PERMISSION_SERVICE);
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(permissionService)
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match")
                .doesNotContain("SalesMatch")
                .doesNotContain("common:sales-match");
        assertThat(permissionDoc)
                .contains("\u4e0d\u65b0\u589e\u53ef\u5206\u914d `common:*` \u6743\u9650")
                .contains("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u81ea\u52a8\u5feb\u7167\u8865\u5168")
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match");
    }

    @Test
    void automaticSnapshotCompletionRemainsInternalToSalesOrderImport() throws IOException {
        String importService = read(SERVICE_DIR.resolve("SalesOrderQpoImportService.java"));
        String completionService = read(SERVICE_DIR.resolve("SalesOrderAutoSnapshotCompletionService.java"));

        assertThat(importService)
                .contains("SalesOrderAutoSnapshotCompletionService")
                .contains("completeImportedOrder")
                .doesNotContain("SalesOrderMatchService")
                .doesNotContain("applyImportedOrder");
        assertThat(completionService)
                .contains("selectLatestReleasedRepeatSources")
                .contains("salesOrderItemMapper.updateById(item)")
                .contains("completeImportedOrder")
                .doesNotContain("@RequestMapping")
                .doesNotContain("@RequirePermission")
                .doesNotContain("SALES_ORDER_MATCH_APPLY");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

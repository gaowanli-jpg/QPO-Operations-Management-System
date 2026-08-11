package com.machining.quote;

import com.machining.quote.entity.SalesOrderHeader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9ContractMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V5__refactor_sales_order_u9_sync_contract.sql");

    @Test
    void migrationBackfillsAndConstrainsStableDocumentNumbers() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "SET document_no = sales_order_no",
                        "WHERE NULLIF(btrim(document_no), '') IS NULL",
                        "ALTER COLUMN document_no SET NOT NULL",
                        "CREATE UNIQUE INDEX uk_sales_order_header_document_no",
                        "ON sales_order_header(document_no)")
                .doesNotContain("SET document_no = 'DOC-RFQ-");
        assertThat(migration.indexOf("SET document_no = sales_order_no"))
                .isLessThan(migration.indexOf("CREATE UNIQUE INDEX uk_sales_order_header_document_no"));
    }

    @Test
    void migrationAddsSelectionAttemptAndEndpointConfiguration() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "ADD COLUMN u9_seller_code varchar(100)",
                        "ADD COLUMN u9_seller_name varchar(200)",
                        "ADD COLUMN u9_sale_department_code varchar(100)",
                        "ADD COLUMN u9_sale_department_name varchar(200)",
                        "ADD COLUMN u9_sync_attempt_id varchar(64)",
                        "ADD COLUMN u9_sync_request_hash varchar(64)",
                        "ADD COLUMN u9_sync_started_at timestamp without time zone",
                        "ADD COLUMN u9_sync_finished_at timestamp without time zone",
                        "SET u9_sync_status = '未同步'",
                        "ALTER COLUMN u9_sync_status SET DEFAULT '未同步'",
                        "ALTER COLUMN u9_sync_status SET NOT NULL",
                        "'U9_COMMON_ENTITY_QUERY'",
                        "'/api/v1/u9/CommonEntity/Query'",
                        "WHERE endpoint_code = 'U9_SALES_ORDER_SYNC'",
                        "GREATEST(COALESCE(timeout_ms, 0), 60000)");
    }

    @Test
    void entityExposesSelectionAndSyncAttemptFields() throws NoSuchFieldException {
        assertField("u9SellerCode", String.class);
        assertField("u9SellerName", String.class);
        assertField("u9SaleDepartmentCode", String.class);
        assertField("u9SaleDepartmentName", String.class);
        assertField("u9SyncAttemptId", String.class);
        assertField("u9SyncRequestHash", String.class);
        assertField("u9SyncStartedAt", LocalDateTime.class);
        assertField("u9SyncFinishedAt", LocalDateTime.class);
    }

    private static void assertField(String name, Class<?> type) throws NoSuchFieldException {
        assertThat(SalesOrderHeader.class.getDeclaredField(name).getType()).isEqualTo(type);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

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
    private static final Path SCHEMA_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration",
            "V1__qpo_baseline_schema.sql");
    private static final Path SEED_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration",
            "V2__qpo_baseline_seed_data.sql");

    @Test
    void baselineConstrainsStableDocumentNumbers() throws IOException {
        String schema = read(SCHEMA_BASELINE);

        assertThat(schema)
                .contains(
                        "document_no character varying(100) NOT NULL",
                        "CREATE UNIQUE INDEX uk_sales_order_header_document_no",
                        "ON sales_order_header USING btree (document_no)")
                .doesNotContain("SET document_no = sales_order_no")
                .doesNotContain("SET document_no = 'DOC-RFQ-");
        assertThat(schema.indexOf("document_no character varying(100) NOT NULL"))
                .isLessThan(schema.indexOf("CREATE UNIQUE INDEX uk_sales_order_header_document_no"));
    }

    @Test
    void baselineAddsSelectionAttemptAndEndpointConfiguration() throws IOException {
        String schema = read(SCHEMA_BASELINE);
        String seed = read(SEED_BASELINE);

        assertThat(schema)
                .contains(
                        "u9_seller_code character varying(100)",
                        "u9_seller_name character varying(200)",
                        "u9_sale_department_code character varying(100)",
                        "u9_sale_department_name character varying(200)",
                        "u9_sync_attempt_id character varying(64)",
                        "u9_sync_request_hash character varying(64)",
                        "u9_sync_started_at timestamp without time zone",
                        "u9_sync_finished_at timestamp without time zone");
        assertThat(seed)
                .contains(
                        "'U9_COMMON_ENTITY_QUERY'",
                        "'/api/v1/u9/CommonEntity/Query'",
                        "'U9_SALES_ORDER_SYNC'",
                        "'/api/v1/u9/SO/Create'",
                        "'U9_COMMON_ENTITY_QUERY_V1'",
                        "'U9_SALES_ORDER_V1'")
                .contains(", 60000,");
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

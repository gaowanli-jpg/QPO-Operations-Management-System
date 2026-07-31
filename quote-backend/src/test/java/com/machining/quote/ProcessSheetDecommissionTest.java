package com.machining.quote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
class ProcessSheetDecommissionTest {
    private static final Path QPO_MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path QPO_MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
    private static final Path QPO_SNAPSHOT_DEFAULTS_MIGRATION = QPO_MIGRATIONS.resolve("V37__delivery_ticket_snapshot_defaults.sql");
    private static final Path APS_SOURCE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "aps", "ApsSourceService.java");
    private static final Path APS_LEGACY_SOURCE_ROOT = Path.of("..", "aps" + "-system");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void orderProcessSheetTableIsDroppedAfterMigrations() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'order_process_sheet'
                """, Integer.class);

        assertThat(tableCount).isZero();
    }

    @Test
    void qpoMainCodeNoLongerReferencesOrderProcessSheetModel() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(QPO_MAIN_JAVA)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path,
                            "OrderProcessSheet",
                            "OrderProcessSheetMapper",
                            "order_process_sheet",
                            "sourceProcessSheetId"))
                    .toList();
        }

        assertThat(offenders)
                .as("order_process_sheet is retired; QPO main code must use order_delivery_ticket snapshots only")
                .isEmpty();
    }

    @Test
    void qpoRepositoryNoLongerCarriesLocalApsSourceTree() {
        assertThat(APS_LEGACY_SOURCE_ROOT)
                .as("APS source has moved out of the QPO workspace; keep QPO tests scoped to QPO code")
                .doesNotExist();
    }

    @Test
    void apsSourceUsesDeliveryTicketProcessSnapshotOnly() throws IOException {
        String source = Files.readString(APS_SOURCE_SERVICE, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("ticket.getProcessRoute()")
                .contains("ORDER_DELIVERY_TICKET")
                .doesNotContain("item.getProcessRoute()")
                .doesNotContain("ITEM_QUOTE_VERSION")
                .doesNotContain("firstNumber(");
    }

    @Test
    void migrationBackfillsTicketSnapshotsBeforeDroppingRetiredTable() throws IOException {
        Path migration = QPO_MIGRATIONS.resolve("V36__decommission_order_process_sheet.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("UPDATE order_delivery_ticket")
                .contains("process_route = COALESCE")
                .contains("material_data = COALESCE")
                .contains("DROP TABLE IF EXISTS order_process_sheet");
    }

    @Test
    void migrationNormalizesMissingTicketSnapshotsWithoutFallback() throws IOException {
        assertThat(QPO_SNAPSHOT_DEFAULTS_MIGRATION).exists();
        String sql = Files.readString(QPO_SNAPSHOT_DEFAULTS_MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("process_route = '[]'::jsonb")
                .contains("material_data = '{}'::jsonb")
                .contains("ALTER COLUMN process_route SET DEFAULT '[]'::jsonb")
                .doesNotContain("item_quote_version.process_route");
    }

    private boolean containsAny(Path path, String... needles) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (source.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}

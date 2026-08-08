package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfqItemMaterialNoConfirmationStage1Test {
    private static final String MATERIAL_UNCONFIRMED = "\u672a\u786e\u8ba4";
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ITEM_QUOTE_VERSION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java");

    @Test
    void migrationAddsRfqItemMaterialConfirmationFieldsWithoutHistoricalBackfill() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "material_no character varying(120)",
                        "material_no_status character varying(30) DEFAULT '" + MATERIAL_UNCONFIRMED + "'::character varying NOT NULL",
                        "material_no_source character varying(30)",
                        "material_no_source_ticket_id bigint",
                        "material_no_synced_at timestamp without time zone",
                        "material_no_message text",
                        "CREATE INDEX idx_item_quote_version_material_status",
                        "CREATE INDEX idx_item_quote_version_material_no_id",
                        "CREATE INDEX idx_item_quote_version_material_source_ticket",
                        "WHERE (material_no_source_ticket_id IS NOT NULL)")
                .doesNotContain(
                        "UPDATE item_quote_version SET material_no = ticket.material_no",
                        "FROM order_delivery_ticket",
                        "COALESCE(ticket.material_no");
    }

    @Test
    void itemQuoteVersionEntityExposesMaterialConfirmationFields() throws IOException {
        String entity = read(ITEM_QUOTE_VERSION);

        assertThat(entity)
                .contains(
                        "import java.time.LocalDateTime;",
                        "private String materialNo;",
                        "private String materialNoStatus;",
                        "private String materialNoSource;",
                        "private Long materialNoSourceTicketId;",
                        "private LocalDateTime materialNoSyncedAt;",
                        "private String materialNoMessage;");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

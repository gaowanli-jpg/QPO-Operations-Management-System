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
            "src", "main", "resources", "db", "migration", "V86__rfq_item_material_no_confirmation.sql");
    private static final Path ITEM_QUOTE_VERSION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java");

    @Test
    void migrationAddsRfqItemMaterialConfirmationFieldsWithoutHistoricalBackfill() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "ALTER TABLE item_quote_version",
                        "ADD COLUMN IF NOT EXISTS material_no VARCHAR(120)",
                        "ADD COLUMN IF NOT EXISTS material_no_status VARCHAR(30) NOT NULL DEFAULT '" + MATERIAL_UNCONFIRMED + "'",
                        "ADD COLUMN IF NOT EXISTS material_no_source VARCHAR(30)",
                        "ADD COLUMN IF NOT EXISTS material_no_source_ticket_id BIGINT",
                        "ADD COLUMN IF NOT EXISTS material_no_synced_at TIMESTAMP",
                        "ADD COLUMN IF NOT EXISTS material_no_message TEXT",
                        "CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_status",
                        "CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_no_id",
                        "CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_source_ticket",
                        "WHERE material_no_source_ticket_id IS NOT NULL",
                        "COMMENT ON COLUMN item_quote_version.material_no_status")
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

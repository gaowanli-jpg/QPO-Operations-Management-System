package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLinkedDiscardMigrationTest {
    private static final Path ROOT = Path.of("src", "main").toAbsolutePath().normalize();

    @Test
    void migrationAddsIndependentSalesOrderAndDeliveryDiscardMetadata() throws Exception {
        Path migration = ROOT.resolve(Path.of("resources", "db", "migration", "V53__sales_order_item_linked_discard.sql"));
        assertThat(migration).exists();

        String source = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(source).contains(
                "ALTER TABLE sales_order_item",
                "linked_discarded boolean DEFAULT false NOT NULL",
                "linked_discarded_at timestamp",
                "linked_discarded_by bigint",
                "linked_discard_reason character varying(500)",
                "linked_u9_change_excluded boolean DEFAULT false NOT NULL",
                "linked_restored_at timestamp",
                "linked_restored_by bigint",
                "linked_restore_reason character varying(500)",
                "ALTER TABLE order_delivery_ticket",
                "ALTER TABLE order_delivery_ticket_allocation",
                "discard_source character varying(50)",
                "discard_source_sales_order_item_id bigint",
                "idx_sales_order_item_linked_discard",
                "idx_order_delivery_ticket_discard_source",
                "idx_delivery_allocation_discard_source")
                .doesNotContain("V1__", "V2__");
    }

    @Test
    void entitiesExposeLinkedDiscardStateAndDeliveryDiscardSource() throws Exception {
        String salesItem = read("entity", "SalesOrderItem.java");
        String ticket = read("entity", "OrderDeliveryTicket.java");
        String allocation = read("entity", "OrderDeliveryTicketAllocation.java");

        assertThat(salesItem).contains(
                "private Boolean linkedDiscarded;",
                "private LocalDateTime linkedDiscardedAt;",
                "private Long linkedDiscardedBy;",
                "private String linkedDiscardReason;",
                "private Boolean linkedU9ChangeExcluded;",
                "private LocalDateTime linkedRestoredAt;",
                "private Long linkedRestoredBy;",
                "private String linkedRestoreReason;");
        assertThat(ticket).contains(
                "private String discardSource;",
                "private Long discardSourceSalesOrderItemId;");
        assertThat(allocation).contains(
                "private String discardSource;",
                "private Long discardSourceSalesOrderItemId;");
    }

    private static String read(String folder, String fileName) throws Exception {
        Path path = ROOT.resolve(Path.of("java", "com", "machining", "quote", folder, fileName));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

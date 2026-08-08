package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteStatusWorkflowStage1Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V32__quote_status_workflow.sql");
    private static final Path DELIVERY_BATCH_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V34__delivery_batch_immutability.sql");

    @Test
    void migrationRenamesLegacyUnpricedStatusesWithoutTouchingQuotedOrders() throws IOException {
        String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("UPDATE order_rfq")
                .contains("SET status = '未核价'")
                .contains("WHERE status = '待工程部评估'")
                .contains("UPDATE item_quote_version")
                .contains("WHERE status = '待核价'");
        assertThat(migration)
                .as("历史已报价订单必须保持已报价，不能降级成已核价")
                .doesNotContain("WHERE status = '已报价'")
                .doesNotContain("SET status = '已核价'");
    }

    @Test
    void mainCodeNoLongerWritesLegacyUnpricedStatuses() throws IOException {
        List<Path> changedMainFlowFiles = List.of(
                MAIN_JAVA.resolve("controller/MarketController.java"),
                MAIN_JAVA.resolve("service/DeliveryService.java"),
                MAIN_JAVA.resolve("service/OrderManagementService.java"),
                MAIN_JAVA.resolve("service/EngineerService.java")
        );

        for (Path path : changedMainFlowFiles) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertThat(source)
                    .as(path + " must not write old order status")
                    .doesNotContain("setStatus(\"待工程部评估\")")
                    .doesNotContain("return \"待工程部评估\"");
            assertThat(source)
                    .as(path + " must not write old item status")
                    .doesNotContain("setStatus(\"待核价\")")
                    .doesNotContain(".set(\"status\", \"待核价\")");
        }
    }

    @Test
    void legacyOrderStatusOnlyRemainsInCompatibilityAndMigrations() throws IOException {
        List<Path> unexpected;
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            unexpected = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("domain", "QuoteStatusWorkflow.java")))
                    .filter(path -> contains(path, "待工程部评估"))
                    .toList();
        }

        assertThat(unexpected)
                .as("New writes and user-facing main code should use 未核价; legacy label belongs in compatibility helper only")
                .isEmpty();
    }

    @Test
    void deliveryTicketSnapshotsAreBackedByMigrationEntityAndApsCompatibility() throws IOException {
        assertThat(DELIVERY_BATCH_MIGRATION)
                .as("Stage 1 must add the delivery ticket snapshot migration")
                .exists();
        String migration = Files.readString(DELIVERY_BATCH_MIGRATION, StandardCharsets.UTF_8);
        String ticketEntity = Files.readString(MAIN_JAVA.resolve("entity/OrderDeliveryTicket.java"), StandardCharsets.UTF_8);
        String deliveryService = Files.readString(MAIN_JAVA.resolve("service/DeliveryService.java"), StandardCharsets.UTF_8);
        String apsSourceService = Files.readString(MAIN_JAVA.resolve("service/aps/ApsSourceService.java"), StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("ADD COLUMN IF NOT EXISTS process_route JSONB")
                .contains("ADD COLUMN IF NOT EXISTS material_data JSONB")
                .contains("ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4)")
                .contains("ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4)")
                .contains("idx_order_delivery_ticket_rfq_item_id");
        assertThat(ticketEntity)
                .contains("autoResultMap = true")
                .contains("private Object processRoute")
                .contains("private Object materialData")
                .contains("private Double materialGrossWeight")
                .contains("private Double partNetWeight");
        assertThat(deliveryService)
                .contains("ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()))")
                .contains("ticket.setMaterialData(item.getMaterialData())")
                .contains("ticket.setMaterialGrossWeight(item.getMaterialGrossWeight())")
                .contains("ticket.setPartNetWeight");
        assertThat(apsSourceService)
                .contains("ticket.getProcessRoute()")
                .contains("ORDER_DELIVERY_TICKET")
                .doesNotContain("ITEM_QUOTE_VERSION");
    }

    private boolean contains(Path path, String text) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}

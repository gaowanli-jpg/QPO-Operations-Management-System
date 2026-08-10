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
    private static final Path BASELINE_SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");

    @Test
    void baselineSchemaUsesCurrentUnpricedStatusDefaults() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(schema)
                .contains("CREATE TABLE order_rfq")
                .contains("CREATE TABLE item_quote_version")
                .contains("status character varying(50)")
                .doesNotContain("\u5f85\u5de5\u7a0b\u90e8\u8bc4\u4f30")
                .doesNotContain("WHERE status = '\u5df2\u62a5\u4ef7'")
                .doesNotContain("SET status = '\u5df2\u6838\u4ef7'");
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
            String source = read(path);
            assertThat(source)
                    .as(path + " must not write old order status")
                    .doesNotContain("setStatus(\"\u5f85\u5de5\u7a0b\u90e8\u8bc4\u4f30\")")
                    .doesNotContain("return \"\u5f85\u5de5\u7a0b\u90e8\u8bc4\u4f30\"");
            assertThat(source)
                    .as(path + " must not write old item status")
                    .doesNotContain("setStatus(\"\u5f85\u6838\u4ef7\")")
                    .doesNotContain(".set(\"status\", \"\u5f85\u6838\u4ef7\")");
        }
    }

    @Test
    void legacyOrderStatusOnlyRemainsInCompatibilityAndMigrations() throws IOException {
        List<Path> unexpected;
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            unexpected = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("domain", "QuoteStatusWorkflow.java")))
                    .filter(path -> contains(path, "\u5f85\u5de5\u7a0b\u90e8\u8bc4\u4f30"))
                    .toList();
        }

        assertThat(unexpected)
                .as("New writes and user-facing main code should use current unpriced labels")
                .isEmpty();
    }

    @Test
    void deliveryTicketSnapshotsAreBackedByBaselineEntityAndApsCompatibility() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String ticketEntity = read(MAIN_JAVA.resolve("entity/OrderDeliveryTicket.java"));
        String deliveryService = read(MAIN_JAVA.resolve("service/DeliveryService.java"));
        String apsSourceService = read(MAIN_JAVA.resolve("service/aps/ApsSourceService.java"));

        assertThat(schema)
                .contains("process_route jsonb")
                .contains("material_data jsonb")
                .contains("material_gross_weight numeric(14,4)")
                .contains("part_net_weight numeric(14,4)")
                .contains("idx_order_delivery_ticket_rfq_item_id");
        assertThat(ticketEntity)
                .contains("autoResultMap = true")
                .contains("private Object processRoute")
                .contains("private Object materialData")
                .contains("private Double materialGrossWeight")
                .contains("private Double partNetWeight");
        assertThat(deliveryService)
                .contains("ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()))")
                .contains("ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()))")
                .contains("ticket.setMaterialGrossWeight(item.getMaterialGrossWeight())")
                .contains("ticket.setPartNetWeight");
        assertThat(apsSourceService)
                .contains("ticket.getProcessRoute()")
                .contains("ORDER_DELIVERY_TICKET")
                .doesNotContain("ITEM_QUOTE_VERSION");
    }

    private static boolean contains(Path path, String text) {
        try {
            return read(path).contains(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

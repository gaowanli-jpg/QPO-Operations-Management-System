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

    private boolean contains(Path path, String text) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}

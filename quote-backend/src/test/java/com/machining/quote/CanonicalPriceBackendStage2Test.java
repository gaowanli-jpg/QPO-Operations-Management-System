package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalPriceBackendStage2Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    @Test
    void backendRuntimeNoLongerUsesLegacyFinalPriceFields() throws IOException {
        List<Path> legacyFiles = new ArrayList<>();
        try (var files = Files.walk(MAIN_JAVA)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path javaFile : javaFiles) {
                if (containsLegacyPriceReference(read(javaFile))) {
                    legacyFiles.add(javaFile);
                }
            }
        }
        assertThat(legacyFiles).isEmpty();
    }

    @Test
    void entitiesExposeOnlyCanonicalPriceFields() throws IOException {
        String rfqItem = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java"));
        String salesItem = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java"));

        assertThat(rfqItem)
                .contains(
                        "private Double taxIncludedUnitPrice;",
                        "private Double taxIncludedTotalPrice;",
                        "private Double untaxedUnitPrice;",
                        "private Double untaxedTotalPrice;")
                .doesNotContain("finalUnitPrice", "finalTotalPrice");
        assertThat(salesItem)
                .contains(
                        "private BigDecimal taxIncludedUnitPrice;",
                        "private BigDecimal taxIncludedTotalPrice;",
                        "private BigDecimal untaxedUnitPrice;",
                        "private BigDecimal untaxedTotalPrice;")
                .doesNotContain("finalUnitPrice", "finalTotalPrice");
    }

    @Test
    void rfqPricingReleaseAndRepeatMatchingUseTaxIncludedPrice() throws IOException {
        String engineerService = read(service("EngineerService.java"));
        String deliveryService = read(service("DeliveryService.java"));
        String quoteProcessService = read(service("QuoteProcessService.java"));
        String repeatSql = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper",
                "RepeatQuoteSourceSqlProvider.java"));

        assertThat(engineerService)
                .contains(
                        "setTaxIncludedUnitPrice",
                        "setTaxIncludedTotalPrice",
                        "getTaxIncludedUnitPrice")
                .doesNotContain("getFinalUnitPrice", "setFinalUnitPrice", "setFinalTotalPrice");
        assertThat(deliveryService)
                .contains("getTaxIncludedUnitPrice")
                .doesNotContain("getFinalUnitPrice");
        assertThat(quoteProcessService)
                .contains("taxIncludedUnitPrice", "taxIncludedTotalPrice")
                .doesNotContain("finalUnitPrice", "finalTotalPrice");
        assertThat(repeatSql)
                .contains("h.tax_included_unit_price IS NOT NULL")
                .doesNotContain("h.final_unit_price");
    }

    @Test
    void salesOrderImportAndPreviewExposeCanonicalPrices() throws IOException {
        String importService = read(service("SalesOrderQpoImportService.java"));
        String matchService = read(service("SalesOrderMatchService.java"));
        String matchRow = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SalesOrderMatchItemRow.java"));
        String u9Preview = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "SalesOrderU9SyncPreviewLine.java"));

        assertThat(importService)
                .contains(
                        "setTaxIncludedUnitPrice(line.getTaxIncludedUnitPrice())",
                        "setTaxIncludedTotalPrice(line.getTaxIncludedTotalPrice())")
                .doesNotContain("setFinalUnitPrice", "setFinalTotalPrice");
        assertThat(matchService)
                .contains("taxIncludedUnitPrice")
                .doesNotContain("finalUnitPrice", "setFinalUnitPrice");
        assertThat(matchRow)
                .contains("private BigDecimal taxIncludedUnitPrice;")
                .doesNotContain("finalUnitPrice");
        assertThat(u9Preview)
                .contains("private BigDecimal taxIncludedUnitPrice;")
                .doesNotContain("finalUnitPrice");
    }

    private static Path service(String name) {
        return Path.of("src", "main", "java", "com", "machining", "quote", "service", name);
    }

    private static boolean containsLegacyPriceReference(String source) {
        return source.contains("finalUnitPrice")
                || source.contains("finalTotalPrice")
                || source.contains("FinalUnitPrice")
                || source.contains("FinalTotalPrice")
                || source.contains("final_unit_price")
                || source.contains("final_total_price");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

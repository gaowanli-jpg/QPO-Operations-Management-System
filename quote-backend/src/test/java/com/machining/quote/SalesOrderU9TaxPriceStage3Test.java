package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9TaxPriceStage3Test {
    @Test
    void u9SyncUsesOnlyTaxIncludedUnitPrice() throws IOException {
        String source = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service",
                "SalesOrderU9SyncService.java"));

        assertThat(source)
                .contains(
                        "requiredTaxIncludedPrice(item)",
                        "item.getTaxIncludedUnitPrice()",
                        "line.setOrderPriceTC(price)",
                        "line.setFinallyPriceTC(price)",
                        "含税单价为空")
                .doesNotContain("preferredPrice(");
    }

    @Test
    void u9PreviewDtoExposesCanonicalTaxIncludedAmount() throws IOException {
        String source = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto",
                "SalesOrderU9SyncPreviewLine.java"));

        assertThat(source)
                .contains(
                        "private BigDecimal taxIncludedUnitPrice;",
                        "private BigDecimal taxIncludedTotalPrice;",
                        "private BigDecimal untaxedUnitPrice;",
                        "private BigDecimal untaxedTotalPrice;");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

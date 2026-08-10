package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReleaseSalesOrderStatusConsistencyTest {
    @Test
    void derivedSalesOrderItemsUseSalesOrderReleasedStatusInsteadOfRfqItemReleasedStatus() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/machining/quote/service/RfqReleaseSalesOrderService.java"), StandardCharsets.UTF_8);

        assertThat(service)
                .contains("item.setStatus(QuoteStatusWorkflow.RELEASED);")
                .doesNotContain("item.setStatus(ITEM_RELEASED);");
    }
}

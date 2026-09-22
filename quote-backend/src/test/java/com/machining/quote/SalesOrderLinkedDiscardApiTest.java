package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLinkedDiscardApiTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void controllerExposesSecuredLinkedDiscardAndRestoreEndpoints() throws Exception {
        String controller = read(MAIN.resolve(Path.of("controller", "SalesOrderManageController.java")));

        assertThat(controller).contains(
                "import com.machining.quote.dto.SalesOrderLinkedDiscardResult;",
                "import com.machining.quote.service.SalesOrderLinkedDiscardService;",
                "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/linked-discard\")",
                "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/linked-restore\")",
                "@RequirePermission(\"market:sales-order:update\")",
                "salesOrderLinkedDiscardService.discard",
                "salesOrderLinkedDiscardService.restore");
    }

    @Test
    void linkedServiceUsesStableSourceMetadataAndDoesNotMatchByPartNumber() throws Exception {
        String service = read(MAIN.resolve(Path.of("service", "SalesOrderLinkedDiscardService.java")));

        assertThat(service).contains(
                "SALES_ORDER_ITEM_LINKED",
                "source_order_id",
                "source_item_id",
                "getSourceTicketId",
                "PRODUCTION_REQUIRED",
                "IN_STOCK_LOCK",
                "IN_PRODUCTION_LOCK",
                "CALLBACK_PENDING",
                "setLinkedU9ChangeExcluded")
                .doesNotContain("part_no", "version");
    }

    @Test
    void manualDeliveryDiscardClearsLinkedOwnershipMetadata() throws Exception {
        String delivered = read(MAIN.resolve(Path.of("service", "DeliveredItemService.java")));
        String production = read(MAIN.resolve(Path.of("service", "ProductionItemService.java")));
        String locked = read(MAIN.resolve(Path.of("service", "LockedItemService.java")));

        for (String source : new String[]{delivered, production, locked}) {
            assertThat(source)
                    .contains("setDiscardSource(\"MANUAL\")")
                    .contains("setDiscardSource(null)")
                    .contains("setDiscardSourceSalesOrderItemId(null)");
        }
    }

    private String read(Path path) throws Exception {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

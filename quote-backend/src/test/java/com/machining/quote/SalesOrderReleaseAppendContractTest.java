package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderReleaseAppendContractTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void controllerExposesReleaseAppendApisWithDedicatedPermissions() throws Exception {
        String controller = Files.readString(MAIN.resolve(Path.of("controller", "SalesOrderReleaseAppendController.java")), StandardCharsets.UTF_8);

        assertThat(controller).contains(
                "@RequestMapping(\"/api/market/release-append\")",
                "@RequirePermission(\"market:release-append:customer-select\")",
                "@RequirePermission(\"market:release-append:order-select\")",
                "@RequirePermission(\"market:release-append:create\")",
                "@GetMapping(\"/customers\")",
                "@GetMapping(\"/sales-orders\")",
                "@PostMapping(value = \"/sales-orders/{salesOrderId}/items\""
        );
    }

    @Test
    void permissionCatalogContainsReleaseAppendModuleWithoutCommonPermission() throws Exception {
        String permissionService = Files.readString(MAIN.resolve(Path.of("service", "PermissionService.java")), StandardCharsets.UTF_8);

        assertThat(permissionService).contains(
                "market:release-append:view",
                "market:release-append:customer-select",
                "market:release-append:order-select",
                "market:release-append:create",
                "ReleaseAppendManage"
        );
        assertThat(permissionService).doesNotContain("common:release-append");
    }

    @Test
    void auditCatalogUsesSafeMarketActionForReleaseAppend() {
        assertThat(AuditService.inferModule("SALES_ORDER_APPEND_RELEASE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_APPEND_RELEASE")).isEqualTo("追加销售提单");
    }
}

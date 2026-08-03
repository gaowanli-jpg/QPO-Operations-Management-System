package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLegacyImportCleanupTest {
    private static final Path ROOT = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void legacySalesOrderU9ImportEndpointAndServiceAreRemoved() {
        assertThat(ROOT.resolve("controller/SalesOrderImportController.java")).doesNotExist();
        assertThat(ROOT.resolve("service/SalesOrderImportService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/SalesOrderExcelParser.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/SalesOrderDryRunResp.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/SalesOrderExcelParseResult.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/SalesOrderExcelRow.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/SalesOrderImportResp.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/SalesOrderImportResultRow.java")).doesNotExist();
    }

    @Test
    void salesOrderTemplateDownloadKeepsActiveEndpointWithoutLegacyPostImport() throws Exception {
        Path controller = ROOT.resolve("controller/SalesOrderImportTemplateController.java");
        String source = Files.readString(controller, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/market/sales-orders\")")
                .contains("@GetMapping(\"/import/template\")")
                .contains("@RequirePermission(\"market:rfq:import-sales-order\")")
                .doesNotContain("@PostMapping(\"/import\")")
                .doesNotContain("SalesOrderImportService");
    }
}

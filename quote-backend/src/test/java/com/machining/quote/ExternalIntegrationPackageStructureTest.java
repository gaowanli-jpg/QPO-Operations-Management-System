package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalIntegrationPackageStructureTest {
    private static final Path ROOT = Path.of("src/main/java/com/machining/quote");

    @Test
    void dedicatedExternalIntegrationFilesLiveUnderSystemNamedPackages() throws Exception {
        Map<String, String> expectedPackages = Map.ofEntries(
                Map.entry("controller/aps/ApsSourceController.java", "package com.machining.quote.controller.aps;"),
                Map.entry("service/aps/ApsSourceService.java", "package com.machining.quote.service.aps;"),
                Map.entry("service/u9/U9SalesOrderClient.java", "package com.machining.quote.service.u9;"),
                Map.entry("service/u9/U9TokenService.java", "package com.machining.quote.service.u9;"),
                Map.entry("service/mes/MesMaterialClient.java", "package com.machining.quote.service.mes;"),
                Map.entry("service/mes/MesMaterialSyncService.java", "package com.machining.quote.service.mes;"),
                Map.entry("service/wms/WmsAllocationClient.java", "package com.machining.quote.service.wms;"),
                Map.entry("service/wms/WmsAllocationService.java", "package com.machining.quote.service.wms;"),
                Map.entry("dto/u9/U9ApiResult.java", "package com.machining.quote.dto.u9;"),
                Map.entry("dto/u9/U9ResultDTO.java", "package com.machining.quote.dto.u9;"),
                Map.entry("dto/u9/U9SalesOrderHeadDTO.java", "package com.machining.quote.dto.u9;"),
                Map.entry("dto/u9/U9SalesOrderLineDTO.java", "package com.machining.quote.dto.u9;"),
                Map.entry("dto/u9/U9SalesOrderShipLineDTO.java", "package com.machining.quote.dto.u9;"),
                Map.entry("dto/mes/MesMaterialResolveReq.java", "package com.machining.quote.dto.mes;"),
                Map.entry("dto/mes/MesMaterialResolveResp.java", "package com.machining.quote.dto.mes;"),
                Map.entry("dto/wms/WmsAllocationReq.java", "package com.machining.quote.dto.wms;"),
                Map.entry("dto/wms/WmsAllocationResp.java", "package com.machining.quote.dto.wms;")
        );

        expectedPackages.forEach((relativePath, packageLine) -> {
            Path file = ROOT.resolve(relativePath);
            assertThat(file).exists();
            try {
                assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains(packageLine);
            } catch (Exception e) {
                throw new AssertionError("Unable to read " + file, e);
            }
        });
    }

    @Test
    void rootPackagesDoNotKeepDedicatedExternalIntegrationFiles() {
        assertThat(ROOT.resolve("controller/ApsSourceController.java")).doesNotExist();
        assertThat(ROOT.resolve("service/ApsSourceService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/U9SalesOrderClient.java")).doesNotExist();
        assertThat(ROOT.resolve("service/U9TokenService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/MesMaterialClient.java")).doesNotExist();
        assertThat(ROOT.resolve("service/MesMaterialSyncService.java")).doesNotExist();
        assertThat(ROOT.resolve("service/WmsAllocationClient.java")).doesNotExist();
        assertThat(ROOT.resolve("service/WmsAllocationService.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/U9ApiResult.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/U9ResultDTO.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/U9SalesOrderHeadDTO.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/U9SalesOrderLineDTO.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/U9SalesOrderShipLineDTO.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/MesMaterialResolveReq.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/MesMaterialResolveResp.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/WmsAllocationReq.java")).doesNotExist();
        assertThat(ROOT.resolve("dto/WmsAllocationResp.java")).doesNotExist();
    }
}

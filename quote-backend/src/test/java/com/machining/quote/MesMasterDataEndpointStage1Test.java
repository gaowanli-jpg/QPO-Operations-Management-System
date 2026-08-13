package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMasterDataEndpointStage1Test {
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");
    private static final Path ENDPOINT_REGISTRY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DataCenterEndpointRegistry.java");
    private static final Path DATA_CENTER_CLIENT = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DataCenterIntegrationClient.java");
    private static final Path PROCESS_ROUTE_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionProcessRouteDTO.java");
    private static final Path BOM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionBomDTO.java");
    private static final Path DISPATCH_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java");

    @Test
    void stageZeroNamesEndpointAndQueryGuardTestsBeforeImplementation() throws IOException {
        String doc = read(DESIGN_DOC);

        assertThat(doc)
                .contains(
                        "MesMasterDataEndpointStage1Test",
                        "MES_PROCESS_TYPE_LIST",
                        "MES_MATERIAL_PAGE",
                        "callGet(String endpointCode, Map<String, ?> queryParams, Class<T> responseType)",
                        "ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH",
                        "ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH");

        assertThat(read(ENDPOINT_REGISTRY))
                .contains("MES_MATERIAL_RESOLVE", "MES_PRODUCTION_DISPATCH", "U9_COMMON_ENTITY_QUERY");
        assertThat(read(DATA_CENTER_CLIENT))
                .contains("public <T> T call(String endpointCode, Object requestPayload, Class<T> responseType)");
    }

    @Test
    void stageZeroKeepsMesProductionDispatchContractOutOfScope() throws IOException {
        assertThat(read(PROCESS_ROUTE_DTO))
                .contains(
                        "private String processName;",
                        "private String processTypeCode;",
                        "public void setProcessCode(String processCode)",
                        "this.processTypeCode = trimToNull(processCode);",
                        "public void setDetailName(String detailName)",
                        "this.processName = trimToNull(detailName);");

        assertThat(read(BOM_DTO))
                .contains("private String itemCode;", "private BigDecimal quantity;");

        assertThat(read(DISPATCH_SERVICE))
                .contains(
                        "dto.setProcessCode(processCode);",
                        "dto.setDetailName(detailName);",
                        "allocation.getEngineeringMaterialCode()",
                        "allocation.getMaterialGrossWeight()",
                        "new MesProductionBomDTO(materialCode, BigDecimal.valueOf(grossWeightKg))");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

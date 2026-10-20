package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterSnapshotCharacterizationTest {
    private static final Path MAIN = Path.of("src/main/java/com/machining/quote");

    @Test
    void salesOrderProductionSnapshotCarriesCompleteEngineeringMaterialIdentity() throws IOException {
        String salesOrderItem = read("entity", "SalesOrderItem.java");
        String autoSnapshotCompletionService = read("service", "SalesOrderAutoSnapshotCompletionService.java");
        String rfqReleaseService = read("service", "RfqReleaseSalesOrderService.java");

        assertThat(salesOrderItem)
                .contains(
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialName;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;");
        assertThat(autoSnapshotCompletionService)
                .contains(
                        "copyEngineeringMaterialIdentityIfMissing(item, source);",
                        "item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));",
                        "item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));",
                        "item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));",
                        "item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));");
        assertThat(rfqReleaseService)
                .contains(
                        "item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));",
                        "item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));",
                        "item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));",
                        "item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));");
    }

    private String read(String folder, String file) throws IOException {
        return Files.readString(MAIN.resolve(Path.of(folder, file)), StandardCharsets.UTF_8);
    }
}

package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterStage9ExportExternalFieldsTest {
    private static final Path MAIN = Path.of("src/main/java/com/machining/quote");

    @Test
    void processSheetDtoAndBuildersExposeMaterialIdentitySnapshot() throws IOException {
        assertThat(read(MAIN.resolve(Path.of("dto", "ProcessSheetDTO.java"))))
                .contains(
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;");

        for (Path path : List.of(
                MAIN.resolve(Path.of("service", "DeliveryService.java")),
                MAIN.resolve(Path.of("service", "SalesOrderManageService.java")))) {
            assertThat(read(path))
                    .as("%s should copy material identity fields into process-sheet DTOs", path)
                    .contains(
                            "sheet.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());",
                            "sheet.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());",
                            "sheet.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());",
                            "sheet.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());");
        }
    }

    @Test
    void excelExportsAndTemplateExportsIncludeMaterialIdentityColumns() throws IOException {
        String excelExportService = read(MAIN.resolve(Path.of("service", "ExcelExportService.java")));
        assertThat(excelExportService)
                .contains(
                        "材料编码",
                        "材料类型",
                        "材质分类",
                        "ticket.getEngineeringMaterialCode()",
                        "ticket.getEngineeringMaterialTypeName()",
                        "ticket.getEngineeringMaterialCategory()",
                        "sheet.getEngineeringMaterialCode()",
                        "sheet.getEngineeringMaterialTypeName()",
                        "sheet.getEngineeringMaterialCategory()",
                        "item.getEngineeringMaterialCode()",
                        "item.getEngineeringMaterialTypeName()",
                        "item.getEngineeringMaterialCategory()");

        String quoteTemplateExportService = read(MAIN.resolve(Path.of("service", "QuoteTemplateExportService.java")));
        assertThat(quoteTemplateExportService)
                .contains(
                        "材料编码",
                        "材料类型",
                        "材质分类",
                        "setByHeader(row, columns.headers(), \"材料编码\", item.getEngineeringMaterialCode());",
                        "setByHeader(row, columns.headers(), \"材料类型\", item.getEngineeringMaterialTypeName());",
                        "setByHeader(row, columns.headers(), \"材质分类\", item.getEngineeringMaterialCategory());");
    }

    @Test
    void mesProductionDispatchUsesMaterialIdentitySummaryWithoutFullMaterialData() throws IOException {
        String requestDto = read(MAIN.resolve(Path.of("dto", "mes", "MesProductionDispatchReq.java")));
        assertThat(requestDto)
                .contains(
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;")
                .doesNotContain("private Object materialData;");

        String dispatchService = read(MAIN.resolve(Path.of("service", "mes", "MesProductionDispatchService.java")));
        assertThat(dispatchService)
                .contains(
                        "req.setEngineeringMaterialCode(allocation.getEngineeringMaterialCode());",
                        "req.setEngineeringMaterialTypeCode(allocation.getEngineeringMaterialTypeCode());",
                        "req.setEngineeringMaterialTypeName(allocation.getEngineeringMaterialTypeName());",
                        "req.setEngineeringMaterialCategory(allocation.getEngineeringMaterialCategory());")
                .doesNotContain("req.setMaterialData(allocation.getMaterialData());");
    }

    @Test
    void u9PreviewCanDisplayEngineeringMaterialCodeButU9IdentityRemainsMesMaterialNo() throws IOException {
        assertThat(read(MAIN.resolve(Path.of("dto", "SalesOrderU9SyncPreviewLine.java"))))
                .contains(
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;");

        String u9Service = read(MAIN.resolve(Path.of("service", "SalesOrderU9SyncService.java")));
        assertThat(u9Service)
                .contains(
                        "line.setEngineeringMaterialCode(item.getEngineeringMaterialCode());",
                        "line.setEngineeringMaterialTypeCode(item.getEngineeringMaterialTypeCode());",
                        "line.setEngineeringMaterialTypeName(item.getEngineeringMaterialTypeName());",
                        "line.setEngineeringMaterialCategory(item.getEngineeringMaterialCategory());",
                        "line.setItemCode(materialNo);",
                        "shipLine.setItemCode(materialNo);")
                .doesNotContain("line.setItemCode(item.getEngineeringMaterialCode())", "shipLine.setItemCode(item.getEngineeringMaterialCode())");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterSchemaCharacterizationTest {
    private static final Path DICT_MATERIAL = main("entity", "DictMaterial.java");
    private static final Path MATERIAL_DTO = main("dto", "MaterialMasterDTO.java");
    private static final Path ITEM = main("entity", "ItemQuoteVersion.java");
    private static final Path SALES_ITEM = main("entity", "SalesOrderItem.java");
    private static final Path TICKET = main("entity", "OrderDeliveryTicket.java");
    private static final Path ALLOCATION = main("entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ENGINEER_SERVICE = main("service", "EngineerService.java");
    private static final Path AUTO_SNAPSHOT_COMPLETION_SERVICE = main("service", "SalesOrderAutoSnapshotCompletionService.java");
    private static final Path RFQ_RELEASE_SERVICE = main("service", "RfqReleaseSalesOrderService.java");
    private static final Path DELIVERY_SERVICE = main("service", "DeliveryService.java");
    private static final Path MATERIAL_RESOLVER = main("util", "MaterialDataResolver.java");
    private static final Path WMS_RESERVATION_STATE_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsReservationStateService.java");
    private static final Path ITEM_PRICING = Path.of("..", "quote-frontend", "src", "components", "ItemPricing.vue");
    private static final Path MATERIAL_PICKER = Path.of(
            "..", "quote-frontend", "src", "components", "EngineeringOptionPickerDialog.vue");
    private static final Path DELIVERED_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "DeliveredItemManage.vue");
    private static final Path LOCKED_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "LockedItemManage.vue");
    private static final Path PRODUCTION_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "ProductionItemManage.vue");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");

    @Test
    void dictMaterialRuntimeEntityUsesCodeTypeAndCategoryIdentityFields() throws IOException {
        String entity = read(DICT_MATERIAL);

        assertThat(entity)
                .contains(
                        "@TableName(\"dict_material\")",
                        "private String materialCode;",
                        "private String materialName;",
                        "private Long materialTypeId;",
                        "private String materialCategory;",
                        "private Double density;",
                        "private Double plateDensity;",
                        "private Double platePrice;",
                        "private Double barDensity;",
                        "private Double barPrice;",
                        "private Double tubeDensity;",
                        "private Double tubePrice;",
                        "private String remarks;",
                        "private Boolean enabled;",
                        "private Integer sortNo;")
                .doesNotContain("private String materialType;");
    }

    @Test
    void dictMaterialAndDtoExposeMesSourceTrackingFields() throws IOException {
        assertThat(read(DICT_MATERIAL))
                .contains(
                        "private String sourceType;",
                        "private String mesExternalId;",
                        "private String mesMaterialType;",
                        "private String mesPurchaseType;",
                        "private String mesProductionType;",
                        "private String mesDocNum;",
                        "private String mesStandard;",
                        "private String mesWeight;",
                        "private String mesDensityRaw;",
                        "private LocalDateTime mesSyncedAt;");

        assertThat(read(MATERIAL_DTO))
                .contains(
                        "private String sourceType;",
                        "private String mesExternalId;",
                        "private String mesMaterialType;",
                        "private String mesPurchaseType;",
                        "private String mesProductionType;",
                        "private String mesDocNum;",
                        "private String mesStandard;",
                        "private String mesWeight;",
                        "private String mesDensityRaw;",
                        "private LocalDateTime mesSyncedAt;");
    }

    @Test
    void pricingSelectionAndCostResolutionUseMaterialCode() throws IOException {
        String itemPricing = read(ITEM_PRICING);
        String materialPicker = read(MATERIAL_PICKER);
        String engineerService = read(ENGINEER_SERVICE);

        assertThat(itemPricing)
                .contains(
                        ":selected-key=\"matForm.materialCode\"",
                        "materialCode: '',",
                        "matForm.materialName = material.materialName",
                        "matForm.materialCode = material.materialCode",
                        "materialOptions.value.find(item => item.materialCode === matForm.materialCode)",
                        "materialCode: matForm.materialCode",
                        "materialTypeCode: matForm.materialTypeCode",
                        "name: matForm.materialName")
                .doesNotContain("item.materialName === matForm.materialName");
        assertThat(materialPicker)
                .contains(
                        "prop=\"materialCode\" label=\"材料编码\"",
                        "prop=\"materialName\" label=\"材料名称\"",
                        "materialTypeCode",
                        "? (item?.materialCode ?? item?.id ?? '')",
                        "item?.materialCode",
                        "item?.materialName",
                        "item?.materialTypeName");
        assertThat(engineerService)
                .contains(
                        "String materialCode = stringValue(materialData.get(\"materialCode\"));",
                        "requireEnabledMaterialByCode(materialCode)",
                        "qw.apply(\"UPPER(material_code) = {0}\"",
                        "qw.eq(\"enabled\", true);",
                        "materialData.put(\"materialCode\", material.getMaterialCode());",
                        "materialData.put(\"materialTypeCode\", materialType == null ? null : materialType.getTypeCode());",
                        "materialData.put(\"density\", density == null ? 0.0 : density);",
                        "materialData.put(\"unitPrice\", unitPrice);",
                        "materialData.put(\"cost\", cost);")
                .doesNotContain("qw.eq(\"material_name\", materialName);");
    }

    @Test
    void quoteSavePersistsMaterialDataAndEngineeringMaterialIdentitySnapshot() throws IOException {
        String item = read(ITEM);
        String salesItem = read(SALES_ITEM);
        String ticket = read(TICKET);
        String allocation = read(ALLOCATION);
        String engineerService = read(ENGINEER_SERVICE);
        String resolver = read(MATERIAL_RESOLVER);

        assertThat(item)
                .contains(
                        "private String materialName;",
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialName;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;",
                        "private String materialShape;",
                        "private Object materialData;");
        assertThat(salesItem)
                .contains(
                        "private String materialName;",
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialName;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;",
                        "private String materialShape;",
                        "private Object materialData;");
        assertThat(ticket)
                .contains(
                        "private String materialName;",
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialName;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;",
                        "private String materialShape;",
                        "private Object materialData;");
        assertThat(allocation)
                .contains(
                        "private String materialName;",
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialName;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;",
                        "private String materialShape;",
                        "private Object materialData;");
        assertThat(engineerService)
                .contains(
                        "String engineeringMaterialCode = stringValue(materialData.get(\"materialCode\"));",
                        "String engineeringMaterialName = stringValue(materialData.get(\"materialName\"));",
                        "String engineeringMaterialTypeCode = stringValue(materialData.get(\"materialTypeCode\"));",
                        "String engineeringMaterialTypeName = stringValue(materialData.get(\"materialTypeName\"));",
                        "String engineeringMaterialCategory = stringValue(materialData.get(\"materialCategory\"));",
                        "item.setMaterialData(materialData);",
                        "item.setEngineeringMaterialCode(engineeringMaterialCode);",
                        "item.setEngineeringMaterialName(engineeringMaterialName);",
                        "item.setEngineeringMaterialTypeCode(engineeringMaterialTypeCode);",
                        "item.setEngineeringMaterialTypeName(engineeringMaterialTypeName);",
                        "item.setEngineeringMaterialCategory(engineeringMaterialCategory);",
                        "item.setMaterialShape(materialShape(materialData));");
        assertThat(resolver)
                .contains(
                        "public static String materialCode(ItemQuoteVersion item)",
                        "public static String materialTypeCode(ItemQuoteVersion item)",
                        "public static String materialTypeName(ItemQuoteVersion item)",
                        "public static String materialCategory(ItemQuoteVersion item)",
                        "normalizeMap(materialData).get(\"materialCode\")",
                        "normalizeMap(item.getMaterialData()).get(\"name\")",
                        "normalizeMap(materialData).get(\"shape\")",
                        "public static String effectiveSalesOrderMaterialName(SalesOrderItem item)");
    }

    @Test
    void materialSnapshotCurrentlyCopiesThroughRepeatSalesReleaseTicketAndWms() throws IOException {
        String autoSnapshotCompletionService = read(AUTO_SNAPSHOT_COMPLETION_SERVICE);
        String rfqReleaseService = read(RFQ_RELEASE_SERVICE);
        String deliveryService = read(DELIVERY_SERVICE);
        String wmsReservationStateService = read(WMS_RESERVATION_STATE_SERVICE);

        assertThat(autoSnapshotCompletionService)
                .contains(
                        "String engineeringMaterialName = resolveEngineeringMaterialName(source);",
                        "String materialShape = resolveMaterialShape(source);",
                        "copyEngineeringMaterialIdentityIfMissing(item, source);",
                        "item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));",
                        "item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));",
                        "item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));",
                        "item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));",
                        "item.setEngineeringMaterialName(engineeringMaterialName);",
                        "item.setMaterialShape(materialShape);",
                        "item.setMaterialData(deepCopySnapshot(source.getMaterialData()));");
        assertThat(rfqReleaseService)
                .contains(
                        "ticket.setEngineeringMaterialCode(MaterialDataResolver.materialCode(item));",
                        "ticket.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));",
                        "ticket.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(item));",
                        "ticket.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(item));",
                        "ticket.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(item));",
                        "ticket.setMaterialShape(MaterialDataResolver.materialShape(item));",
                        "ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));",
                        "item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));",
                        "item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));",
                        "item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));",
                        "item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));",
                        "item.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(source));",
                        "item.setMaterialShape(MaterialDataResolver.materialShape(source));",
                        "item.setMaterialData(deepCopySnapshot(source.getMaterialData()));");
        assertThat(deliveryService)
                .contains(
                        "ticket.setEngineeringMaterialCode(resolveSalesOrderEngineeringMaterialCode(item));",
                        "ticket.setEngineeringMaterialName(resolveSalesOrderEngineeringMaterialName(item));",
                        "ticket.setEngineeringMaterialTypeCode(resolveSalesOrderEngineeringMaterialTypeCode(item));",
                        "ticket.setEngineeringMaterialTypeName(resolveSalesOrderEngineeringMaterialTypeName(item));",
                        "ticket.setEngineeringMaterialCategory(resolveSalesOrderEngineeringMaterialCategory(item));",
                        "ticket.setMaterialShape(resolveSalesOrderMaterialShape(item));",
                        "ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));",
                        "sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());");
        assertThat(wmsReservationStateService)
                .contains(
                        "row.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());",
                        "row.setEngineeringMaterialName(ticket.getEngineeringMaterialName());",
                        "row.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());",
                        "row.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());",
                        "row.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());",
                        "row.setMaterialShape(ticket.getMaterialShape());",
                        "row.setMaterialData(deepCopySnapshot(ticket.getMaterialData()));");
    }

    @Test
    void importMaterialAndEngineeringMaterialAreSeparateFields() throws IOException {
        String item = read(ITEM);
        String salesItem = read(SALES_ITEM);
        String autoSnapshotCompletionService = read(AUTO_SNAPSHOT_COMPLETION_SERVICE);

        assertThat(item)
                .as("RFQ keeps imported/customer materialName separate from engineeringMaterialName chosen during pricing")
                .contains("private String materialName;", "private String engineeringMaterialName;");
        assertThat(salesItem)
                .as("Sales order keeps imported/customer materialName separate from engineeringMaterialName copied from production snapshot")
                .contains("private String materialName;", "private String engineeringMaterialName;");
        assertThat(autoSnapshotCompletionService)
                .contains(
                        "String engineeringMaterialName = resolveEngineeringMaterialName(source);",
                        "item.setEngineeringMaterialName(engineeringMaterialName);")
                .doesNotContain("item.setMaterialName(source.getMaterialName())");
    }

    @Test
    void deliveredProductionAndLockedPagesDisplayMaterialIdentityAndShapeSeparately() throws IOException {
        for (Path page : List.of(DELIVERED_PAGE, PRODUCTION_PAGE, LOCKED_PAGE)) {
            String source = read(page);
            assertThat(source)
                    .contains(
                            "label=\"材料类型\"",
                            "formatMaterialCode(row)",
                            "formatMaterialType(row)",
                            "formatMaterialName(row)",
                            "formatMaterialShape(row)",
                            "row?.engineeringMaterialCode",
                            "row?.engineeringMaterialTypeName")
                    .doesNotContain(
                            "formatMaterialShape(row.materialData)",
                            "data.shape || data.materialShape || data.materialType || '-'");
        }
    }

    private static Path main(String packageName, String fileName) {
        return Path.of("src", "main", "java", "com", "machining", "quote", packageName, fileName);
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

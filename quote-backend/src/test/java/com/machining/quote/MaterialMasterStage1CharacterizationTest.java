package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterStage1CharacterizationTest {
    private static final Path DICT_MATERIAL = main("entity", "DictMaterial.java");
    private static final Path ITEM = main("entity", "ItemQuoteVersion.java");
    private static final Path SALES_ITEM = main("entity", "SalesOrderItem.java");
    private static final Path TICKET = main("entity", "OrderDeliveryTicket.java");
    private static final Path ALLOCATION = main("entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ENGINEER_SERVICE = main("service", "EngineerService.java");
    private static final Path SALES_MATCH_SERVICE = main("service", "SalesOrderMatchService.java");
    private static final Path RFQ_RELEASE_SERVICE = main("service", "RfqReleaseSalesOrderService.java");
    private static final Path DELIVERY_SERVICE = main("service", "DeliveryService.java");
    private static final Path MATERIAL_RESOLVER = main("util", "MaterialDataResolver.java");
    private static final Path WMS_ALLOCATION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsAllocationService.java");
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
    void dictMaterialCurrentlyUsesNameAndLegacyTypeWithoutStableCode() throws IOException {
        String entity = read(DICT_MATERIAL);
        String schema = read(SCHEMA);
        String seed = read(SEED);

        assertThat(entity)
                .contains(
                        "@TableName(\"dict_material\")",
                        "private String materialName;",
                        "private String materialType;",
                        "private Double density;",
                        "private Double plateDensity;",
                        "private Double platePrice;",
                        "private Double barDensity;",
                        "private Double barPrice;",
                        "private Double tubeDensity;",
                        "private Double tubePrice;",
                        "private String remarks;")
                .doesNotContain("materialCode", "materialTypeId", "materialCategory");
        assertThat(schema)
                .contains(
                        "CREATE TABLE dict_material (",
                        "material_name character varying(100) NOT NULL",
                        "material_type character varying(50)",
                        "CREATE UNIQUE INDEX idx_dict_material_name_remarks ON dict_material USING btree (material_name, remarks)")
                .doesNotContain("CREATE TABLE dict_material_type", "material_code", "material_type_id", "material_category");
        assertThat(seed)
                .contains("INSERT INTO dict_material (id, material_name, material_type")
                .containsAnyOf("'钢铁'", "'铝'", "'塑料'")
                .doesNotContain("RAW_MATERIAL", "material_code", "material_category");
    }

    @Test
    void pricingSelectionAndCostResolutionCurrentlyUseMaterialName() throws IOException {
        String itemPricing = read(ITEM_PRICING);
        String materialPicker = read(MATERIAL_PICKER);
        String engineerService = read(ENGINEER_SERVICE);

        assertThat(itemPricing)
                .contains(
                        ":selected-key=\"matForm.materialName\"",
                        "const matForm = reactive({ materialName: '', shape: ''",
                        "matForm.materialName = material.materialName",
                        "materialOptions.value.find(item => item.materialName === matForm.materialName)",
                        "name: matForm.materialName")
                .doesNotContain("matForm.materialCode", "materialCode: matForm.materialCode");
        assertThat(materialPicker)
                .contains(
                        "prop=\"materialName\" label=\"材料名称\"",
                        "prop=\"materialType\" label=\"材料类型\"",
                        "? (item?.materialName ?? item?.id ?? '')",
                        "item?.materialName",
                        "item?.materialType")
                .doesNotContain("materialCode", "materialTypeCode");
        assertThat(engineerService)
                .contains(
                        "String materialName = stringValue(materialData.get(\"name\"));",
                        "qw.eq(\"material_name\", materialName);",
                        "materialData.put(\"density\", density);",
                        "materialData.put(\"unitPrice\", unitPrice);",
                        "materialData.put(\"cost\", cost);")
                .doesNotContain("materialData.get(\"materialCode\")", "qw.eq(\"material_code\"");
    }

    @Test
    void quoteSaveCurrentlyPersistsMaterialDataEngineeringMaterialNameAndShape() throws IOException {
        String item = read(ITEM);
        String salesItem = read(SALES_ITEM);
        String ticket = read(TICKET);
        String allocation = read(ALLOCATION);
        String engineerService = read(ENGINEER_SERVICE);
        String resolver = read(MATERIAL_RESOLVER);

        assertThat(item)
                .contains("private String materialName;", "private String engineeringMaterialName;", "private String materialShape;", "private Object materialData;")
                .doesNotContain("engineeringMaterialCode", "engineeringMaterialTypeCode", "engineeringMaterialCategory");
        assertThat(salesItem)
                .contains("private String materialName;", "private String engineeringMaterialName;", "private String materialShape;", "private Object materialData;")
                .doesNotContain("engineeringMaterialCode", "engineeringMaterialTypeCode", "engineeringMaterialCategory");
        assertThat(ticket)
                .contains("private String materialName;", "private String engineeringMaterialName;", "private String materialShape;", "private Object materialData;")
                .doesNotContain("engineeringMaterialCode", "engineeringMaterialTypeCode", "engineeringMaterialCategory");
        assertThat(allocation)
                .contains("private String materialName;", "private String engineeringMaterialName;", "private String materialShape;", "private Object materialData;")
                .doesNotContain("engineeringMaterialCode", "engineeringMaterialTypeCode", "engineeringMaterialCategory");
        assertThat(engineerService)
                .contains(
                        "String engineeringMaterialName = stringValue(materialData.get(\"name\"));",
                        "item.setMaterialData(materialData);",
                        "item.setEngineeringMaterialName(engineeringMaterialName);",
                        "item.setMaterialShape(materialShape(materialData));");
        assertThat(resolver)
                .contains(
                        "normalizeMap(item.getMaterialData()).get(\"name\")",
                        "normalizeMap(materialData).get(\"shape\")",
                        "public static String effectiveSalesOrderMaterialName(SalesOrderItem item)")
                .doesNotContain("materialCode", "materialTypeCode", "materialCategory");
    }

    @Test
    void materialSnapshotCurrentlyCopiesThroughRepeatSalesReleaseTicketAndWms() throws IOException {
        String salesMatchService = read(SALES_MATCH_SERVICE);
        String rfqReleaseService = read(RFQ_RELEASE_SERVICE);
        String deliveryService = read(DELIVERY_SERVICE);
        String wmsAllocationService = read(WMS_ALLOCATION_SERVICE);

        assertThat(salesMatchService)
                .contains(
                        "String engineeringMaterialName = resolveEngineeringMaterialName(source);",
                        "String materialShape = resolveMaterialShape(source);",
                        "item.setEngineeringMaterialName(engineeringMaterialName);",
                        "item.setMaterialShape(materialShape);",
                        "item.setMaterialData(source.getMaterialData());",
                        "fields.put(\"materialSnapshotAvailable\", current.getMaterialData() == null && source.getMaterialData() != null);")
                .doesNotContain("setEngineeringMaterialCode", "setEngineeringMaterialTypeCode", "setEngineeringMaterialCategory");
        assertThat(rfqReleaseService)
                .contains(
                        "ticket.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(item));",
                        "ticket.setMaterialShape(MaterialDataResolver.materialShape(item));",
                        "ticket.setMaterialData(item.getMaterialData());",
                        "item.setEngineeringMaterialName(MaterialDataResolver.engineeringMaterialName(source));",
                        "item.setMaterialShape(MaterialDataResolver.materialShape(source));",
                        "item.setMaterialData(source.getMaterialData());")
                .doesNotContain("setEngineeringMaterialCode", "setEngineeringMaterialTypeCode", "setEngineeringMaterialCategory");
        assertThat(deliveryService)
                .contains(
                        "ticket.setEngineeringMaterialName(resolveSalesOrderEngineeringMaterialName(item));",
                        "ticket.setMaterialShape(resolveSalesOrderMaterialShape(item));",
                        "ticket.setMaterialData(item.getMaterialData());",
                        "sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());")
                .doesNotContain("setEngineeringMaterialCode", "setEngineeringMaterialTypeCode", "setEngineeringMaterialCategory");
        assertThat(wmsAllocationService)
                .contains(
                        "row.setEngineeringMaterialName(ticket.getEngineeringMaterialName());",
                        "row.setMaterialShape(ticket.getMaterialShape());",
                        "row.setMaterialData(ticket.getMaterialData());")
                .doesNotContain("setEngineeringMaterialCode", "setEngineeringMaterialTypeCode", "setEngineeringMaterialCategory");
    }

    @Test
    void importMaterialAndEngineeringMaterialAreSeparateFields() throws IOException {
        String item = read(ITEM);
        String salesItem = read(SALES_ITEM);
        String salesMatchService = read(SALES_MATCH_SERVICE);

        assertThat(item)
                .as("RFQ keeps imported/customer materialName separate from engineeringMaterialName chosen during pricing")
                .contains("private String materialName;", "private String engineeringMaterialName;");
        assertThat(salesItem)
                .as("Sales order keeps imported/customer materialName separate from engineeringMaterialName copied from production snapshot")
                .contains("private String materialName;", "private String engineeringMaterialName;");
        assertThat(salesMatchService)
                .contains(
                        "fields.put(\"materialName\", protectedText(current.getMaterialName(), source.getMaterialName()));",
                        "row.setMaterialName(source.getMaterialName());",
                        "row.setEngineeringMaterialName(resolveEngineeringMaterialName(source));")
                .doesNotContain("item.setMaterialName(source.getMaterialName())");
    }

    @Test
    void deliveredProductionAndLockedPagesCurrentlyDisplayShapeUnderMaterialTypeLabel() throws IOException {
        for (Path page : List.of(DELIVERED_PAGE, PRODUCTION_PAGE, LOCKED_PAGE)) {
            String source = read(page);
            assertThat(source)
                    .contains(
                            "label=\"材料类型\"",
                            "formatMaterialShape(row.materialData)",
                            "data.shape || data.materialShape || data.materialType || '-'")
                    .doesNotContain("engineeringMaterialCode", "engineeringMaterialTypeName");
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

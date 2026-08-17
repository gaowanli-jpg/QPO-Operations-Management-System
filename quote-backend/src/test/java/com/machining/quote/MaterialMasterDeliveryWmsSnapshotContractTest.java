package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterDeliveryWmsSnapshotContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/machining/quote");
    private static final Path FRONTEND_COMPONENTS = Path.of("..", "quote-frontend", "src", "components");

    @Test
    void deliveryTicketAndAllocationDtosExposeEngineeringMaterialIdentity() throws IOException {
        for (Path path : List.of(
                MAIN.resolve(Path.of("entity", "OrderDeliveryTicket.java")),
                MAIN.resolve(Path.of("entity", "OrderDeliveryTicketAllocation.java")),
                MAIN.resolve(Path.of("dto", "DeliveredItemDTO.java")),
                MAIN.resolve(Path.of("dto", "LockedItemDTO.java")))) {
            assertThat(read(path))
                    .as("%s must expose the complete engineering material snapshot", path)
                    .contains(
                            "private String engineeringMaterialCode;",
                            "private String engineeringMaterialTypeCode;",
                            "private String engineeringMaterialTypeName;",
                            "private String engineeringMaterialCategory;",
                            "private String engineeringMaterialName;",
                            "private String materialShape;",
                            "private Object materialData;");
        }
    }

    @Test
    void deliveredAndLockedMappersSelectMaterialIdentitySnapshotColumns() throws IOException {
        String ticketMapper = read(MAIN.resolve(Path.of("mapper", "OrderDeliveryTicketMapper.java")));
        String allocationMapper = read(MAIN.resolve(Path.of("mapper", "OrderDeliveryTicketAllocationMapper.java")));

        assertThat(ticketMapper)
                .contains(
                        "ticket.engineering_material_code",
                        "ticket.engineering_material_type_code",
                        "ticket.engineering_material_type_name",
                        "ticket.engineering_material_category",
                        "property = \"engineeringMaterialCode\"",
                        "property = \"engineeringMaterialTypeCode\"",
                        "property = \"engineeringMaterialTypeName\"",
                        "property = \"engineeringMaterialCategory\"");
        assertThat(allocationMapper)
                .contains(
                        "alloc.engineering_material_code",
                        "alloc.engineering_material_type_code",
                        "alloc.engineering_material_type_name",
                        "alloc.engineering_material_category",
                        "property = \"engineeringMaterialCode\"",
                        "property = \"engineeringMaterialTypeCode\"",
                        "property = \"engineeringMaterialTypeName\"",
                        "property = \"engineeringMaterialCategory\"");
    }

    @Test
    void releaseAndWmsServicesCopyCompleteMaterialSnapshotsWithoutLoggingMaterialDataJson() throws IOException {
        String deliveryService = read(MAIN.resolve(Path.of("service", "DeliveryService.java")));
        String rfqReleaseService = read(MAIN.resolve(Path.of("service", "RfqReleaseSalesOrderService.java")));
        String wmsReservationStateService = read(MAIN.resolve(Path.of("service", "wms", "WmsReservationStateService.java")));

        assertThat(deliveryService)
                .contains(
                        "ticket.setEngineeringMaterialCode(resolveSalesOrderEngineeringMaterialCode(item));",
                        "ticket.setEngineeringMaterialTypeCode(resolveSalesOrderEngineeringMaterialTypeCode(item));",
                        "ticket.setEngineeringMaterialTypeName(resolveSalesOrderEngineeringMaterialTypeName(item));",
                        "ticket.setEngineeringMaterialCategory(resolveSalesOrderEngineeringMaterialCategory(item));",
                        "ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));");
        assertThat(rfqReleaseService)
                .contains(
                        "ticket.setEngineeringMaterialCode(MaterialDataResolver.materialCode(item));",
                        "ticket.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(item));",
                        "ticket.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(item));",
                        "ticket.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(item));",
                        "ticket.setMaterialData(deepCopySnapshot(item.getMaterialData()));");
        assertThat(wmsReservationStateService)
                .contains(
                        "row.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());",
                        "row.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());",
                        "row.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());",
                        "row.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());",
                        "row.setMaterialData(deepCopySnapshot(ticket.getMaterialData()));")
                .doesNotContain(
                        "summary.put(\"materialData\"",
                        "log.info(\"{}\", ticket.getMaterialData()",
                        "log.error(\"{}\", ticket.getMaterialData()");
    }

    @Test
    void deliveredProductionAndLockedPagesShowMaterialCodeTypeNameAndShapeSeparately() throws IOException {
        for (String component : List.of("DeliveredItemManage.vue", "ProductionItemManage.vue", "LockedItemManage.vue")) {
            String source = read(FRONTEND_COMPONENTS.resolve(component));
            assertThat(source)
                    .as("%s must show material identity fields separately", component)
                    .contains(
                            "formatMaterialCode(row)",
                            "formatMaterialType(row)",
                            "formatMaterialName(row)",
                            "formatMaterialShape(row)",
                            "row?.engineeringMaterialCode",
                            "row?.engineeringMaterialTypeName",
                            "row?.engineeringMaterialTypeCode")
                    .doesNotContain("formatMaterialShape(row.materialData)", "formatMaterialShape(selectedTicketDetail.materialData)");
            assertThat(source)
                    .as("material shape helper must not treat legacy materialType as shape")
                    .doesNotContain("data.materialType || '-'");
        }
    }

    @Test
    void productionOrderNumberIsNotPartOfMaterialOrWmsSnapshots() throws IOException {
        String camel = "wms" + "ProductionOrderNo";
        String snake = "wms_" + "production_order_no";

        for (Path path : List.of(
                MAIN.resolve(Path.of("entity", "OrderDeliveryTicketAllocation.java")),
                MAIN.resolve(Path.of("dto", "LockedItemDTO.java")),
                MAIN.resolve(Path.of("dto", "LockedItemQueryReq.java")),
                MAIN.resolve(Path.of("mapper", "OrderDeliveryTicketAllocationMapper.java")),
                MAIN.resolve(Path.of("service", "LockedItemService.java")),
                FRONTEND_COMPONENTS.resolve("LockedItemManage.vue"),
                FRONTEND_COMPONENTS.resolve("ProductionItemManage.vue"))) {
            assertThat(read(path))
                    .as("%s should follow the new in-production coverage contract", path)
                    .doesNotContain(camel, snake);
        }
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

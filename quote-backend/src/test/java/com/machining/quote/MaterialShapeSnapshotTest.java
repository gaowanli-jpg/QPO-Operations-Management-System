package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialShapeSnapshotTest {
    private static final Path ITEM = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "ItemQuoteVersion.java");
    private static final Path SALES_ITEM = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");
    private static final Path TICKET = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path ALLOCATION = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ENGINEER_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "EngineerService.java");
    private static final Path DELIVERY_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "DeliveryService.java");
    private static final Path SALES_MATCH_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderMatchService.java");
    private static final Path SALES_REPEAT_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderRepeatEnrichmentService.java");
    private static final Path MES_REQ = Path.of("src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionDispatchReq.java");
    private static final Path MES_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java");
    private static final Path MES_INTERFACE_DOC = Path.of("..", "docs", "QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md");

    @Test
    void materialShapeIsExplicitOnAllProductionSnapshotEntities() throws IOException {
        assertThat(read(ITEM)).contains("private String materialShape;");
        assertThat(read(SALES_ITEM)).contains("private String materialShape;");
        assertThat(read(TICKET)).contains("private String materialShape;");
        assertThat(read(ALLOCATION)).contains("private String materialShape;");
    }

    @Test
    void materialShapeIsCopiedThroughQuoteTicketAllocationAndSalesMatchFlows() throws IOException {
        assertThat(read(ENGINEER_SERVICE))
                .contains("item.setMaterialShape(materialShape(materialData))");
        assertThat(read(DELIVERY_SERVICE))
                .contains(
                        "ticket.setMaterialShape(resolveMaterialShape(item))",
                        "ticket.setMaterialShape(resolveSalesOrderMaterialShape(item))"
                );
        assertThat(read(SALES_MATCH_SERVICE))
                .contains(
                        "String materialShape = resolveMaterialShape(source)",
                        "item.setMaterialShape(materialShape)"
                );
        assertThat(read(SALES_REPEAT_SERVICE))
                .contains("current.setMaterialShape(resolveMaterialShape(source))");
    }

    @Test
    void mesProductionDispatchSendsMaterialShapeAsFirstClassField() throws IOException {
        assertThat(read(MES_REQ)).contains("private String materialShape;");
        assertThat(read(MES_SERVICE)).contains("req.setMaterialShape(allocation.getMaterialShape())");
        assertThat(read(MES_INTERFACE_DOC))
                .contains(
                        "`materialShape`",
                        "材料形态",
                        "板材 / 棒材 / 管材",
                        "`materialData.shape`"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

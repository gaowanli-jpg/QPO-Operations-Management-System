package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRouteBaselineStage1CharacterizationTest {
    private static final Path STAGE_38_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "38-数据库基线重建与两级工艺主数据全链路重构开发文档.md");

    @Test
    void currentProcessDictionaryIsSingleLayerWithTieredPricesBeforeRefactor() throws IOException {
        String entity = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "DictProcess.java"));
        String schema = read(Path.of("src", "main", "resources", "schema.sql"));
        String baselineMigration = read(Path.of("src", "main", "resources", "db", "migration", "V1__baseline.sql"));

        assertThat(entity)
                .as("Stage 1 freezes the legacy process table shape before replacing it with category/detail tables")
                .contains(
                        "@TableName(\"dict_process\")",
                        "private String processName;",
                        "private String calcType;",
                        "@TableField(\"price_1_5\")",
                        "private Double price1To5;",
                        "@TableField(\"price_3000_plus\")",
                        "private Double price3000Plus;")
                .doesNotContain("processCode", "processDetailId", "hourlyRate");
        assertThat(schema)
                .contains(
                        "CREATE TABLE IF NOT EXISTS dict_process",
                        "process_name VARCHAR(50)",
                        "price_1_5 NUMERIC(10, 2)",
                        "price_3000_plus NUMERIC(10, 2)",
                        "CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_process_name ON dict_process(process_name)");
        assertThat(baselineMigration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS dict_process",
                        "price_1_5 NUMERIC(10, 2)",
                        "price_3000_plus NUMERIC(10, 2)");
    }

    @Test
    void currentPricingSaveUsesProcessNameAndFreezesUnitRateLineCostSnapshot() throws IOException {
        String submitReq = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "QuoteSubmitReq.java"));
        String engineerService = read(service("EngineerService.java"));

        assertThat(submitReq)
                .as("Current quote payload identifies a process by processName; later stages will replace this")
                .contains(
                        "private List<ProcessSubmitDTO> processRoute;",
                        "private String processName;",
                        "private Double inputVal;",
                        "private String calcType;",
                        "private Double unitRate;",
                        "private Double lineCost;")
                .doesNotContain("processDetailId", "processCode", "categoryName", "detailName", "hourlyRate", "unitWorkHours");
        assertThat(engineerService)
                .as("Current pricing resolves dict_process by process_name and writes the matched rate into the route snapshot")
                .contains(
                        "findProcess(process.getProcessName())",
                        "qw.eq(\"process_name\", processName)",
                        "double rate = getTieredRate(dictInfo, qty)",
                        "double lineCost = process.getInputVal() * rate",
                        "process.setCalcType(\"工时\")",
                        "process.setUnitRate(rate)",
                        "process.setLineCost(lineCost)",
                        "item.setProcessRoute(processRoute)");
        assertThat(engineerService)
                .contains(
                        "private double getTieredRate(DictProcess dp, int qty)",
                        "if (qty <= 5) return dp.getPrice1To5()",
                        "if (qty <= 3000) return dp.getPrice1000To3000()",
                        "return dp.getPrice3000Plus()");
    }

    @Test
    void currentProcessRouteSnapshotIsCopiedThroughReleaseWmsAndMesDispatch() throws IOException {
        String rfqReleaseService = read(service("RfqReleaseSalesOrderService.java"));
        String deliveryService = read(service("DeliveryService.java"));
        String salesMatchService = read(service("SalesOrderMatchService.java"));
        String wmsAllocationService = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsAllocationService.java"));
        String mesProductionService = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java"));
        String mesProductionReq = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionDispatchReq.java"));

        assertThat(rfqReleaseService)
                .contains(
                        "ticket.setProcessRoute(item.getProcessRoute())",
                        "item.setProcessRoute(source.getProcessRoute())");
        assertThat(deliveryService)
                .contains(
                        "ticket.setProcessRoute(item.getProcessRoute())",
                        "sheet.setProcessRoute(ticket.getProcessRoute())");
        assertThat(salesMatchService)
                .contains(
                        "if (item.getProcessRoute() == null)",
                        "item.setProcessRoute(source.getProcessRoute())",
                        "processSnapshotAvailable");
        assertThat(wmsAllocationService)
                .contains("row.setProcessRoute(ticket.getProcessRoute())");
        assertThat(mesProductionService)
                .contains("req.setProcessRoute(allocation.getProcessRoute())");
        assertThat(mesProductionReq)
                .contains("private Object processRoute;")
                .doesNotContain("MesProductionProcessRouteDTO");
    }

    @Test
    void stage38PlanMarksLegacyIdentityAndTierPricingAsTemporary() throws IOException {
        String plan = read(STAGE_38_DOC);

        assertThat(plan)
                .contains(
                        "当前工艺字典 `dict_process` 是单层工艺表",
                        "不再分级定价",
                        "旧分级价格字段和 `processName` 身份字段将被替换",
                        "删除分级价格取价方法",
                        "运行时代码不再查询 `dict_process`")
                .doesNotContain("必须保留分级价格", "以 APS 作为新设计目标");
    }

    @Test
    void apsProcessReadingIsHistoricalCompatibilityOnly() throws IOException {
        String agents = read(Path.of("..", "AGENTS.md"));
        String plan = read(STAGE_38_DOC);
        String apsSource = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "aps", "ApsSourceService.java"));

        assertThat(agents)
                .contains(
                        "APS is no longer maintained inside this QPO workspace",
                        "Do not use APS as the design target for new QPO integrations");
        assertThat(plan)
                .contains("APS 相关代码只作为历史兼容读取，不作为新设计目标");
        assertThat(apsSource)
                .as("The legacy APS reader may still parse old route keys, but Stage 38 must not design around it")
                .contains(
                        "row.get(\"unitRate\")",
                        "row.get(\"lineCost\")")
                .doesNotContain("dict_process_category", "dict_process_detail", "MesProductionProcessRouteDTO");
    }

    private static Path service(String name) {
        return Path.of("src", "main", "java", "com", "machining", "quote", "service", name);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

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
    void processDictionaryRuntimeUsesTwoLevelMasterWithoutTieredPrices() throws IOException {
        Path legacyEntity = Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "DictProcess.java");
        Path legacyMapper = Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper", "DictProcessMapper.java");
        String categoryEntity = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "DictProcessCategory.java"));
        String detailEntity = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "entity", "DictProcessDetail.java"));
        String schema = read(Path.of("src", "main", "resources", "schema.sql"));

        assertThat(legacyEntity).doesNotExist();
        assertThat(legacyMapper).doesNotExist();
        assertThat(categoryEntity)
                .contains(
                        "@TableName(\"dict_process_category\")",
                        "private String processCode;",
                        "private String categoryName;",
                        "private BigDecimal hourlyRate;")
                .doesNotContain("price1To5", "price3000Plus");
        assertThat(detailEntity)
                .contains(
                        "@TableName(\"dict_process_detail\")",
                        "private Long categoryId;",
                        "private String detailName;")
                .doesNotContain("price1To5", "price3000Plus");
        assertThat(schema)
                .contains(
                        "CREATE TABLE IF NOT EXISTS dict_process_category",
                        "process_code VARCHAR(64) NOT NULL",
                        "hourly_rate NUMERIC(18, 4) NOT NULL DEFAULT 0",
                        "CREATE TABLE IF NOT EXISTS dict_process_detail",
                        "category_id BIGINT NOT NULL REFERENCES dict_process_category(id) ON DELETE RESTRICT")
                .doesNotContain(
                        "CREATE TABLE IF NOT EXISTS dict_process (",
                        "price_1_5",
                        "price_3000_plus",
                        "idx_dict_process_name");
    }

    @Test
    void pricingSaveUsesProcessDetailAndCurrentCategoryHourlyRate() throws IOException {
        String submitReq = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "QuoteSubmitReq.java"));
        String engineerService = read(service("EngineerService.java"));
        String pricingService = read(service("ProcessRoutePricingService.java"));

        assertThat(submitReq)
                .as("Stage 6 quote payload identifies a process by processDetailId and keeps old fields only as compatibility/display inputs")
                .contains(
                        "private List<ProcessSubmitDTO> processRoute;",
                        "private Long processDetailId;",
                        "private Double unitWorkHours;",
                        "private String processCode;",
                        "private String categoryName;",
                        "private String detailName;",
                        "private Double hourlyRate;",
                        "private String processName;",
                        "private Double inputVal;",
                        "private String calcType;",
                        "private Double unitRate;",
                        "private Double lineCost;");
        assertThat(engineerService)
                .as("Stage 6 pricing delegates process rows to the category/detail pricing service and stores the priced snapshot")
                .contains(
                        "private ProcessRoutePricingService processRoutePricingService;",
                        "processRoutePricingService.priceProcessRows(List.of(process))",
                        "item.setProcessRoute(pricedProcessRoute)",
                        "toProcessSubmitRows(item.getProcessRoute())")
                .doesNotContain(
                        "findProcess(process.getProcessName())",
                        "qw.eq(\"process_name\", processName)",
                        "double rate = getTieredRate",
                        "private double getTieredRate");
        assertThat(pricingService)
                .contains(
                        "private DictProcessDetailMapper detailMapper;",
                        "private DictProcessCategoryMapper categoryMapper;",
                        "Long processDetailId = row.getProcessDetailId();",
                        "double hourlyRate = number(category.getHourlyRate());",
                        "double lineCost = unitWorkHours * hourlyRate;",
                        "snapshot.put(\"processCode\", category.getProcessCode());",
                        "snapshot.put(\"categoryName\", category.getCategoryName());",
                        "snapshot.put(\"detailName\", detail.getDetailName());");
        assertThat(engineerService)
                .doesNotContain("getPrice1To5", "getPrice1000To3000", "getPrice3000Plus");
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
                        "ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()))",
                        "item.setProcessRoute(processRouteSnapshotService.copySnapshot(source.getProcessRoute()))");
        assertThat(deliveryService)
                .contains(
                        "ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()))",
                        "sheet.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()))");
        assertThat(salesMatchService)
                .contains(
                        "item.setProcessRoute(processRouteSnapshotService.copySnapshot(source.getProcessRoute()))",
                        "row.setSourceProcessRouteSummary(processRouteSnapshotService.formatSnapshot(source.getProcessRoute()))",
                        "processSnapshotAvailable");
        assertThat(wmsAllocationService)
                .contains("row.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()))");
        assertThat(mesProductionService)
                .contains("req.setProcessRoute(buildProcessRoute(allocation.getProcessRoute()))");
        assertThat(mesProductionReq)
                .contains("private List<MesProductionProcessRouteDTO> processRoute;")
                .doesNotContain("private Object processRoute;");
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
                .as("The legacy APS endpoint stays read-only, but its work-hour process list now reads the two-level QPO process master")
                .contains(
                        "private DictProcessCategoryMapper processCategoryMapper;",
                        "private DictProcessDetailMapper processDetailMapper;",
                        "processDetailMapper.selectList(qw)",
                        "processCategoryMapper.selectList(qw)",
                        "row.get(\"unitRate\")",
                        "row.get(\"lineCost\")")
                .doesNotContain("DictProcessMapper", "MesProductionProcessRouteDTO");
    }

    private static Path service(String name) {
        return Path.of("src", "main", "java", "com", "machining", "quote", "service", name);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

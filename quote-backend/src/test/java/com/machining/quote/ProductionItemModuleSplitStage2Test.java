package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionItemModuleSplitStage2Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path FRONTEND_SRC = Path.of("..", "quote-frontend", "src");

    @Test
    void backendExposesSeparateLockedAndProductionItemReadEndpoints() throws IOException {
        String controller = read(MAIN_JAVA.resolve(Path.of("controller", "OrderDeliveryController.java")));

        assertThat(controller)
                .contains(
                        "private LockedItemService lockedItemService;",
                        "private ProductionItemService productionItemService;",
                        "@GetMapping(\"/locked-items\")",
                        "@RequirePermission(\"market:locked-item:view\")",
                        "return lockedItemService.listAllocations(req)",
                        "@GetMapping(\"/locked-items/customers\")",
                        "@RequirePermission(\"market:locked-item:customer-select\")",
                        "return lockedItemService.listCustomerOptions(field, keyword)",
                        "@GetMapping(\"/production-items\")",
                        "@RequirePermission(\"market:production-item:view\")",
                        "return productionItemService.listProductionItems(req)",
                        "@GetMapping(\"/production-items/customers\")",
                        "@RequirePermission(\"market:production-item:customer-select\")",
                        "return productionItemService.listCustomerOptions(field, keyword)"
                );
    }

    @Test
    void backendDoesNotExposeLegacyAllocationListAliasesAfterModuleSplit() throws IOException {
        String controller = read(MAIN_JAVA.resolve(Path.of("controller", "OrderDeliveryController.java")));

        assertThat(controller)
                .doesNotContain(
                        "@GetMapping(\"/allocations\")",
                        "@GetMapping(\"/allocations/customers\")",
                        "listAllocationCustomers("
                );
    }

    @Test
    void lockedItemServiceForcesLockedStockScopeAndKeepsPagingDataScopeInDatabase() throws IOException {
        String lockedService = read(MAIN_JAVA.resolve(Path.of("service", "LockedItemService.java")));

        assertThat(lockedService)
                .contains(
                        "applyLockedStockScope(query)",
                        "LOCKED_STOCK_TYPES",
                        "query.in(\"alloc.allocation_type\", LOCKED_STOCK_TYPES)",
                        "countLockedItems(query)",
                        "selectLockedItems(",
                        "dataScopeService.applyDeliveryTicketScope(query, \"ticket\")"
                )
                .doesNotContain(
                        "applyEquals(query, \"alloc.allocation_type\", normalizeUpper(req.getAllocationType()))",
                        "applyEquals(query, \"alloc.mes_production_status\", normalizeUpper(req.getMesProductionStatus()))"
                );
    }

    @Test
    void productionItemServiceReturnsOnlyUnsuccessfulProductionRequiredRows() throws IOException {
        String queryReq = read(MAIN_JAVA.resolve(Path.of("dto", "ProductionItemQueryReq.java")));
        String productionService = read(MAIN_JAVA.resolve(Path.of("service", "ProductionItemService.java")));

        assertThat(queryReq)
                .contains(
                        "private Integer page;",
                        "private Integer pageSize;",
                        "private String customerCode;",
                        "private String customerName;",
                        "private String rfqNo;",
                        "private String partNo;",
                        "private String version;",
                        "private String materialNo;",
                        "private String internalProjectNo;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String itemType;",
                        "private String mesProductionStatus;",
                        "private LocalDate dateFrom;",
                        "private LocalDate dateTo;"
                )
                .doesNotContain("private String allocationType;");
        assertThat(productionService)
                .contains(
                        "query.eq(\"alloc.allocation_type\", PRODUCTION_REQUIRED_TYPE)",
                        "applyProductionStatusScope(query, req.getMesProductionStatus())",
                        "COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') <> 'SUCCESS'",
                        "COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') = 'PENDING'",
                        "query.eq(\"alloc.mes_production_status\", STATUS_PROCESSING)",
                        "query.eq(\"alloc.mes_production_status\", STATUS_FAILED)",
                        "countLockedItems(query)",
                        "selectLockedItems(",
                        "dataScopeService.applyDeliveryTicketScope(query, \"ticket\")"
                );
    }

    @Test
    void customerPickersUseU9CustomerCacheWhileKeepingModuleFallbackScope() throws IOException {
        String lockedService = read(MAIN_JAVA.resolve(Path.of("service", "LockedItemService.java")));
        String productionService = read(MAIN_JAVA.resolve(Path.of("service", "ProductionItemService.java")));

        assertThat(lockedService)
                .contains("CustomerMasterService", "customerMasterService.listCustomerOptions(field, keyword)", "applyLockedStockScope(query)", "selectLockedItemCustomers(query)");
        assertThat(productionService)
                .contains("CustomerMasterService", "customerMasterService.listCustomerOptions(field, keyword)", "applyProductionActiveScope(query)", "selectLockedItemCustomers(query)");
    }

    @Test
    void frontendUsesSplitReadEndpointsInsteadOfOldAllocationListEndpoint() throws IOException {
        String api = read(FRONTEND_SRC.resolve(Path.of("services", "deliveredItemApi.js")));
        String orderApi = read(FRONTEND_SRC.resolve(Path.of("services", "orderApi.js")));

        assertThat(api)
                .contains(
                        "export const listLockedItems = (params = {}) => request('/api/order-delivery/locked-items', { params })",
                        "export const listProductionItems = (params = {}) => request('/api/order-delivery/production-items', { params })"
                )
                .doesNotContain("request('/api/order-delivery/allocations', { params })");
        assertThat(orderApi)
                .contains(
                        "'locked-item': '/api/order-delivery/locked-items/customers'",
                        "'production-item': '/api/order-delivery/production-items/customers'"
                )
                .doesNotContain("'locked-item': '/api/order-delivery/allocations/customers'");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

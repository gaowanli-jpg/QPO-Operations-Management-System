package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReleaseBatchStage3Test {
    private static final Path BACKEND_SOURCE = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path FRONTEND_SOURCE = Path.of("..", "quote-frontend", "src", "components", "MarketRelease.vue");

    @Test
    void requestUsesCustomerLevelBatchContractWithoutLegacySingleRfqDate() throws IOException {
        String source = read(BACKEND_SOURCE.resolve(Path.of("dto", "DeliveryGenerateReq.java")));

        assertThat(source).contains(
                "private List<Integer> rfqIds;",
                "private String documentTypeCode;",
                "private LocalDate salesOrderDate;",
                "private String customerOrderTitle;",
                "private String releaseBatchKey;");
        assertThat(source).doesNotContain("private Integer rfqId;", "private LocalDate startDate;");
    }

    @Test
    void controllerReturnsTypedBatchSummaryAndValidationServiceOwnsBatchRules() throws IOException {
        String controller = read(BACKEND_SOURCE.resolve(Path.of("controller", "MarketController.java")));
        String dto = read(BACKEND_SOURCE.resolve(Path.of("dto", "DeliveryBatchResultDTO.java")));
        String validation = read(BACKEND_SOURCE.resolve(Path.of("service", "RfqReleaseBatchValidationService.java")));
        String releaseSalesOrder = read(BACKEND_SOURCE.resolve(Path.of("service", "RfqReleaseSalesOrderService.java")));

        assertThat(controller).contains(
                "public DeliveryBatchResultDTO generateDeliveryTicket",
                "deliveryService.generateRfqReleaseBatch(req)");
        assertThat(dto).contains(
                "private Long salesOrderId;",
                "private List<Integer> sourceRfqIds;",
                "private List<Integer> ticketIds;",
                "private int confirmedMaterialCount;",
                "private int pendingMaterialCount;");
        assertThat(validation).contains(
                "QuoteStatusWorkflow.canGenerateDeliveryTicket",
                "assertCanAccessOrder",
                "customerName",
                "releaseBatchKey");
        assertThat(releaseSalesOrder)
                .contains("releaseBatchKeySummary")
                .doesNotContain("summary.put(\"releaseBatchKey\", context.releaseBatchKey())");
    }

    @Test
    void releasePageSubmitsOneBatchRequestForAllSelectedRfqs() throws IOException {
        String source = read(FRONTEND_SOURCE);
        int submitStart = source.indexOf("const submitTicket = async () =>");
        int submitEnd = source.indexOf("const resolveDeliveryLeadTime", submitStart);
        String submitBlock = source.substring(submitStart, submitEnd);

        assertThat(submitBlock).contains(
                "const batchRfqIds = selectedRowsRfqIds()",
                "rfqIds: batchRfqIds",
                "releaseBatchKey",
                "const response = await generateTicket(payload)",
                "error.response?.data?.message || error.message || '提单生成请求失败。'");
        assertThat(submitBlock).doesNotContain(
                "groupSelectedRowsBySource",
                "for (const { source, rows } of releaseGroups.values())",
                "successfulResults",
                "failedResults");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

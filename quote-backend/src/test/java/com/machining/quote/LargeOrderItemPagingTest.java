package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LargeOrderItemPagingTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void setupSummaryRemainsPreviewOnly() throws Exception {
        String source = source("controller", "EngineerController.java");

        assertThat(source)
                .contains("SETUP_PREVIEW_LIMIT = 10")
                .contains("@GetMapping(\"/setup/summary/{rfqId}\")")
                .contains("result.put(\"previewItemList\", previewItems)")
                .contains("result.put(\"itemCount\", itemCount == null ? 0L : itemCount)")
                .contains(".last(\"LIMIT \" + SETUP_PREVIEW_LIMIT)");
    }

    @Test
    void itemOperationModulesExposeStablePagingContracts() throws Exception {
        assertThat(source("controller", "EngineerController.java"))
                .contains("@GetMapping(\"/pricing/items/{rfqId}\")")
                .contains("@GetMapping(\"/pricing/assembly-child-candidates/{rfqId}\")")
                .contains("PageResult<ItemQuoteVersion>");

        assertThat(source("controller", "QuoteProcessController.java"))
                .contains("@GetMapping(\"/{rfqId}/summary\")")
                .contains("@GetMapping(\"/{rfqId}/items\")")
                .contains("PageResult<Map<String, Object>>");

        assertThat(source("controller", "MarketController.java"))
                .contains("@GetMapping(\"/release/items/{rfqId}\")")
                .contains("@GetMapping(\"/release/tickets/{rfqId}\")")
                .contains("@GetMapping(\"/release/process-sheets/{rfqId}\")");

        assertThat(source("controller", "OrderManagementController.java"))
                .contains("@GetMapping(\"/orders/{rfqId}/items\")")
                .contains("@GetMapping(\"/orders/{rfqId}/tickets\")")
                .contains("@GetMapping(\"/orders/{rfqId}/process-sheets\")")
                .contains("@GetMapping(\"/orders/{rfqId}/drawing-summary\")");
    }

    @Test
    void orderDetailPagingUsesExplicitServiceContracts() throws Exception {
        String controller = source("controller", "OrderManagementController.java");
        String service = source("service", "OrderManagementService.java");

        assertThat(controller)
                .contains("pageOrderItems(rfqId, page, pageSize)")
                .contains("pageOrderTickets(rfqId, page, pageSize)")
                .contains("pageOrderProcessSheets(rfqId, page, pageSize)")
                .contains("getDrawingSummary(rfqId)");
        assertThat(service)
                .contains("public PageResult<ItemQuoteVersion> pageOrderItems")
                .contains("public PageResult<OrderDeliveryTicket> pageOrderTickets")
                .contains("public PageResult<ProcessSheetDTO> pageOrderProcessSheets")
                .contains("public Map<String, Object> getDrawingSummary");
    }

    @Test
    void wholeOrderActionsRemainOrderScopedNotPageScoped() throws Exception {
        assertThat(method(source("controller", "EngineerController.java"), "completeOrderQuote"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.completeOrderQuote(rfqId)");
        assertThat(method(source("controller", "EngineerController.java"), "refreshOrderQuotes"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.refreshOrderQuotes(rfqId)");
        assertThat(method(source("controller", "EngineerController.java"), "applyRepeatQuotes"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.applyRepeatQuotes(rfqId)");

        assertThat(method(source("controller", "QuoteProcessController.java"), "saveQuoteSheet"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("quoteProcessService.saveQuoteSheet(rfqId)");
        assertThat(method(source("controller", "MarketController.java"), "generateDeliveryTicket"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("deliveryService.generateTickets(req)");
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }

    private String method(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertThat(start).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int nextAnnotation = source.indexOf("\n    @", start + methodName.length());
        int end = nextAnnotation >= 0 ? nextAnnotation : source.length();
        return source.substring(start, end);
    }
}

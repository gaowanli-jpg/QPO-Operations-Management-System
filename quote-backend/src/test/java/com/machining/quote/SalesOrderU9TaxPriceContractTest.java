package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9TaxPriceContractTest {
    @Test
    void u9SyncDefaultsToTaxIncludedAndAllowsOperatorSelectedUntaxedPriceMode() throws IOException {
        String assembler = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "u9",
                "U9SalesOrderRequestAssembler.java"));
        String validator = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "u9",
                "U9SalesOrderContractValidator.java"));
        String priceMode = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto",
                "SalesOrderU9PriceMode.java"));
        String reconciliation = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "u9",
                "U9SalesOrderReconciliationService.java"));

        assertThat(assembler)
                .contains(
                        "public U9SalesOrderHeadDTO assemble(SalesOrderHeader header, List<SalesOrderItem> items)",
                        "SalesOrderU9PriceMode.defaultMode(null)",
                        "head.setIsPriceIncludeTax(priceMode.isPriceIncludeTax())",
                        "line.setFinallyPriceTC(priceFor(item, priceMode))",
                        "desc.setPrivateDescSeg9(formatDecimal(priceFor(item, priceMode)))")
                .contains("getUntaxedUnitPrice()", "getTaxIncludedUnitPrice()")
                .doesNotContain("setOrderPriceTC", "OrderPriceTC", "TaxSchedule");
        assertThat(validator)
                .contains("priceMode.priceFieldLabel()", "requiredPrice(item, priceMode) == null")
                .contains("getUntaxedUnitPrice()", "getTaxIncludedUnitPrice()");
        assertThat(priceMode)
                .contains("UNTAXED", "TAX_INCLUDED", "isPriceIncludeTax", "fromCode", "return TAX_INCLUDED", "return mode == null ? TAX_INCLUDED : mode");
        assertThat(reconciliation)
                .contains("同步单价")
                .doesNotContain("未税单价\", expectedLine.getFinallyPriceTC()");
    }

    @Test
    void u9DtoClassesExposeOnlyMinimalVerifiedContractFields() throws IOException {
        String head = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "u9",
                "U9SalesOrderHeadDTO.java"));
        String line = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "u9",
                "U9SalesOrderLineDTO.java"));
        String shipLine = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "u9",
                "U9SalesOrderShipLineDTO.java"));
        String descFlex = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto", "u9",
                "U9DescFlexFieldDTO.java"));

        assertThat(head)
                .contains("OtherID", "DocNo", "BusinessDate", "OrderBy", "Seller", "SaleDepartment",
                        "DocumentType", "IsPriceIncludeTax", "SOLineDTOList")
                .doesNotContain("TC", "AC", "ShipToSite", "BillToSite", "PayerCustomer", "PayerToSite", "Memo");
        assertThat(line)
                .contains("DocLineNo", "ItemCode", "OrderByQtyTU", "OrderByQtyPU", "FinallyPriceTC",
                        "FreeType", "ShipTogetherFlag", "DescFlexField", "SOShiplineDTOList")
                .doesNotContain("CustomerItemNo", "CustomerItemName", "ItemVersion", "OrderPriceTC",
                        "TaxSchedule", "PriceSource", "FreeReason", "Project", "Task", "TBUToPBURate");
        assertThat(shipLine)
                .contains("DocSubLineNo", "ItemCode", "RequireDate", "DeliveryDate", "ShipPlanQtyTU", "ShipPlanQtyPU")
                .doesNotContain("PlanDate", "TUBaseToPUBaseRate", "Wh", "Memo");
        assertThat(descFlex)
                .contains("PrivateDescSeg1", "PrivateDescSeg2", "PrivateDescSeg3", "PrivateDescSeg4",
                        "PrivateDescSeg5", "PrivateDescSeg9", "PubDescSeg2");
    }

    @Test
    void u9PreviewDtoSeparatesBlockingErrorsWarningsAndFingerprint() throws IOException {
        String source = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto",
                "SalesOrderU9SyncPreviewLine.java"));
        String resp = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "dto",
                "SalesOrderU9SyncPreviewResp.java"));

        assertThat(source)
                .contains(
                        "private BigDecimal taxIncludedUnitPrice;",
                        "private BigDecimal taxIncludedTotalPrice;",
                        "private BigDecimal untaxedUnitPrice;",
                        "private BigDecimal untaxedTotalPrice;",
                        "private List<String> blockingErrors",
                        "private List<String> warnings");
        assertThat(resp)
                .contains(
                        "private List<String> blockingErrors",
                        "private List<String> warnings",
                        "private String requestFingerprint;");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

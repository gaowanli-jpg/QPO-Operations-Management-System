package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9TaxPriceContractTest {
    @Test
    void u9SyncUsesOnlyUntaxedUnitPriceForCreateRequest() throws IOException {
        String assembler = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "u9",
                "U9SalesOrderRequestAssembler.java"));
        String validator = read(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "u9",
                "U9SalesOrderContractValidator.java"));

        assertThat(assembler)
                .contains(
                        "line.setFinallyPriceTC(item.getUntaxedUnitPrice())",
                        "head.setIsPriceIncludeTax(false)",
                        "desc.setPrivateDescSeg9(formatDecimal(item.getUntaxedUnitPrice()))")
                .doesNotContain("getTaxIncludedUnitPrice()", "setOrderPriceTC", "OrderPriceTC", "preferredPrice(");
        assertThat(validator)
                .contains("item.getUntaxedUnitPrice() == null", "未税单价为空")
                .doesNotContain("含税单价为空");
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

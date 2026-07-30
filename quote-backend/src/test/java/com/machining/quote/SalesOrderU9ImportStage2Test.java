package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderDryRunResp;
import com.machining.quote.dto.SalesOrderExcelParseResult;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.service.SalesOrderExcelParser;
import com.machining.quote.service.SalesOrderImportService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderU9ImportStage2Test {
    private final SalesOrderExcelParser parser = new SalesOrderExcelParser();

    @Test
    void parserAggregatesRowsIntoU9SalesOrdersWithDefaultLineAndShipLineValues() throws Exception {
        SalesOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", 10, "PCS", "PCS", "18.5", "2026-08-01"),
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-002", "CP-002", "零件B", 5, "PCS", "PCS", "20", "2026-08-03"),
                        row("C002", "SO01", "PO-002", "2026-07-25", "ITEM-003", "CP-003", "零件C", 2, "PCS", "PCS", "8", "2026-08-05")
                ))),
                "SOI202607240001"
        );

        assertThat(result.getSourceRowCount()).isEqualTo(3);
        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getLineCount()).isEqualTo(3);

        U9SalesOrderHeadDTO firstOrder = result.getOrders().get(0);
        assertThat(firstOrder.getOtherID()).isEqualTo("SOI202607240001-1");
        assertThat(firstOrder.getOrderBy()).isEqualTo("C001");
        assertThat(firstOrder.getDocumentType()).isEqualTo("SO01");
        assertThat(firstOrder.getBusinessDate()).isEqualTo("2026-07-24");
        assertThat(firstOrder.getSOLineDTOList()).hasSize(2);

        var firstLine = firstOrder.getSOLineDTOList().get(0);
        assertThat(firstLine.getItemCode()).isEqualTo("ITEM-001");
        assertThat(firstLine.getCustomerItemNo()).isEqualTo("CP-001");
        assertThat(firstLine.getCustomerItemName()).isEqualTo("零件A");
        assertThat(firstLine.getOrderByQtyTU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstLine.getOrderByQtyPU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstLine.getOrderPriceTC()).isEqualByComparingTo(new BigDecimal("18.5"));
        assertThat(firstLine.getFinallyPriceTC()).isEqualByComparingTo(new BigDecimal("18.5"));
        assertThat(firstLine.getFreeType()).isEqualTo(-1);
        assertThat(firstLine.getFreeReason()).isEqualTo(-1);
        assertThat(firstLine.getShipTogetherFlag()).isEqualTo(-1);
        assertThat(firstLine.getPriceSource()).isEqualTo(1);
        assertThat(firstLine.getSOShiplineDTOList()).hasSize(1);

        var firstShipLine = firstLine.getSOShiplineDTOList().get(0);
        assertThat(firstShipLine.getItemCode()).isEqualTo("ITEM-001");
        assertThat(firstShipLine.getRequireDate()).isEqualTo("2026-08-01");
        assertThat(firstShipLine.getShipPlanQtyTU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstShipLine.getShipPlanQtyPU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstShipLine.getTU()).isEqualTo("PCS");
        assertThat(firstShipLine.getPU()).isEqualTo("PCS");
        assertThat(firstShipLine.getTUBaseToPUBaseRate()).isEqualByComparingTo(BigDecimal.ONE);

        String json = new ObjectMapper().writeValueAsString(firstOrder);
        assertThat(json)
                .contains("\"OtherID\":\"SOI202607240001-1\"")
                .contains("\"SOLineDTOList\"")
                .contains("\"SOShiplineDTOList\"");
    }

    @Test
    void parserRejectsMissingRequiredHeadersWithClearMessage() {
        List<String> headers = List.of("客户编码", "单据类型编码", "客户订单号", "业务日期", "客户料号", "客户品名", "数量", "销售单位", "计价单位", "单价", "交期");

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(excelBytes(headers, List.of())), "SOI202607240001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失表头")
                .hasMessageContaining("U9料号");
    }

    @Test
    void parserRejectsRowsMissingRequiredValuesWithRowNumber() {
        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream(excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", "", "PCS", "PCS", "18.5", "2026-08-01")
                ))),
                "SOI202607240001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("数量");
    }

    @Test
    void dryRunParsesAndValidatesWithoutCallingU9() throws Exception {
        SalesOrderImportService service = new SalesOrderImportService(parser);
        MockMultipartFile file = new MockMultipartFile(
                "excelFile",
                "sales-order.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", 10, "PCS", "PCS", "18.5", "2026-08-01")
                ))
        );

        SalesOrderDryRunResp resp = service.dryRun(file, "SOI202607240001");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getBatchNo()).isEqualTo("SOI202607240001");
        assertThat(resp.getTotalOrderCount()).isEqualTo(1);
        assertThat(resp.getTotalLineCount()).isEqualTo(1);
        assertThat(resp.getOrders()).hasSize(1);
    }

    @Test
    void u9ServicesValidateRequiredConfigurationBeforeExternalCalls() {
        U9TokenService tokenService = new U9TokenService();
        ReflectionTestUtils.setField(tokenService, "u9BaseUrl", "https://u9.example.com/u9c");
        ReflectionTestUtils.setField(tokenService, "u9UserCode", "demo");
        ReflectionTestUtils.setField(tokenService, "u9EntCode", "001");
        ReflectionTestUtils.setField(tokenService, "u9OrgCode", "001");
        ReflectionTestUtils.setField(tokenService, "u9ClientId", "QPO");

        assertThatThrownBy(tokenService::fetchToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP_U9_CLIENT_SECRET");

        U9SalesOrderClient client = new U9SalesOrderClient();
        ReflectionTestUtils.setField(client, "u9BaseUrl", "");
        ReflectionTestUtils.setField(client, "tokenService", tokenService);

        assertThatThrownBy(() -> client.create(List.of(new U9SalesOrderHeadDTO())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP_U9_BASE_URL");
    }

    private List<String> fullHeaders() {
        return List.of("客户编码", "单据类型编码", "客户订单号", "业务日期", "U9料号", "客户料号", "客户品名", "数量", "销售单位", "计价单位", "单价", "交期");
    }

    private List<Object> row(Object... values) {
        return List.of(values);
    }

    private byte[] excelBytes(List<String> headers, List<List<Object>> rows) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            List<List<String>> head = headers.stream().map(List::of).toList();
            EasyExcel.write(outputStream).head(head).sheet("sales-order").doWrite(rows);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

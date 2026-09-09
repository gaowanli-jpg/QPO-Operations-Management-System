package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderContractValidator.ValidationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9MinimalContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void assemblerSerializesOnlyMinimalVerifiedU9Contract() throws Exception {
        U9SalesOrderRequestAssembler assembler = new U9SalesOrderRequestAssembler();
        SalesOrderHeader header = header();
        header.setU9SellerCode("C00150");
        header.setU9SellerName("ignored seller name");
        header.setU9SaleDepartmentCode("200201");
        header.setU9SaleDepartmentName("ignored department name");
        SalesOrderItem first = item(501L, 7, "MAT-100201", 2, "100.00");
        first.setCustomerProjectNo("ITEM-CP");
        first.setCustomerOrderNo("ITEM-PO");
        first.setInternalProjectNo("ITEM-IP");
        first.setCustomerDeliveryDate(LocalDate.of(2026, 9, 30));
        first.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        first.setRemark("ship with release note");
        first.setSalesMaterialNo("CUSTOMER-MAT-MUST-NOT-SEND");
        first.setDescription("customer item name must not send");
        first.setVersion("DRAWING-VERSION-MUST-NOT-SEND");
        SalesOrderItem duplicateLine = item(502L, 7, "MAT-100202", 3, "88.50");

        U9SalesOrderHeadDTO request = assembler.assemble(header, List.of(first, duplicateLine));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));
        String rawJson = objectMapper.writeValueAsString(request);

        assertThat(json.path("OtherID").asText()).isEqualTo("SOQPO-260811-001");
        assertThat(json.path("DocNo").asText()).isEqualTo("DOC-STABLE-260811-001");
        assertThat(json.path("BusinessDate").asText()).isEqualTo("2026-08-11");
        assertThat(json.path("OrderBy").asText()).isEqualTo("GSS1");
        assertThat(json.path("DocumentType").asText()).isEqualTo("SO01");
        assertThat(json.path("Seller").asText()).isEqualTo("C00150");
        assertThat(json.path("SaleDepartment").asText()).isEqualTo("200201");
        assertThat(json.path("IsPriceIncludeTax").asBoolean()).isFalse();

        JsonNode firstLine = json.path("SOLineDTOList").get(0);
        assertThat(firstLine.path("DocLineNo").asInt()).isEqualTo(1);
        assertThat(firstLine.path("ItemCode").asText()).isEqualTo("MAT-100201");
        assertThat(firstLine.path("OrderByQtyTU").decimalValue()).isEqualByComparingTo("2");
        assertThat(firstLine.path("OrderByQtyPU").decimalValue()).isEqualByComparingTo("2");
        assertThat(firstLine.path("FinallyPriceTC").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(firstLine.path("FreeType").asInt()).isEqualTo(-1);
        assertThat(firstLine.path("ShipTogetherFlag").asInt()).isEqualTo(-1);
        assertThat(firstLine.path("SOShiplineDTOList")).hasSize(1);
        assertThat(firstLine.path("SOShiplineDTOList").get(0).path("ItemCode").asText()).isEqualTo("MAT-100201");
        assertThat(firstLine.path("SOShiplineDTOList").get(0).path("RequireDate").asText()).isEqualTo("2026-09-30");
        assertThat(firstLine.path("SOShiplineDTOList").get(0).path("DeliveryDate").asText()).isEqualTo("2026-09-25");

        JsonNode descFlex = firstLine.path("DescFlexField");
        assertThat(descFlex.path("PrivateDescSeg1").asText()).isEqualTo("ITEM-CP");
        assertThat(descFlex.path("PrivateDescSeg2").asText()).isEqualTo("ITEM-PO");
        assertThat(descFlex.path("PrivateDescSeg3").asText()).isEqualTo("ITEM-IP");
        assertThat(descFlex.path("PrivateDescSeg4").asText()).isEqualTo("2026-09-30");
        assertThat(descFlex.path("PrivateDescSeg5").asText()).isEqualTo("2026-09-25");
        assertThat(descFlex.path("PrivateDescSeg9").asText()).isEqualTo("100");
        assertThat(descFlex.path("PubDescSeg2").asText()).isEqualTo("ship with release note");

        assertThat(rawJson)
                .doesNotContain(
                        "CustomerItemNo", "CustomerItemName", "ItemVersion", "Project", "Task",
                        "OrderPriceTC", "PriceSource", "TaxSchedule", "FreeReason",
                        "ShipToSite", "BillToSite", "PayerCustomer", "PayerToSite", "Memo",
                        "TBUToPBURate", "TUBaseToPUBaseRate", "PlanDate", "Wh",
                        "CUSTOMER-MAT-MUST-NOT-SEND", "customer item name must not send", "DRAWING-VERSION-MUST-NOT-SEND");
        assertThat(json.has("TC")).isFalse();
        assertThat(json.has("AC")).isFalse();
        assertThat(firstLine.has("TU")).isFalse();
        assertThat(firstLine.has("PU")).isFalse();
        assertThat(firstLine.path("SOShiplineDTOList").get(0).has("TU")).isFalse();
        assertThat(firstLine.path("SOShiplineDTOList").get(0).has("PU")).isFalse();
        assertThat(rawJson.split("MAT-100201", -1)).hasSize(3);
    }

    @Test
    void assemblerRenumbersNonContiguousLineNumbersBeforeSendingU9() {
        U9SalesOrderRequestAssembler assembler = new U9SalesOrderRequestAssembler();
        SalesOrderHeader header = header();
        SalesOrderItem first = item(501L, 10, "MAT-100201", 2, "100.00");
        SalesOrderItem second = item(502L, 20, "MAT-100202", 3, "88.50");

        U9SalesOrderHeadDTO request = assembler.assemble(header, List.of(first, second));

        assertThat(request.getSOLineDTOList().stream()
                .map(line -> line.getDocLineNo())
                .toList()).containsExactly(1, 2);
    }

    @Test
    void validatorBlocksMissingUntaxedPriceAndOnlyWarnsForBlankDeliveryDates() {
        U9SalesOrderContractValidator validator = new U9SalesOrderContractValidator();
        SalesOrderItem item = item(501L, 1, "MAT-100201", 2, null);
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("226.00"));
        item.setCustomerDeliveryDate(null);
        item.setInternalDeliveryDate(null);

        ValidationResult result = validator.validate(header(), List.of(item));

        assertThat(result.hasBlockingErrors()).isTrue();
        assertThat(result.blockingErrors()).anyMatch(message -> message.contains("未税单价"));
        assertThat(result.warnings()).anyMatch(message -> message.contains("客户交期"));
        assertThat(result.warnings()).anyMatch(message -> message.contains("内部交期"));
        assertThat(result.blockingErrors()).noneMatch(message -> message.contains("含税单价"));
    }

    @Test
    void previewFingerprintMatchesActualSubmittedRequestFingerprint() {
        U9SalesOrderRequestAssembler assembler = new U9SalesOrderRequestAssembler();
        SalesOrderU9SyncService service = new SalesOrderU9SyncService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        com.machining.quote.service.u9.U9SalesOrderClient u9Client = mock(com.machining.quote.service.u9.U9SalesOrderClient.class);
        SalesOrderU9SyncStateService stateService = mock(SalesOrderU9SyncStateService.class);
        U9SalesOrderReconciliationService reconciliationService = mock(U9SalesOrderReconciliationService.class);
        SalesOrderHeader header = header();
        SalesOrderItem item = item(501L, 1, "MAT-100201", 2, "100.00");
        U9SalesOrderHeadDTO preparedRequest = assembler.assemble(header, List.of(item));
        String preparedFingerprint = assembler.fingerprint(preparedRequest);
        when(headerMapper.selectById(header.getId())).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(stateService.reserve(header.getId())).thenReturn(new SalesOrderU9SyncStateService.PreparedOrder(
                header,
                List.of(item),
                preparedRequest,
                "ATT-901",
                preparedFingerprint,
                LocalDateTime.of(2026, 8, 11, 14, 30)
        ));
        when(reconciliationService.checkExisting(any()))
                .thenReturn(U9SalesOrderReconciliationService.Result.notFoundConfirmed("创建前未找到同 DocNo U9 订单"));
        when(u9Client.create(any())).thenReturn(apiResult(header.getSalesOrderNo()));
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", u9Client);
        ReflectionTestUtils.setField(service, "requestAssembler", assembler);
        ReflectionTestUtils.setField(service, "contractValidator", new U9SalesOrderContractValidator());
        ReflectionTestUtils.setField(service, "syncStateService", stateService);
        ReflectionTestUtils.setField(service, "reconciliationService", reconciliationService);

        SalesOrderU9SyncPreviewResp preview = service.previewSalesOrder(header.getId());
        service.syncSalesOrders(List.of(header.getId()));

        ArgumentCaptor<List<U9SalesOrderHeadDTO>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(u9Client).create(requestCaptor.capture());
        String submittedFingerprint = assembler.fingerprint(requestCaptor.getValue().get(0));
        assertThat(preview.getRequestFingerprint()).isEqualTo(submittedFingerprint);
        verify(stateService).markSuccess(any(), any(), any(), any());
    }

    private SalesOrderHeader header() {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(901L);
        header.setSalesOrderNo("SOQPO-260811-001");
        header.setDocumentNo("DOC-STABLE-260811-001");
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("GSS1");
        header.setCustomerProjectNo("HEAD-CP");
        header.setCustomerOrderNo("HEAD-PO");
        header.setInternalProjectNo("HEAD-IP");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 11));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus("未同步");
        return header;
    }

    private SalesOrderItem item(Long id, Integer lineNo, String materialNo, int qty, String untaxedPrice) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(901L);
        item.setLineNo(lineNo);
        item.setPartNo("P-" + id);
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus("已确认");
        item.setQty(qty);
        item.setReleasedQty(qty);
        item.setRemainingQty(0);
        item.setUntaxedUnitPrice(untaxedPrice == null ? null : new BigDecimal(untaxedPrice));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 30));
        item.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private com.machining.quote.dto.u9.U9ApiResult<com.machining.quote.dto.u9.U9ResultDTO> apiResult(String otherId) {
        com.machining.quote.dto.u9.U9ResultDTO row = new com.machining.quote.dto.u9.U9ResultDTO();
        row.setMOtherID(otherId);
        row.setMIsSucess(true);
        row.setMID("U9-ID-1");
        row.setMCode("U9-CODE-1");
        com.machining.quote.dto.u9.U9ApiResult<com.machining.quote.dto.u9.U9ResultDTO> result = new com.machining.quote.dto.u9.U9ApiResult<>();
        result.setResCode(0);
        result.setSuccess(true);
        result.setData(List.of(row));
        return result;
    }
}

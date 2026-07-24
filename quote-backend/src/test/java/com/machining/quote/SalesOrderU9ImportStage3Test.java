package com.machining.quote;

import com.machining.quote.dto.SalesOrderExcelParseResult;
import com.machining.quote.dto.SalesOrderImportResp;
import com.machining.quote.dto.U9ApiResult;
import com.machining.quote.dto.U9ResultDTO;
import com.machining.quote.dto.U9SalesOrderHeadDTO;
import com.machining.quote.dto.U9SalesOrderLineDTO;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderExcelParser;
import com.machining.quote.service.SalesOrderImportService;
import com.machining.quote.service.U9SalesOrderClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9ImportStage3Test {
    @Test
    void realImportPersistsSuccessfulBatchRecordsAndSafeSummaries() throws Exception {
        SalesOrderImportService service = service(parseResult(
                order("SOI202607240003-1", "PO-001", "C001", "SO01", 2),
                order("SOI202607240003-2", "PO-002", "C002", "SO01", 1)
        ));
        U9SalesOrderClient u9Client = injected(service, "u9SalesOrderClient");
        when(u9Client.create(any())).thenReturn(apiResult(0,
                u9Result("SOI202607240003-1", true, "U9-ID-1", "SO-001", ""),
                u9Result("SOI202607240003-2", true, "U9-ID-2", "SO-002", "")
        ));

        SalesOrderImportResp resp = service.importAndSync(file(), "SOI202607240003");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getBatchNo()).isEqualTo("SOI202607240003");
        assertThat(resp.getTotalOrderCount()).isEqualTo(2);
        assertThat(resp.getSuccessOrderCount()).isEqualTo(2);
        assertThat(resp.getFailedOrderCount()).isZero();
        assertThat(resp.getRows()).extracting("u9Code").containsExactly("SO-001", "SO-002");

        SalesOrderImportBatch createdBatch = capturedBatchInsert(service);
        SalesOrderImportBatch updatedBatch = capturedBatchUpdate(service);
        assertThat(createdBatch.getStatus()).isEqualTo("SYNCING");
        assertThat(updatedBatch.getStatus()).isEqualTo("SUCCESS");
        assertThat(updatedBatch.getSuccessOrderCount()).isEqualTo(2);

        List<SalesOrderImportRecord> records = capturedRecords(service);
        assertThat(records).hasSize(2);
        assertThat(records).extracting(SalesOrderImportRecord::getStatus).containsOnly("SUCCESS");
        assertThat(records.get(0).getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(records.get(0).getU9Id()).isEqualTo("U9-ID-1");
        assertThat(String.valueOf(records.get(0).getRequestSummaryJson()))
                .contains("SOI202607240003-1", "PO-001", "lineCount=2")
                .doesNotContain("token", "clientSecret", "SOLineDTOList", "SOShiplineDTOList");

        AuditService auditService = injected(service, "auditService");
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_U9"),
                eq("sales_order_import_batch"),
                any(),
                eq(null),
                any(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_IMPORT_U9")),
                eq(null)
        );
    }

    @Test
    void partialU9FailurePersistsFailureDetailsAndPartialBatchStatus() throws Exception {
        SalesOrderImportService service = service(parseResult(
                order("SOI202607240004-1", "PO-001", "C001", "SO01", 1),
                order("SOI202607240004-2", "PO-002", "C002", "SO01", 1)
        ));
        U9SalesOrderClient u9Client = injected(service, "u9SalesOrderClient");
        when(u9Client.create(any())).thenReturn(apiResult(0,
                u9Result("SOI202607240004-1", true, "U9-ID-1", "SO-001", ""),
                u9Result("SOI202607240004-2", false, null, null, "item not found")
        ));

        SalesOrderImportResp resp = service.importAndSync(file(), "SOI202607240004");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(resp.getRows()).extracting("success").containsExactly(true, false);
        assertThat(resp.getRows().get(1).getErrorMessage()).contains("item not found");
        assertThat(capturedBatchUpdate(service).getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(capturedRecords(service)).extracting(SalesOrderImportRecord::getStatus)
                .containsExactly("SUCCESS", "FAILED");
    }

    @Test
    void u9CallFailureMarksBatchFailedAndRedactsSensitiveFailureText() throws Exception {
        SalesOrderImportService service = service(parseResult(
                order("SOI202607240005-1", "PO-001", "C001", "SO01", 1)
        ));
        U9SalesOrderClient u9Client = injected(service, "u9SalesOrderClient");
        when(u9Client.create(any())).thenThrow(new RuntimeException(
                "U9 down token=secret clientSecret=hidden SOLineDTOList=[large-request]"
        ));

        SalesOrderImportResp resp = service.importAndSync(file(), "SOI202607240005");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(resp.getErrorMessage())
                .contains("U9 down")
                .doesNotContain("secret", "hidden", "SOLineDTOList");
        assertThat(capturedBatchUpdate(service).getStatus()).isEqualTo("FAILED");
        assertThat(capturedRecords(service).get(0).getU9ErrorMsg())
                .doesNotContain("secret", "hidden", "SOLineDTOList");
    }

    @Test
    void salesOrderImportAuditActionHasMarketModuleAndReadableLabel() {
        assertThat(AuditService.inferModule("SALES_ORDER_IMPORT_U9")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_IMPORT_U9")).contains("销售订单").contains("U9");
    }

    private SalesOrderImportService service(SalesOrderExcelParseResult parseResult) throws Exception {
        SalesOrderExcelParser parser = mock(SalesOrderExcelParser.class);
        when(parser.parse(any(), any())).thenReturn(parseResult);
        SalesOrderImportService service = new SalesOrderImportService(parser);

        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(900L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));

        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        U9SalesOrderClient u9Client = mock(U9SalesOrderClient.class);
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", u9Client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T injected(SalesOrderImportService service, String field) {
        return (T) ReflectionTestUtils.getField(service, field);
    }

    private SalesOrderImportBatch capturedBatchInsert(SalesOrderImportService service) {
        SalesOrderImportBatchMapper mapper = injected(service, "batchMapper");
        ArgumentCaptor<SalesOrderImportBatch> captor = ArgumentCaptor.forClass(SalesOrderImportBatch.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private SalesOrderImportBatch capturedBatchUpdate(SalesOrderImportService service) {
        SalesOrderImportBatchMapper mapper = injected(service, "batchMapper");
        ArgumentCaptor<SalesOrderImportBatch> captor = ArgumentCaptor.forClass(SalesOrderImportBatch.class);
        verify(mapper).updateById(captor.capture());
        return captor.getValue();
    }

    private List<SalesOrderImportRecord> capturedRecords(SalesOrderImportService service) {
        SalesOrderImportRecordMapper mapper = injected(service, "recordMapper");
        ArgumentCaptor<SalesOrderImportRecord> captor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(mapper, times((int) org.mockito.Mockito.mockingDetails(mapper).getInvocations().stream()
                .filter(invocation -> "insert".equals(invocation.getMethod().getName()))
                .count())).insert(captor.capture());
        return captor.getAllValues();
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
                "excelFile",
                "sales-order.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );
    }

    private SalesOrderExcelParseResult parseResult(U9SalesOrderHeadDTO... orders) {
        SalesOrderExcelParseResult result = new SalesOrderExcelParseResult();
        result.setOrders(new ArrayList<>(List.of(orders)));
        result.setOrderCount(orders.length);
        int lineCount = result.getOrders().stream().mapToInt(order -> order.getSOLineDTOList().size()).sum();
        result.setLineCount(lineCount);
        result.setSourceRowCount(lineCount);
        return result;
    }

    private U9SalesOrderHeadDTO order(String otherId, String customerOrderNo, String orderBy, String documentType, int lineCount) {
        U9SalesOrderHeadDTO order = new U9SalesOrderHeadDTO();
        order.setOtherID(otherId);
        order.setCustomerOrderNo(customerOrderNo);
        order.setOrderBy(orderBy);
        order.setDocumentType(documentType);
        order.setBusinessDate("2026-07-24");
        for (int i = 1; i <= lineCount; i++) {
            U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
            line.setItemCode("ITEM-" + i);
            order.getSOLineDTOList().add(line);
        }
        return order;
    }

    @SafeVarargs
    private U9ApiResult<U9ResultDTO> apiResult(int resCode, U9ResultDTO... rows) {
        U9ApiResult<U9ResultDTO> result = new U9ApiResult<>();
        result.setResCode(resCode);
        result.setSuccess(resCode == 0);
        result.setResMsg(resCode == 0 ? "ok" : "failed");
        result.setData(List.of(rows));
        return result;
    }

    private U9ResultDTO u9Result(String otherId, boolean success, String u9Id, String u9Code, String error) {
        U9ResultDTO result = new U9ResultDTO();
        result.setMOtherID(otherId);
        result.setMIsSucess(success);
        result.setMID(u9Id);
        result.setMCode(u9Code);
        result.setMErrorMsg(error);
        return result;
    }
}

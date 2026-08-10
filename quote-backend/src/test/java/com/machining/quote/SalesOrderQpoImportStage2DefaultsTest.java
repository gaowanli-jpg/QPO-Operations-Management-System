package com.machining.quote;

import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderAutoSnapshotCompletionService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderNumberService;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage2DefaultsTest {
    @Test
    void controllerMapsPageDefaultsAndKeepsDrawingParametersUnchanged() {
        SalesOrderQpoImportController controller = new SalesOrderQpoImportController();
        SalesOrderQpoImportService service = mock(SalesOrderQpoImportService.class);
        ReflectionTestUtils.setField(controller, "salesOrderQpoImportService", service);

        MockMultipartFile excel = excel();
        List<MultipartFile> drawings = List.of(pdf("drawings", "P-001.pdf"));
        List<MultipartFile> versionDrawings = List.of(pdf("versionDrawings", "P-001_A.pdf"));
        when(service.importQpo(
                eq(excel),
                eq(7L),
                eq("SO01"),
                any(SalesOrderQpoImportDefaultsDTO.class),
                same(drawings),
                same(versionDrawings)
        )).thenReturn(new SalesOrderQpoImportResp());

        controller.importQpo(
                7L,
                "SO01",
                "2026-07-01",
                " PO-001 ",
                " CP-001 ",
                " 客户抬头 ",
                " 正式订单 ",
                " IP-001 ",
                excel,
                drawings,
                versionDrawings);

        var defaultsCaptor = org.mockito.ArgumentCaptor.forClass(SalesOrderQpoImportDefaultsDTO.class);
        verify(service).importQpo(
                eq(excel),
                eq(7L),
                eq("SO01"),
                defaultsCaptor.capture(),
                same(drawings),
                same(versionDrawings));

        SalesOrderQpoImportDefaultsDTO defaults = defaultsCaptor.getValue();
        assertThat(defaults.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(defaults.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(defaults.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(defaults.getCustomerOrderTitle()).isEqualTo("客户抬头");
        assertThat(defaults.getOrderType()).isEqualTo("正式订单");
        assertThat(defaults.getInternalProjectNo()).isEqualTo("IP-001");
    }

    @Test
    void controllerRejectsMissingRequiredDefaultsWithClearMessages() {
        SalesOrderQpoImportController controller = new SalesOrderQpoImportController();
        ReflectionTestUtils.setField(controller, "salesOrderQpoImportService", mock(SalesOrderQpoImportService.class));
        MockMultipartFile excel = excel();
        List<MultipartFile> drawings = List.of();
        List<MultipartFile> versionDrawings = List.of();

        assertThatThrownBy(() -> controller.importQpo(
                7L,
                "SO01",
                " ",
                "PO-001",
                "CP-001",
                "客户抬头",
                "正式订单",
                "IP-001",
                excel,
                drawings,
                versionDrawings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写下单日期");
        assertThatThrownBy(() -> controller.importQpo(
                7L,
                "SO01",
                "2026-07-01",
                " ",
                "CP-001",
                "客户抬头",
                "正式订单",
                "IP-001",
                excel,
                drawings,
                versionDrawings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写客户订单号");
        assertThatThrownBy(() -> controller.importQpo(
                7L,
                "SO01",
                "2026-07-01",
                "PO-001",
                "CP-001",
                "客户抬头",
                " ",
                "IP-001",
                excel,
                drawings,
                versionDrawings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写订单类型");
        assertThatThrownBy(() -> controller.importQpo(
                7L,
                "SO01",
                "2026-07-01",
                "PO-001",
                "CP-001",
                "客户抬头",
                "正式订单",
                " ",
                excel,
                drawings,
                versionDrawings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写内部项目号");
    }

    @Test
    void serviceOverloadRejectsMissingRequiredDefaultsBeforeAnyImportWork() {
        SalesOrderQpoImportService service = service();
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        defaults.setCustomerOrderNo("PO-001");
        defaults.setOrderType("正式订单");

        assertThatThrownBy(() -> service.importQpo(
                excel(),
                7L,
                "SO01",
                defaults,
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请填写内部项目号");

        verifyNoInteractions(
                customerMapper(service),
                batchMapper(service),
                recordMapper(service),
                headerMapper(service),
                itemMapper(service),
                auditService(service),
                businessFileService(service),
                autoSnapshotCompletionService(service),
                deliveryService(service),
                dataScopeService(service),
                parser(service));
    }

    @Test
    @SuppressWarnings("unchecked")
    void servicePersistsHeaderDefaultsAndItemLineEffectiveValues() throws Exception {
        SalesOrderQpoImportService service = service();
        SalesOrderQpoImportDefaultsDTO defaults = defaults();
        SalesOrderQpoExcelParseResult parseResult = parseResultWithLineOverride();
        when(customerMapper(service).selectById(7L)).thenReturn(customer());
        when(parser(service).parse(any(), same(defaults))).thenReturn(parseResult);
        when(headerMapper(service).selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper(service)).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(301L);
            return 1;
        }).when(headerMapper(service)).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(401L);
            return 1;
        }).when(recordMapper(service)).insert(any(SalesOrderImportRecord.class));

        service.importQpo(excel(), 7L, "SO01", defaults, List.of(), List.of());

        var headerCaptor = org.mockito.ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper(service)).insert(headerCaptor.capture());
        SalesOrderHeader header = headerCaptor.getValue();
        assertThat(header.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(header.getCustomerOrderNo()).isEqualTo("A-PO");
        assertThat(header.getCustomerProjectNo()).isEqualTo("A-CP");
        assertThat(header.getCustomerOrderTitle()).isEqualTo("A-TITLE");
        assertThat(header.getOrderType()).isEqualTo("正式订单");
        assertThat(header.getInternalProjectNo()).isEqualTo("A-IP");

        var itemCaptor = org.mockito.ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper(service), times(2)).insert(itemCaptor.capture());
        SalesOrderItem overrideItem = itemCaptor.getAllValues().get(0);
        assertThat(overrideItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(overrideItem.getCustomerOrderNo()).isEqualTo("B-PO");
        assertThat(overrideItem.getCustomerProjectNo()).isEqualTo("B-CP");
        assertThat(overrideItem.getCustomerOrderTitle()).isEqualTo("B-TITLE");
        assertThat(overrideItem.getOrderType()).isEqualTo("样品订单");
        assertThat(overrideItem.getInternalProjectNo()).isEqualTo("B-IP");

        SalesOrderItem fallbackItem = itemCaptor.getAllValues().get(1);
        assertThat(fallbackItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(fallbackItem.getCustomerOrderNo()).isEqualTo("A-PO");
        assertThat(fallbackItem.getCustomerProjectNo()).isEqualTo("A-CP");
        assertThat(fallbackItem.getCustomerOrderTitle()).isEqualTo("A-TITLE");
        assertThat(fallbackItem.getOrderType()).isEqualTo("正式订单");
        assertThat(fallbackItem.getInternalProjectNo()).isEqualTo("A-IP");

        var recordCaptor = org.mockito.ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper(service)).insert(recordCaptor.capture());
        Map<String, Object> requestSummary = (Map<String, Object>) recordCaptor.getValue().getRequestSummaryJson();
        assertThat(requestSummary)
                .containsEntry("customerOrderNo", "A-PO")
                .containsEntry("internalProjectNo", "A-IP")
                .containsEntry("salesOrderDate", LocalDate.of(2026, 7, 1))
                .containsEntry("orderType", "正式订单")
                .containsEntry("lineCount", 2);
        assertThat(String.valueOf(requestSummary))
                .doesNotContain("B-PO")
                .doesNotContain("B-IP")
                .doesNotContain("B-TITLE");
    }

    private SalesOrderQpoImportService service() {
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "excelParser", mock(SalesOrderQpoExcelParser.class));
        ReflectionTestUtils.setField(service, "customerMapper", mock(CustomerMasterMapper.class));
        ReflectionTestUtils.setField(service, "batchMapper", mock(SalesOrderImportBatchMapper.class));
        ReflectionTestUtils.setField(service, "recordMapper", mock(SalesOrderImportRecordMapper.class));
        ReflectionTestUtils.setField(service, "headerMapper", mock(SalesOrderHeaderMapper.class));
        ReflectionTestUtils.setField(service, "itemMapper", mock(SalesOrderItemMapper.class));
        ReflectionTestUtils.setField(service, "dataScopeService", mock(DataScopeService.class));
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(service, "businessFileService", mock(BusinessFileService.class));
        ReflectionTestUtils.setField(service, "autoSnapshotCompletionService", mock(SalesOrderAutoSnapshotCompletionService.class));
        ReflectionTestUtils.setField(service, "deliveryService", mock(DeliveryService.class));
        ReflectionTestUtils.setField(service, "salesOrderNumberService", new SalesOrderNumberService());
        return service;
    }

    private static SalesOrderQpoImportDefaultsDTO defaults() {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        defaults.setCustomerOrderNo("A-PO");
        defaults.setCustomerProjectNo("A-CP");
        defaults.setCustomerOrderTitle("A-TITLE");
        defaults.setOrderType("正式订单");
        defaults.setInternalProjectNo("A-IP");
        return defaults;
    }

    private static SalesOrderQpoExcelParseResult parseResultWithLineOverride() {
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("A-PO|A-IP");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerOrderNo("A-PO");
        group.setCustomerProjectNo("A-CP");
        group.setCustomerOrderTitle("A-TITLE");
        group.setOrderType("正式订单");
        group.setInternalProjectNo("A-IP");
        group.setLines(List.of(
                line(1, LocalDate.of(2026, 7, 2), "B-PO", "B-CP", "B-TITLE", "样品订单", "B-IP", "P-001"),
                line(2, LocalDate.of(2026, 7, 1), "A-PO", "A-CP", "A-TITLE", "正式订单", "A-IP", "P-002")
        ));

        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(2);
        result.setSourceRowCount(2);
        return result;
    }

    private static SalesOrderQpoExcelLineDTO line(
            int lineNo,
            LocalDate salesOrderDate,
            String customerOrderNo,
            String customerProjectNo,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo,
            String partNo) {
        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(lineNo + 1);
        line.setSalesOrderLineNo(lineNo);
        line.setSalesOrderDate(salesOrderDate);
        line.setCustomerOrderNo(customerOrderNo);
        line.setCustomerProjectNo(customerProjectNo);
        line.setCustomerOrderTitle(customerOrderTitle);
        line.setOrderType(orderType);
        line.setInternalProjectNo(internalProjectNo);
        line.setPartNo(partNo);
        line.setQty(1);
        line.setShippedQty(0);
        line.setUnshippedQty(1);
        line.setVersion("A");
        return line;
    }

    private static CustomerMaster customer() {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(7L);
        customer.setCustomerCode("C001");
        customer.setCustomerName("ACME");
        customer.setEnabled(true);
        return customer;
    }

    private static SalesOrderQpoExcelParser parser(SalesOrderQpoImportService service) {
        return (SalesOrderQpoExcelParser) ReflectionTestUtils.getField(service, "excelParser");
    }

    private static CustomerMasterMapper customerMapper(SalesOrderQpoImportService service) {
        return (CustomerMasterMapper) ReflectionTestUtils.getField(service, "customerMapper");
    }

    private static SalesOrderImportBatchMapper batchMapper(SalesOrderQpoImportService service) {
        return (SalesOrderImportBatchMapper) ReflectionTestUtils.getField(service, "batchMapper");
    }

    private static SalesOrderImportRecordMapper recordMapper(SalesOrderQpoImportService service) {
        return (SalesOrderImportRecordMapper) ReflectionTestUtils.getField(service, "recordMapper");
    }

    private static SalesOrderHeaderMapper headerMapper(SalesOrderQpoImportService service) {
        return (SalesOrderHeaderMapper) ReflectionTestUtils.getField(service, "headerMapper");
    }

    private static SalesOrderItemMapper itemMapper(SalesOrderQpoImportService service) {
        return (SalesOrderItemMapper) ReflectionTestUtils.getField(service, "itemMapper");
    }

    private static AuditService auditService(SalesOrderQpoImportService service) {
        return (AuditService) ReflectionTestUtils.getField(service, "auditService");
    }

    private static BusinessFileService businessFileService(SalesOrderQpoImportService service) {
        return (BusinessFileService) ReflectionTestUtils.getField(service, "businessFileService");
    }

    private static SalesOrderAutoSnapshotCompletionService autoSnapshotCompletionService(SalesOrderQpoImportService service) {
        return (SalesOrderAutoSnapshotCompletionService) ReflectionTestUtils.getField(service, "autoSnapshotCompletionService");
    }

    private static DeliveryService deliveryService(SalesOrderQpoImportService service) {
        return (DeliveryService) ReflectionTestUtils.getField(service, "deliveryService");
    }

    private static DataScopeService dataScopeService(SalesOrderQpoImportService service) {
        return (DataScopeService) ReflectionTestUtils.getField(service, "dataScopeService");
    }

    private static MockMultipartFile excel() {
        return new MockMultipartFile(
                "excelFile",
                "sales-order.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "xlsx".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile pdf(String part, String fileName) {
        return new MockMultipartFile(part, fileName, "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));
    }
}

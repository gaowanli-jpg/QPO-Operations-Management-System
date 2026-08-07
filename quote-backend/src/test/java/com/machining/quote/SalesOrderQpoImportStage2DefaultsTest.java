package com.machining.quote;

import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderMatchService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
                salesOrderMatchService(service),
                deliveryService(service),
                dataScopeService(service),
                parser(service));
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
        ReflectionTestUtils.setField(service, "salesOrderMatchService", mock(SalesOrderMatchService.class));
        ReflectionTestUtils.setField(service, "deliveryService", mock(DeliveryService.class));
        return service;
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

    private static SalesOrderMatchService salesOrderMatchService(SalesOrderQpoImportService service) {
        return (SalesOrderMatchService) ReflectionTestUtils.getField(service, "salesOrderMatchService");
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

package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderManageController;
import com.machining.quote.dto.SalesOrderItemDataReplacementConfirmResp;
import com.machining.quote.dto.SalesOrderItemUpdateLineReq;
import com.machining.quote.dto.SalesOrderItemUpdateReq;
import com.machining.quote.dto.SalesOrderMaintenanceOrderUpdateReq;
import com.machining.quote.dto.SalesOrderMaintenanceUpdateResp;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderItemDataReplacementExcelParser;
import com.machining.quote.service.SalesOrderItemDataReplacementService;
import com.machining.quote.service.SalesOrderMaintenanceService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderMaintenanceServiceTest {
    @Test
    void controllerExposesSalesOrderMaintenanceWriteApisWithSalesOrderUpdatePermission() throws Exception {
        assertRoute("saveOrder", new Class<?>[]{Long.class, SalesOrderMaintenanceOrderUpdateReq.class},
                PutMapping.class, "/orders/{salesOrderId}", "market:sales-order:update");
        assertRoute("saveItems", new Class<?>[]{Long.class, SalesOrderItemUpdateReq.class},
                PostMapping.class, "/orders/{salesOrderId}/items/batch", "market:sales-order:update");
        assertRoute("previewItemDataReplacement", new Class<?>[]{Long.class, org.springframework.web.multipart.MultipartFile.class},
                PostMapping.class, "/orders/{salesOrderId}/items/data-replacement/preview", "market:sales-order:update");
        assertRoute("confirmItemDataReplacement", new Class<?>[]{Long.class, String.class, org.springframework.web.multipart.MultipartFile.class},
                PostMapping.class, "/orders/{salesOrderId}/items/data-replacement/confirm", "market:sales-order:update");
    }

    @Test
    void saveOrderUpdatesHeaderAndOnlyFillsBlankItemOrderDefaults() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(10L, "未同步");
        SalesOrderItem blankItem = item(101L, 10L);
        blankItem.setCustomerOrderNo(null);
        blankItem.setCustomerProjectNo("");
        blankItem.setOrderType(null);
        blankItem.setInternalProjectNo(null);
        blankItem.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        SalesOrderItem existingItem = item(102L, 10L);
        existingItem.setCustomerOrderNo("ITEM-PO");
        existingItem.setCustomerProjectNo("ITEM-CP");
        existingItem.setOrderType("备货订单");
        existingItem.setInternalProjectNo("ITEM-IP");
        existingItem.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        fixture.withOrder(header, List.of(blankItem, existingItem));

        SalesOrderMaintenanceOrderUpdateReq req = new SalesOrderMaintenanceOrderUpdateReq();
        req.setDocumentTypeCode(" SO02 ");
        req.setSalesOrderDate(LocalDate.of(2026, 9, 7));
        req.setCustomerOrderNo(" PO-NEW ");
        req.setCustomerProjectNo(" CP-NEW ");
        req.setCustomerOrderTitle(" Title New ");
        req.setOrderType("正式订单");
        req.setInternalProjectNo(" IP-NEW ");

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.saveOrder(header.getId(), req);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder(
                "documentTypeCode",
                "salesOrderDate",
                "customerOrderNo",
                "customerProjectNo",
                "customerOrderTitle",
                "orderType",
                "internalProjectNo");
        assertThat(header.getDocumentTypeCode()).isEqualTo("SO02");
        assertThat(header.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(header.getCustomerOrderNo()).isEqualTo("PO-NEW");
        assertThat(header.getCustomerProjectNo()).isEqualTo("CP-NEW");
        assertThat(header.getCustomerOrderTitle()).isEqualTo("Title New");
        assertThat(header.getOrderType()).isEqualTo("正式订单");
        assertThat(header.getInternalProjectNo()).isEqualTo("IP-NEW");

        assertThat(blankItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(blankItem.getCustomerOrderNo()).isEqualTo("PO-NEW");
        assertThat(blankItem.getCustomerProjectNo()).isEqualTo("CP-NEW");
        assertThat(blankItem.getOrderType()).isEqualTo("正式订单");
        assertThat(blankItem.getInternalProjectNo()).isEqualTo("IP-NEW");
        assertThat(existingItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(existingItem.getCustomerOrderNo()).isEqualTo("ITEM-PO");
        assertThat(existingItem.getCustomerProjectNo()).isEqualTo("ITEM-CP");
        assertThat(existingItem.getOrderType()).isEqualTo("备货订单");
        assertThat(existingItem.getInternalProjectNo()).isEqualTo("ITEM-IP");

        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.headerMapper).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.ticketMapper, never()).update(any(), any());
        verifyAudit(fixture.auditService, "SALES_ORDER_UPDATE", header.getId());
    }

    @Test
    void saveItemsUpdatesEditableFieldsWithoutDerivingTaxIncludedPriceFromUntaxedPrice() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(11L, "未同步");
        SalesOrderItem item = item(201L, 11L);
        item.setUntaxedUnitPrice(new BigDecimal("100.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        fixture.withOrder(header, List.of(item));

        SalesOrderItemUpdateReq req = new SalesOrderItemUpdateReq();
        SalesOrderItemUpdateLineReq line = new SalesOrderItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setQty(3);
        line.setUntaxedUnitPrice(new BigDecimal("200.00"));
        line.setCustomerDeliveryDate(LocalDate.of(2026, 10, 1));
        line.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        line.setCustomerOrderNo(" PO-ITEM ");
        line.setCustomerProjectNo(" CP-ITEM ");
        line.setInternalProjectNo(" IP-ITEM ");
        line.setRemark(" urgent ");
        req.setItems(List.of(line));

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.saveItems(header.getId(), req);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "untaxedUnitPrice",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "customerOrderNo",
                "customerProjectNo",
                "internalProjectNo",
                "remark");
        assertThat(item.getQty()).isEqualTo(3);
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("200.00");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("113.00");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("600.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("339.00");
        assertThat(item.getRemainingQty()).isEqualTo(2);
        assertThat(item.getUnshippedQty()).isEqualTo(2);
        assertThat(item.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(item.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(item.getCustomerOrderNo()).isEqualTo("PO-ITEM");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-ITEM");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-ITEM");
        assertThat(item.getRemark()).isEqualTo("urgent");

        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.ticketMapper, never()).update(any(), any());
        verifyAudit(fixture.auditService, "SALES_ORDER_ITEM_UPDATE", header.getId());
    }

    @Test
    void saveItemsUpdatesTaxIncludedUnitPriceIndependentlyFromUntaxedPrice() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(12L, "未同步");
        SalesOrderItem item = item(202L, 12L);
        item.setUntaxedUnitPrice(new BigDecimal("100.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        fixture.withOrder(header, List.of(item));

        SalesOrderItemUpdateReq req = new SalesOrderItemUpdateReq();
        SalesOrderItemUpdateLineReq line = new SalesOrderItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setTaxIncludedUnitPrice(new BigDecimal("118.50"));
        req.setItems(List.of(line));

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.saveItems(header.getId(), req);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactly("taxIncludedUnitPrice");
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("118.50");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("200.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("237.00");
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void maintenanceWritesRejectLockedU9Statuses() {
        for (String status : List.of("同步中", "结果待确认", "已同步")) {
            Fixture fixture = fixture();
            SalesOrderHeader header = header(14L, status);
            SalesOrderItem item = item(204L, 14L);
            fixture.withOrder(header, List.of(item));

            SalesOrderMaintenanceOrderUpdateReq orderReq = new SalesOrderMaintenanceOrderUpdateReq();
            orderReq.setDocumentTypeCode("SO02");
            assertThatThrownBy(() -> fixture.maintenanceService.saveOrder(header.getId(), orderReq))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(status)
                    .hasMessageContaining("不能修改");

            SalesOrderItemUpdateReq itemReq = new SalesOrderItemUpdateReq();
            SalesOrderItemUpdateLineReq line = new SalesOrderItemUpdateLineReq();
            line.setSalesOrderItemId(item.getId());
            line.setRemark("locked");
            itemReq.setItems(List.of(line));
            assertThatThrownBy(() -> fixture.maintenanceService.saveItems(header.getId(), itemReq))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(status)
                    .hasMessageContaining("不能修改");

            verify(fixture.itemMapper, never()).update(any(), any());
            verify(fixture.headerMapper, never()).update(any(), any());
        }
    }

    @Test
    void dataReplacementPreviewAndConfirmUseSalesOrderManagementActionAndDoNotTouchTickets() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(15L, "未同步");
        SalesOrderItem item = item(301L, 15L);
        item.setUntaxedUnitPrice(new BigDecimal("100.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        fixture.withOrder(header, List.of(item));
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "数量", "客户交期", "内部交期", "未税单价", "含税单价", "客户订单号", "客户项目号", "内部项目号", "备注"),
                List.of(item.getPartNo(), item.getVersion(), item.getLineNo(), 4, "2026-10-11", "2026-10-02", "120.00", "150.00", "PO-X", "CP-X", "IP-X", "note"));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(excel));
        SalesOrderItemDataReplacementConfirmResp result = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(excel), preview.getPreviewFingerprint());

        Method confirm = SalesOrderItemDataReplacementService.class.getMethod(
                "confirm", Long.class, java.io.InputStream.class, String.class);
        assertThat(confirm.getAnnotation(Transactional.class)).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "untaxedUnitPrice",
                "taxIncludedUnitPrice",
                "customerOrderNo",
                "customerProjectNo",
                "internalProjectNo",
                "remark");
        assertThat(item.getQty()).isEqualTo(4);
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("150.00");
        verify(fixture.ticketMapper, never()).update(any(), any());
        verifyAudit(fixture.auditService, "SALES_ORDER_ITEM_DATA_REPLACEMENT", header.getId());
    }

    @Test
    void controllerDelegatesSalesOrderManagementDataReplacementWithoutReasonParameter() throws Exception {
        SalesOrderManageController controller = new SalesOrderManageController();
        SalesOrderItemDataReplacementService service = mock(SalesOrderItemDataReplacementService.class);
        ReflectionTestUtils.setField(controller, "itemDataReplacementService", service);
        SalesOrderU9ItemDataReplacementPreviewDTO preview = new SalesOrderU9ItemDataReplacementPreviewDTO();
        when(service.preview(eq(1L), any())).thenReturn(preview);
        SalesOrderItemDataReplacementConfirmResp confirm = new SalesOrderItemDataReplacementConfirmResp();
        confirm.setSuccess(true);
        when(service.confirm(eq(1L), any(), eq("a".repeat(64)))).thenReturn(confirm);
        MockMultipartFile file = new MockMultipartFile("excelFile", "replacement.xlsx", "application/octet-stream", new byte[]{1});

        assertThat(controller.previewItemDataReplacement(1L, file)).isSameAs(preview);
        assertThat(controller.confirmItemDataReplacement(1L, " " + "a".repeat(64) + " ", file)).isSameAs(confirm);

        verify(service).preview(eq(1L), any());
        verify(service).confirm(eq(1L), any(), eq("a".repeat(64)));
    }

    private static void assertRoute(
            String methodName,
            Class<?>[] parameterTypes,
            Class<?> mappingType,
            String path,
            String permission) throws Exception {
        Method method = SalesOrderManageController.class.getMethod(methodName, parameterTypes);
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        assertThat(requirePermission).isNotNull();
        assertThat(requirePermission.value()).containsExactly(permission);
        if (mappingType == PutMapping.class) {
            assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly(path);
        } else if (mappingType == PostMapping.class) {
            assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        } else {
            throw new IllegalArgumentException("Unsupported mapping type " + mappingType);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyAudit(AuditService auditService, String action, Long salesOrderId) {
        ArgumentCaptor<Object> summaryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq(action),
                eq("sales_order_header"),
                eq(salesOrderId),
                any(),
                summaryCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction(action)),
                isNull());
        assertThat((Map<String, Object>) summaryCaptor.getValue())
                .containsEntry("salesOrderId", salesOrderId)
                .containsKey("changedFields")
                .containsKey("durationMs");
    }

    private static Fixture fixture() {
        SalesOrderMaintenanceService maintenanceService = new SalesOrderMaintenanceService();
        SalesOrderItemDataReplacementService replacementService = new SalesOrderItemDataReplacementService();
        Fixture fixture = new Fixture(maintenanceService, replacementService);
        ReflectionTestUtils.setField(maintenanceService, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(maintenanceService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(maintenanceService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(maintenanceService, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(maintenanceService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(replacementService, "excelParser", new SalesOrderItemDataReplacementExcelParser());
        ReflectionTestUtils.setField(replacementService, "maintenanceService", maintenanceService);
        ReflectionTestUtils.setField(replacementService, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(replacementService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(replacementService, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(replacementService, "auditService", fixture.auditService);
        when(fixture.headerMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        return fixture;
    }

    private static SalesOrderHeader header(Long id, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SOQPO-" + id);
        header.setDocumentTypeCode("SO01");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        header.setCustomerOrderNo("HEADER-PO");
        header.setCustomerProjectNo("HEADER-CP");
        header.setCustomerOrderTitle("HEADER-TITLE");
        header.setOrderType("预测订单");
        header.setInternalProjectNo("HEADER-IP");
        header.setStatus("已生成提单");
        header.setU9SyncStatus(u9Status);
        return header;
    }

    private static SalesOrderItem item(Long id, Long salesOrderId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("P-100");
        item.setVersion("A");
        item.setQty(2);
        item.setReleasedQty(1);
        item.setShippedQty(1);
        item.setRemainingQty(1);
        item.setUnshippedQty(1);
        item.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        item.setUntaxedUnitPrice(new BigDecimal("50.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("56.50"));
        item.setUntaxedTotalPrice(new BigDecimal("100.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        return item;
    }

    private static byte[] excel(List<String> headers, List<?>... rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("replacement");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                List<?> values = rows[r];
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Fixture(
            SalesOrderMaintenanceService maintenanceService,
            SalesOrderItemDataReplacementService replacementService,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            AuditService auditService) {
        private Fixture(SalesOrderMaintenanceService maintenanceService, SalesOrderItemDataReplacementService replacementService) {
            this(
                    maintenanceService,
                    replacementService,
                    mock(SalesOrderHeaderMapper.class),
                    mock(SalesOrderItemMapper.class),
                    mock(OrderDeliveryTicketMapper.class),
                    mock(DataScopeService.class),
                    mock(AuditService.class));
        }

        private void withOrder(SalesOrderHeader header, List<SalesOrderItem> items) {
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            when(headerMapper.selectById(header.getId())).thenReturn(header);
            when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
            for (SalesOrderItem item : items) {
                when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
            }
        }
    }
}

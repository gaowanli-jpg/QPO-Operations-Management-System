package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemMaintenanceLineReq;
import com.machining.quote.dto.OrderItemMaintenanceUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderItemDataReplacementExcelParser;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.storage.StorageType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderItemMaintenanceServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void pageOrderItemRowsIncludesQuoteModeForDetailTypeColumn() {
        Fixture fixture = fixture();
        OrderRfq order = order(899, "RFQ-899", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion assembly = item(1000, 899, "P-ASM", "A", 2);
        assembly.setQuoteMode("ASSEMBLY");

        when(fixture.orderMapper.selectById(899)).thenReturn(order);
        when(fixture.itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(assembly));
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> row = fixture.service.pageOrderItemRows(899, 1, 20).getRecords().get(0);

        assertThat(row).containsEntry("quoteMode", "ASSEMBLY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveItemsUpdatesEditableSourceFieldsAndLeavesTicketSnapshotUntouched() {
        Fixture fixture = fixture();
        OrderRfq order = order(900, "RFQ-900", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion item = item(1001, 900, "P-OLD", "A", 10);
        OrderDeliveryTicket ticket = ticket(3001, 900, 1001, "P-OLD", "A", 4);

        when(fixture.orderMapper.selectByIdForUpdate(900)).thenReturn(order);
        when(fixture.itemMapper.selectByIdForUpdate(1001)).thenReturn(item);
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(ticket));
        when(fixture.itemMapper.updateById(item)).thenReturn(1);

        OrderItemMaintenanceLineReq line = new OrderItemMaintenanceLineReq();
        line.setItemId(1001);
        line.setPartNo("P-NEW");
        line.setVersion("B");
        line.setQty(12);
        line.setLeadTime("2026/10/01");
        line.setQuoteLeadTime("2026-10-05");
        line.setInternalTargetLeadTime("2026-10-08");
        line.setDescription("updated desc");
        line.setMaterialName("updated material name");
        line.setSurfaceTreatment("anodized");
        line.setRemark("updated remark");
        line.setSalesOrderDate(LocalDate.of(2026, 9, 15));
        line.setCustomerOrderNo("CO-9");
        line.setCustomerProjectNo("CP-9");
        line.setCustomerOrderTitle("title 9");
        line.setOrderType("正式订单");
        line.setInternalProjectNo("IP-9");

        OrderItemMaintenanceUpdateReq req = new OrderItemMaintenanceUpdateReq();
        req.setItems(List.of(line));

        Map<String, Object> result = fixture.service.saveOrderItems(900, req);

        assertThat(result).containsEntry("success", true).containsEntry("updatedCount", 1);
        assertThat(item.getPartNo()).isEqualTo("P-NEW");
        assertThat(item.getVersion()).isEqualTo("B");
        assertThat(item.getQty()).isEqualTo(12);
        assertThat(item.getLeadTime()).isEqualTo("2026-10-01");
        assertThat(item.getQuoteLeadTime()).isEqualTo("2026-10-05");
        assertThat(item.getInternalTargetLeadTime()).isEqualTo("2026-10-08");
        assertThat(item.getDescription()).isEqualTo("updated desc");
        assertThat(item.getMaterialName()).isEqualTo("updated material name");
        assertThat(item.getSurfaceTreatment()).isEqualTo("anodized");
        assertThat(item.getRemark()).isEqualTo("updated remark");
        assertThat(item.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(item.getCustomerOrderNo()).isEqualTo("CO-9");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-9");
        assertThat(item.getCustomerOrderTitle()).isEqualTo("title 9");
        assertThat(item.getOrderType()).isEqualTo("正式订单");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-9");
        assertThat(ticket.getPartNo()).isEqualTo("P-OLD");
        assertThat(ticket.getVersion()).isEqualTo("A");
        assertThat(ticket.getQty()).isEqualTo(4);
        assertThat(ticket.getMaterialName()).isEqualTo("ticket material snapshot");

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_UPDATE"),
                eq("order_rfq"),
                eq(900),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_UPDATE")),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) afterData.getValue();
        assertThat(auditSummary)
                .containsEntry("rfqId", 900)
                .containsEntry("updatedCount", 1);
        assertThat((List<String>) auditSummary.get("changedFields")).contains("materialName");
    }

    @Test
    void saveItemsAuditsSafelyWhenRfqNoIsMissing() {
        Fixture fixture = fixture();
        OrderRfq order = order(900, null, QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion item = item(1001, 900, "P-100", "A", 10);
        OrderItemMaintenanceLineReq line = new OrderItemMaintenanceLineReq();
        line.setItemId(1001);
        line.setPartNo("P-100-B");
        line.setVersion("A");
        line.setQty(10);
        OrderItemMaintenanceUpdateReq req = new OrderItemMaintenanceUpdateReq();
        req.setItems(List.of(line));

        when(fixture.orderMapper.selectByIdForUpdate(900)).thenReturn(order);
        when(fixture.itemMapper.selectByIdForUpdate(1001)).thenReturn(item);
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(fixture.itemMapper.updateById(item)).thenReturn(1);

        Map<String, Object> result = fixture.service.saveOrderItems(900, req);

        assertThat(result).containsEntry("updatedCount", 1);
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_UPDATE"),
                eq("order_rfq"),
                eq(900),
                isNull(),
                any(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_UPDATE")),
                isNull()
        );
    }

    @Test
    void saveItemsRejectsQuantityBelowTicketedQuantity() {
        Fixture fixture = fixture();
        OrderRfq order = order(901, "RFQ-901", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion item = item(1002, 901, "P-LOW", "A", 10);
        OrderDeliveryTicket ticket = ticket(3002, 901, 1002, "P-LOW", "A", 6);
        OrderItemMaintenanceLineReq line = new OrderItemMaintenanceLineReq();
        line.setItemId(1002);
        line.setPartNo("P-LOW");
        line.setVersion("A");
        line.setQty(5);
        OrderItemMaintenanceUpdateReq req = new OrderItemMaintenanceUpdateReq();
        req.setItems(List.of(line));

        when(fixture.orderMapper.selectByIdForUpdate(901)).thenReturn(order);
        when(fixture.itemMapper.selectByIdForUpdate(1002)).thenReturn(item);
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(ticket));

        assertThatThrownBy(() -> fixture.service.saveOrderItems(901, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("询价数量不能小于已提单数量 6");
        verify(fixture.itemMapper, never()).updateById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceDrawingsOverwritesExistingDrawingUrlAndAuditsReplacement() {
        Fixture fixture = fixture();
        OrderRfq order = order(902, "RFQ-902", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion versionA = item(1003, 902, "P-200", "A", 1);
        ItemQuoteVersion versionB = item(1004, 902, "P-200", "B", 1);
        versionB.setDrawingUrl("/old/P-200_B.pdf");
        MockMultipartFile drawing = drawing("P-200_B.pdf");

        when(fixture.orderMapper.selectByIdForUpdate(902)).thenReturn(order);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(versionA, versionB));
        when(fixture.businessFileService.saveDrawing(eq(order), eq(drawing), eq("ORDER_DRAWING_REPLACE"), eq("902")))
                .thenReturn(stored("/new/P-200_B.pdf", drawing));
        when(fixture.itemMapper.updateById(versionB)).thenReturn(1);

        Map<String, Object> result = fixture.service.replaceDrawings(
                902,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(drawing)
        );

        assertThat(result).containsEntry("success", true).containsEntry("replacedCount", 1);
        assertThat(versionA.getDrawingUrl()).isNull();
        assertThat(versionB.getDrawingUrl()).isEqualTo("/new/P-200_B.pdf");

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_DRAWING_REPLACE"),
                eq("order_rfq"),
                eq(902),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_DRAWING_REPLACE")),
                isNull()
        );
        assertThat((Map<String, Object>) afterData.getValue())
                .containsEntry("replacedCount", 1)
                .containsEntry("versionUploadedDrawingCount", 1);
    }

    @Test
    void replaceDrawingsDoesNotCountPartOnlySkipsAfterVersionSpecificReplacement() {
        Fixture fixture = fixture();
        OrderRfq order = order(903, "RFQ-903", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion item = item(1005, 903, "P-300", "A", 1);
        MockMultipartFile versionDrawing = drawing("P-300_A.pdf");
        MockMultipartFile partDrawing = new MockMultipartFile(
                "drawings",
                "P-300.pdf",
                "application/pdf",
                "%PDF-1.4 P-300".getBytes(StandardCharsets.UTF_8)
        );

        when(fixture.orderMapper.selectByIdForUpdate(903)).thenReturn(order);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(fixture.businessFileService.saveDrawing(eq(order), eq(versionDrawing), eq("ORDER_DRAWING_REPLACE"), eq("903")))
                .thenReturn(stored("/new/P-300_A.pdf", versionDrawing));
        when(fixture.businessFileService.saveDrawing(eq(order), eq(partDrawing), eq("ORDER_DRAWING_REPLACE"), eq("903")))
                .thenReturn(stored("/new/P-300.pdf", partDrawing));
        when(fixture.itemMapper.updateById(item)).thenReturn(1);

        Map<String, Object> result = fixture.service.replaceDrawings(
                903,
                List.<MultipartFile>of(partDrawing),
                List.<MultipartFile>of(versionDrawing)
        );

        assertThat(result).containsEntry("replacedCount", 1);
        assertThat((List<?>) result.get("replacedItems")).hasSize(1);
        assertThat(item.getDrawingUrl()).isEqualTo("/new/P-300_A.pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceDrawingsWithFolderOverwritesEveryItemAndStoresOrderFolderMetadata() {
        Fixture fixture = fixture();
        OrderRfq order = order(908, "RFQ-908", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion versionA = item(1010, 908, "P-FOLDER", "A", 1);
        ItemQuoteVersion versionB = item(1011, 908, "P-FOLDER", "B", 1);
        versionB.setDrawingUrl("/old/P-FOLDER_B.pdf");
        MockMultipartFile folderDrawing = drawing("folder/P-FOLDER-pack.pdf");
        BusinessFileService.DrawingFolderSaveResult folderResult = drawingFolderResult(
                "/uploads/drawing-folders/RFQ/908/folder-908/",
                "RFQ-908图纸",
                folderDrawing);

        when(fixture.orderMapper.selectByIdForUpdate(908)).thenReturn(order);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(versionA, versionB));
        when(fixture.businessFileService.saveDrawingFolderFiles(
                eq("RFQ"), eq("908"), eq("RFQ-908图纸"), eq(List.of(folderDrawing)), eq("ORDER_DRAWING_REPLACE_FOLDER"), eq("908")))
                .thenReturn(folderResult);
        when(fixture.orderMapper.updateById(order)).thenReturn(1);
        when(fixture.itemMapper.updateById(any(ItemQuoteVersion.class))).thenReturn(1);

        Map<String, Object> result = fixture.service.replaceDrawings(
                908,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(),
                List.<MultipartFile>of(folderDrawing),
                "RFQ-908图纸");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("replacedCount", 2)
                .containsEntry("drawingFolderFileCount", 1);
        assertThat(order.getDrawingFolderUrl()).isEqualTo(folderResult.folderUrl());
        assertThat(order.getDrawingFolderName()).isEqualTo("RFQ-908图纸");
        assertThat(order.getDrawingFolderFileCount()).isEqualTo(1);
        assertThat(versionA.getDrawingUrl()).isEqualTo(folderResult.folderUrl());
        assertThat(versionB.getDrawingUrl()).isEqualTo(folderResult.folderUrl());

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_DRAWING_REPLACE"),
                eq("order_rfq"),
                eq(908),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_DRAWING_REPLACE")),
                isNull()
        );
        Map<String, Object> summary = (Map<String, Object>) afterData.getValue();
        assertThat(summary)
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1)
                .containsEntry("replacedCount", 2);
        assertThat(String.valueOf(summary)).doesNotContain(folderResult.folderUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmItemDataReplacementUpdatesRequestedEditableFields() throws Exception {
        Fixture fixture = fixture();
        OrderRfq order = order(904, "RFQ-904", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion item = item(1006, 904, "P-OLD", "A", 10);
        MockMultipartFile workbook = orderReplacementWorkbook(
                List.of("零件ID", "图号", "版本", "新图号", "询价数量", "客户询价交期", "报价承诺交期", "物料描述", "材料名称", "表面处理"),
                List.of(row(1006, "P-OLD", "A", "P-NEW", 12, "2026/10/01", "2026-10-05", "updated desc", "updated material name", "brushed")));

        when(fixture.orderMapper.selectById(904)).thenReturn(order);
        when(fixture.orderMapper.selectByIdForUpdate(904)).thenReturn(order);
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(fixture.itemMapper.selectByIdForUpdate(1006)).thenReturn(item);
        when(fixture.itemMapper.updateById(item)).thenReturn(1);

        Map<String, Object> preview = fixture.service.previewItemDataReplacement(904, workbook);
        Map<String, Object> result = fixture.service.confirmItemDataReplacement(
                904,
                workbook,
                String.valueOf(preview.get("previewFingerprint")));

        assertThat(result).containsEntry("success", true);
        assertThat((Map<String, Object>) result.get("summary"))
                .containsEntry("updatedItemCount", 1)
                .containsEntry("changedCount", 1);
        assertThat(item.getPartNo()).isEqualTo("P-NEW");
        assertThat(item.getQty()).isEqualTo(12);
        assertThat(item.getLeadTime()).isEqualTo("2026-10-01");
        assertThat(item.getQuoteLeadTime()).isEqualTo("2026-10-05");
        assertThat(item.getDescription()).isEqualTo("updated desc");
        assertThat(item.getMaterialName()).isEqualTo("updated material name");
        assertThat(item.getSurfaceTreatment()).isEqualTo("brushed");

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_DATA_REPLACEMENT"),
                eq("order_rfq"),
                eq(904),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_DATA_REPLACEMENT")),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) afterData.getValue();
        assertThat(auditSummary)
                .containsEntry("updatedItemCount", 1)
                .containsEntry("matchedCount", 1);
        assertThat((Map<String, Integer>) auditSummary.get("changedFieldCounts"))
                .containsEntry("materialName", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void discardOrderItemStoresPreviousStatusAndLeavesTicketSnapshotUntouched() {
        Fixture fixture = fixture();
        OrderRfq order = order(905, "RFQ-905", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion item = item(1007, 905, "P-DISCARD", "A", 10);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        OrderDeliveryTicket ticket = ticket(3003, 905, 1007, "P-DISCARD", "A", 4);

        when(fixture.orderMapper.selectByIdForUpdate(905)).thenReturn(order);
        when(fixture.itemMapper.selectByIdForUpdate(1007)).thenReturn(item);
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(ticket));
        when(fixture.itemMapper.updateById(item)).thenReturn(1);

        Map<String, Object> result = fixture.service.discardOrderItem(905, 1007);

        assertThat(result).containsEntry("success", true).containsEntry("itemId", 1007);
        assertThat(item.getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(item.getPreviousStatusBeforeDiscard()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(ticket.getPartNo()).isEqualTo("P-DISCARD");
        assertThat(ticket.getQty()).isEqualTo(4);

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_DISCARD"),
                eq("order_rfq"),
                eq(905),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_DISCARD")),
                isNull()
        );
        assertThat((Map<String, Object>) afterData.getValue())
                .containsEntry("itemId", 1007)
                .containsEntry("statusBefore", QuoteStatusWorkflow.QUOTED)
                .containsEntry("statusAfter", QuoteStatusWorkflow.DISCARDED)
                .containsEntry("ticketedQty", 4);
    }

    @Test
    void restoreOrderItemUsesPreviousStatusBeforeDiscard() {
        Fixture fixture = fixture();
        OrderRfq order = order(906, "RFQ-906", QuoteStatusWorkflow.QUOTED);
        ItemQuoteVersion item = item(1008, 906, "P-RESTORE", "B", 3);
        item.setStatus(QuoteStatusWorkflow.DISCARDED);
        item.setPreviousStatusBeforeDiscard(QuoteStatusWorkflow.PRICED);

        when(fixture.orderMapper.selectByIdForUpdate(906)).thenReturn(order);
        when(fixture.itemMapper.selectByIdForUpdate(1008)).thenReturn(item);
        when(fixture.ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        Map<String, Object> result = fixture.service.restoreOrderItem(906, 1008);

        assertThat(result).containsEntry("success", true).containsEntry("itemId", 1008);
        assertThat(item.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(item.getPreviousStatusBeforeDiscard()).isNull();
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_RESTORE"),
                eq("order_rfq"),
                eq(906),
                isNull(),
                any(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_RESTORE")),
                isNull()
        );
    }

    @Test
    void appendOrderItemsImportsUnpricedPartsAndBindsDrawingsWithoutChangingOrderStatus() throws Exception {
        Fixture fixture = fixture();
        OrderRfq order = order(907, "RFQ-907", QuoteStatusWorkflow.QUOTED);
        order.setCustomerCode("C907");
        order.setCustomerShortName("ACME");
        MockMultipartFile workbook = rfqImportWorkbook(
                List.of("图号", "物料描述", "材料名称", "表面处理", "版本", "数量", "单位", "交货周期", "备注"),
                List.of(row("P-APP", "append desc", "45#", "black", "A", 5, "pcs", "2026/10/01", "new part")));
        MockMultipartFile drawing = drawing("P-APP_A.pdf");

        when(fixture.orderMapper.selectByIdForUpdate(907)).thenReturn(order);
        when(fixture.businessFileService.saveDrawing(eq(order), eq(drawing), eq("ORDER_ITEM_APPEND_DRAWING"), eq("907")))
                .thenReturn(stored("/new/P-APP_A.pdf", drawing));
        when(fixture.itemMapper.insert(any(ItemQuoteVersion.class))).thenReturn(1);

        Map<String, Object> result = fixture.service.appendOrderItems(
                907,
                workbook,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(drawing));

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("insertedCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.QUOTED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.QUOTED);
        assertThat(order.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);

        ArgumentCaptor<ItemQuoteVersion> inserted = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).insert(inserted.capture());
        ItemQuoteVersion saved = inserted.getValue();
        assertThat(saved.getRfqId()).isEqualTo(907);
        assertThat(saved.getPartNo()).isEqualTo("P-APP");
        assertThat(saved.getVersion()).isEqualTo("A");
        assertThat(saved.getQty()).isEqualTo(5);
        assertThat(saved.getMaterialName()).isEqualTo("45#");
        assertThat(saved.getLeadTime()).isEqualTo("2026/10/01");
        assertThat(saved.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(saved.getDrawingUrl()).isEqualTo("/new/P-APP_A.pdf");
        verify(fixture.orderMapper, never()).updateById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendOrderItemsWithFolderBindsNewItemsToFolderAndStoresOrderFolderMetadata() throws Exception {
        Fixture fixture = fixture();
        OrderRfq order = order(909, "RFQ-909", QuoteStatusWorkflow.QUOTED);
        MockMultipartFile workbook = rfqImportWorkbook(
                List.of("图号", "物料描述", "材料名称", "表面处理", "版本", "数量", "单位", "交货周期", "备注"),
                List.of(row("P-APP-FOLDER", "append desc", "45#", "black", "A", 5, "pcs", "2026/10/01", "new part")));
        MockMultipartFile folderDrawing = drawing("folder/P-APP-FOLDER.pdf");
        BusinessFileService.DrawingFolderSaveResult folderResult = drawingFolderResult(
                "/uploads/drawing-folders/RFQ/909/folder-909/",
                "新增零件图纸",
                folderDrawing);

        when(fixture.orderMapper.selectByIdForUpdate(909)).thenReturn(order);
        when(fixture.businessFileService.saveDrawingFolderFiles(
                eq("RFQ"), eq("909"), eq("新增零件图纸"), eq(List.of(folderDrawing)), eq("ORDER_ITEM_APPEND_DRAWING_FOLDER"), eq("909")))
                .thenReturn(folderResult);
        when(fixture.orderMapper.updateById(order)).thenReturn(1);
        when(fixture.itemMapper.insert(any(ItemQuoteVersion.class))).thenReturn(1);

        Map<String, Object> result = fixture.service.appendOrderItems(
                909,
                workbook,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(),
                List.<MultipartFile>of(folderDrawing),
                "新增零件图纸");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("insertedCount", 1)
                .containsEntry("boundCount", 1)
                .containsEntry("missingDrawingCount", 0)
                .containsEntry("drawingFolderFileCount", 1);
        assertThat(order.getDrawingFolderUrl()).isEqualTo(folderResult.folderUrl());
        assertThat(order.getDrawingFolderFileCount()).isEqualTo(1);

        ArgumentCaptor<ItemQuoteVersion> inserted = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).insert(inserted.capture());
        ItemQuoteVersion saved = inserted.getValue();
        assertThat(saved.getDrawingUrl()).isEqualTo(folderResult.folderUrl());
        assertThat(saved.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);

        ArgumentCaptor<Object> afterData = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("ORDER_ITEM_APPEND_IMPORT"),
                eq("order_rfq"),
                eq(909),
                isNull(),
                afterData.capture(),
                eq("ORDER"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("ORDER_ITEM_APPEND_IMPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterData.getValue())
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1);
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new OrderManagementService();
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.businessFileService = mock(BusinessFileService.class);
        fixture.auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(fixture.service, "orderMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.service, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.service, "businessFileService", fixture.businessFileService);
        ReflectionTestUtils.setField(fixture.service, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.service, "leadTimeService", new LeadTimeService());
        ReflectionTestUtils.setField(fixture.service, "orderItemDataReplacementExcelParser", new OrderItemDataReplacementExcelParser());
        return fixture;
    }

    private OrderRfq order(Integer id, String rfqNo, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName("ACME");
        order.setStatus(status);
        return order;
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, String partNo, String version, Integer qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(qty);
        item.setStatus(QuoteStatusWorkflow.PRICED);
        item.setDescription("old desc");
        item.setLeadTime("2026-09-01");
        item.setQuoteLeadTime("2026-09-05");
        item.setInternalTargetLeadTime("2026-09-08");
        return item;
    }

    private OrderDeliveryTicket ticket(Integer id, Integer rfqId, Integer itemId, String partNo, String version, Integer qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(rfqId);
        ticket.setItemId(itemId);
        ticket.setSourceType("RFQ");
        ticket.setSourceOrderId(rfqId.longValue());
        ticket.setSourceItemId(itemId.longValue());
        ticket.setPartNo(partNo);
        ticket.setVersion(version);
        ticket.setQty(qty);
        ticket.setMaterialName("ticket material snapshot");
        return ticket;
    }

    private MockMultipartFile drawing(String fileName) {
        return new MockMultipartFile(
                "versionDrawings",
                fileName,
                "application/pdf",
                ("%PDF-1.4 " + fileName).getBytes(StandardCharsets.UTF_8)
        );
    }

    private BusinessFileService.StoredBusinessFile stored(String publicPath, MockMultipartFile drawing) {
        return new BusinessFileService.StoredBusinessFile(
                publicPath,
                StorageType.LOCAL,
                1L,
                publicPath,
                drawing.getOriginalFilename(),
                drawing.getContentType(),
                drawing.getSize(),
                null
        );
    }

    private BusinessFileService.DrawingFolderSaveResult drawingFolderResult(
            String folderUrl,
            String folderName,
            MockMultipartFile drawing) {
        BusinessFileService.DrawingFolderFile file = new BusinessFileService.DrawingFolderFile(
                drawing.getOriginalFilename(),
                folderUrl + "file-1.pdf",
                drawing.getContentType(),
                drawing.getSize(),
                java.time.LocalDateTime.now());
        return new BusinessFileService.DrawingFolderSaveResult(folderUrl, folderName, 1, List.of(file));
    }

    private MockMultipartFile orderReplacementWorkbook(List<String> headers, List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("replacement");
            writeHeaders(sheet.createRow(0), headers);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row dataRow = sheet.createRow(rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    writeCell(dataRow.createCell(columnIndex), values.get(columnIndex));
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "excelFile",
                    "order-item-replacement.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private MockMultipartFile rfqImportWorkbook(List<String> headers, List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("rfq");
            writeHeaders(sheet.createRow(0), headers);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row dataRow = sheet.createRow(rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    writeCell(dataRow.createCell(columnIndex), values.get(columnIndex));
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "excelFile",
                    "order-item-append.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private static void writeHeaders(Row row, List<String> headers) {
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            row.createCell(columnIndex).setCellValue(headers.get(columnIndex));
        }
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static class Fixture {
        private OrderManagementService service;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private OrderDeliveryTicketMapper ticketMapper;
        private BusinessFileService businessFileService;
        private AuditService auditService;
    }
}

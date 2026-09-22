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
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
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
        assertRoute("convertUntaxedToTaxIncluded", new Class<?>[]{Long.class},
                PostMapping.class, "/orders/{salesOrderId}/items/convert-untaxed-to-tax-included", "market:sales-order:update");
        assertRoute("convertTaxIncludedToUntaxed", new Class<?>[]{Long.class},
                PostMapping.class, "/orders/{salesOrderId}/items/convert-tax-included-to-untaxed", "market:sales-order:update");
        assertRoute("replaceItemField", new Class<?>[]{Long.class,
                        Class.forName("com.machining.quote.dto.SalesOrderItemFieldReplacementReq")},
                PostMapping.class, "/orders/{salesOrderId}/items/field-replacement", "market:sales-order:update");
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
    void saveItemsUpdatesEditableFieldsWithoutDerivingTaxIncludedPriceFromUntaxedPrice() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(11L, "未同步");
        SalesOrderItem item = item(201L, 11L);
        item.setUntaxedUnitPrice(new BigDecimal("100.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        item.setOrderType("预测订单");
        fixture.withOrder(header, List.of(item));

        SalesOrderItemUpdateReq req = new SalesOrderItemUpdateReq();
        SalesOrderItemUpdateLineReq line = new SalesOrderItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setQty(3);
        line.setUntaxedUnitPrice(new BigDecimal("200.00"));
        line.setVersion(" B ");
        line.setMaterialName(" 6061-T6 ");
        line.setSurfaceTreatment(" Anodized ");
        line.setCustomerDeliveryDate(LocalDate.of(2026, 10, 1));
        line.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        line.setCustomerOrderNo(" PO-ITEM ");
        line.setCustomerProjectNo(" CP-ITEM ");
        line.setCustomerOrderTitle(" Title-ITEM ");
        line.setInternalProjectNo(" IP-ITEM ");
        setLineOrderType(line, "正式订单");
        line.setDescription(" Updated description ");
        line.setStatus("已生成提单");
        line.setRemark(" urgent ");
        req.setItems(List.of(line));

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.saveItems(header.getId(), req);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "untaxedUnitPrice",
                "version",
                "materialName",
                "surfaceTreatment",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "customerOrderNo",
                "customerProjectNo",
                "customerOrderTitle",
                "internalProjectNo",
                "orderType",
                "description",
                "status",
                "remark");
        assertThat(item.getQty()).isEqualTo(3);
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("200.00");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("113.00");
        assertThat(item.getVersion()).isEqualTo("B");
        assertThat(item.getMaterialName()).isEqualTo("6061-T6");
        assertThat(item.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("600.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("339.00");
        assertThat(item.getRemainingQty()).isEqualTo(2);
        assertThat(item.getUnshippedQty()).isEqualTo(2);
        assertThat(item.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(item.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(item.getCustomerOrderNo()).isEqualTo("PO-ITEM");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-ITEM");
        assertThat(item.getCustomerOrderTitle()).isEqualTo("Title-ITEM");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-ITEM");
        assertThat(item.getOrderType()).isEqualTo("正式订单");
        assertThat(item.getDescription()).isEqualTo("Updated description");
        assertThat(item.getStatus()).isEqualTo("已生成提单");
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
    void replaceItemFieldUpdatesAllItemsInSalesOrderWithOneAuditEntry() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(13L, "未同步");
        SalesOrderItem first = item(203L, 13L);
        first.setCustomerOrderNo("PO-OLD-1");
        SalesOrderItem second = item(204L, 13L);
        second.setCustomerOrderNo("PO-OLD-2");
        fixture.withOrder(header, List.of(first, second));

        SalesOrderMaintenanceUpdateResp result = replaceItemField(
                fixture, header.getId(), "customerOrderNo", " PO-BULK ");

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getChangedFields()).containsExactly("customerOrderNo");
        assertThat(first.getCustomerOrderNo()).isEqualTo("PO-BULK");
        assertThat(second.getCustomerOrderNo()).isEqualTo("PO-BULK");
        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verifyAudit(fixture.auditService, "SALES_ORDER_ITEM_UPDATE", header.getId());
    }

    @Test
    void replaceItemFieldRejectsInvalidOrderTypeBeforeUpdatingRows() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(14L, "未同步");
        SalesOrderItem item = item(205L, 14L);
        item.setOrderType("预测订单");
        fixture.withOrder(header, List.of(item));

        assertThatThrownBy(() -> replaceItemField(fixture, header.getId(), "orderType", "临时订单"))
                .hasRootCauseMessage("订单类型必须为预测订单、备货订单或正式订单");

        assertThat(item.getOrderType()).isEqualTo("预测订单");
        verify(fixture.itemMapper, never()).update(any(), any());
    }

    @Test
    void convertUntaxedToTaxIncludedUsesEnabledNormalTaxRateForWholeSalesOrder() {
        Fixture fixture = fixture();
        DictPricingMarkupFeeMapper pricingMarkupFeeMapper = mock(DictPricingMarkupFeeMapper.class);
        ReflectionTestUtils.setField(fixture.maintenanceService, "pricingMarkupFeeMapper", pricingMarkupFeeMapper);
        when(pricingMarkupFeeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                pricingFee(1L, "NORMAL", "TAX", "Tax 10", "10.00", true, 1),
                pricingFee(2L, "NORMAL", "TAX", "Tax 3", "3.00", true, 2)));
        SalesOrderHeader header = header(16L, "NOT_SYNCED");
        SalesOrderItem first = item(206L, 16L);
        first.setLineNo(10);
        first.setQty(2);
        first.setUntaxedUnitPrice(new BigDecimal("100.00"));
        first.setTaxIncludedUnitPrice(new BigDecimal("1.00"));
        SalesOrderItem second = item(207L, 16L);
        second.setLineNo(20);
        second.setQty(3);
        second.setUntaxedUnitPrice(new BigDecimal("12.3456"));
        second.setTaxIncludedUnitPrice(new BigDecimal("1.00"));
        fixture.withOrder(header, List.of(first, second));

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.convertUntaxedToTaxIncluded(header.getId());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getChangedFields()).containsExactly("taxIncludedUnitPrice");
        assertThat(first.getUntaxedUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(first.getTaxIncludedUnitPrice()).isEqualByComparingTo("113.0000");
        assertThat(first.getUntaxedTotalPrice()).isEqualByComparingTo("200.00");
        assertThat(first.getTaxIncludedTotalPrice()).isEqualByComparingTo("226.00");
        assertThat(second.getTaxIncludedUnitPrice()).isEqualByComparingTo("13.9505");
        assertThat(second.getTaxIncludedTotalPrice()).isEqualByComparingTo("41.85");
        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verifyAudit(fixture.auditService, "SALES_ORDER_ITEM_UPDATE", header.getId());
    }

    @Test
    void convertTaxIncludedToUntaxedUsesEnabledNormalTaxRateForWholeSalesOrder() {
        Fixture fixture = fixture();
        DictPricingMarkupFeeMapper pricingMarkupFeeMapper = mock(DictPricingMarkupFeeMapper.class);
        ReflectionTestUtils.setField(fixture.maintenanceService, "pricingMarkupFeeMapper", pricingMarkupFeeMapper);
        when(pricingMarkupFeeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                pricingFee(1L, "NORMAL", "TAX", "Tax", "13.00", true, 1)));
        SalesOrderHeader header = header(17L, "NOT_SYNCED");
        SalesOrderItem item = item(208L, 17L);
        item.setQty(4);
        item.setUntaxedUnitPrice(new BigDecimal("1.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        fixture.withOrder(header, List.of(item));

        SalesOrderMaintenanceUpdateResp result = fixture.maintenanceService.convertTaxIncludedToUntaxed(header.getId());

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactly("untaxedUnitPrice");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("113.00");
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("100.0000");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("400.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("452.00");
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void priceConversionRejectsMissingSourcePriceBeforePartialUpdate() {
        Fixture fixture = fixture();
        DictPricingMarkupFeeMapper pricingMarkupFeeMapper = mock(DictPricingMarkupFeeMapper.class);
        ReflectionTestUtils.setField(fixture.maintenanceService, "pricingMarkupFeeMapper", pricingMarkupFeeMapper);
        when(pricingMarkupFeeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                pricingFee(1L, "NORMAL", "TAX", "Tax", "13.00", true, 1)));
        SalesOrderHeader header = header(18L, "NOT_SYNCED");
        SalesOrderItem item = item(209L, 18L);
        item.setLineNo(10);
        item.setUntaxedUnitPrice(null);
        fixture.withOrder(header, List.of(item));

        assertThatThrownBy(() -> fixture.maintenanceService.convertUntaxedToTaxIncluded(header.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("10")
                .hasMessageContaining("untaxedUnitPrice");
        verify(fixture.itemMapper, never()).update(any(), any());
    }

    @Test
    void maintenanceWritesRejectU9StatusesStillInFlight() {
        for (String status : List.of("同步中", "结果待确认")) {
            Fixture fixture = fixture();
            SalesOrderHeader header = header(19L, status);
            SalesOrderItem item = item(210L, 19L);
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
    void maintenanceWritesAllowAlreadySyncedOrders() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(20L, "已同步");
        SalesOrderItem item = item(211L, 20L);
        fixture.withOrder(header, List.of(item));

        SalesOrderMaintenanceOrderUpdateReq orderReq = new SalesOrderMaintenanceOrderUpdateReq();
        orderReq.setDocumentTypeCode("SO02");
        SalesOrderMaintenanceUpdateResp orderResult = fixture.maintenanceService.saveOrder(header.getId(), orderReq);

        SalesOrderItemUpdateReq itemReq = new SalesOrderItemUpdateReq();
        SalesOrderItemUpdateLineReq line = new SalesOrderItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setRemark("synced order correction");
        itemReq.setItems(List.of(line));
        SalesOrderMaintenanceUpdateResp itemResult = fixture.maintenanceService.saveItems(header.getId(), itemReq);

        assertThat(orderResult.getUpdatedCount()).isEqualTo(1);
        assertThat(itemResult.getUpdatedCount()).isEqualTo(1);
        assertThat(header.getDocumentTypeCode()).isEqualTo("SO02");
        assertThat(item.getRemark()).isEqualTo("synced order correction");
        verify(fixture.headerMapper).update(any(), any());
        verify(fixture.itemMapper).update(any(), any());

        byte[] replacementExcel = excel(
                List.of("图号", "版本", "备注"),
                List.of(item.getPartNo(), item.getVersion(), "synced order replacement"));
        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(replacementExcel));
        SalesOrderItemDataReplacementConfirmResp replacementResult = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(replacementExcel), preview.getPreviewFingerprint());

        assertThat(replacementResult.getSuccess()).isTrue();
        assertThat(replacementResult.getUpdatedCount()).isEqualTo(1);
        assertThat(item.getRemark()).isEqualTo("synced order replacement");
    }

    @Test
    void dataReplacementPreviewAndConfirmUseSalesOrderManagementActionAndDoNotTouchTickets() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(15L, "未同步");
        SalesOrderItem item = item(301L, 15L);
        item.setUntaxedUnitPrice(new BigDecimal("100.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("113.00"));
        item.setOrderType("预测订单");
        fixture.withOrder(header, List.of(item));
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "数量", "客户交期", "内部交期", "未税单价", "含税单价", "客户订单号", "客户项目号", "客户下单抬头", "内部项目号", "订单类型", "物料描述", "订单状态", "备注"),
                List.of(item.getPartNo(), item.getVersion(), item.getLineNo(), 4, "2026-10-11", "2026-10-02", "120.00", "150.00", "PO-X", "CP-X", "TITLE-X", "IP-X", "正式订单", "replacement description", "已生成提单", "note"));

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
                "customerOrderTitle",
                "internalProjectNo",
                "orderType",
                "description",
                "status",
                "remark");
        assertThat(item.getQty()).isEqualTo(4);
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("150.00");
        assertThat(item.getCustomerOrderTitle()).isEqualTo("TITLE-X");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-X");
        assertThat(item.getOrderType()).isEqualTo("正式订单");
        assertThat(item.getDescription()).isEqualTo("replacement description");
        assertThat(item.getStatus()).isEqualTo("已生成提单");
        verify(fixture.ticketMapper, never()).update(any(), any());
        verifyAudit(fixture.auditService, "SALES_ORDER_ITEM_DATA_REPLACEMENT", header.getId());
    }

    @Test
    void dataReplacementUpdatesMaterialNameAndSurfaceTreatment() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(16L, "NOT_SYNCED");
        SalesOrderItem item = item(401L, 16L);
        item.setMaterialName("old material");
        item.setSurfaceTreatment("old surface");
        fixture.withOrder(header, List.of(item));
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "材料名称", "表面处理"),
                List.of(item.getPartNo(), item.getVersion(), item.getLineNo(), "new material", "new surface"));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(excel));
        SalesOrderItemDataReplacementConfirmResp result = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(excel), preview.getPreviewFingerprint());

        assertThat(preview.getRecognizedFields()).contains("materialName", "surfaceTreatment");
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder("materialName", "surfaceTreatment");
        assertThat(item.getMaterialName()).isEqualTo("new material");
        assertThat(item.getSurfaceTreatment()).isEqualTo("new surface");
    }

    @Test
    void dataReplacementAppliesOneExcelRowToAllItemsWithSamePartAndVersionWhenLineNoIsBlank() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(17L, "NOT_SYNCED");
        SalesOrderItem first = item(501L, 17L);
        first.setLineNo(10);
        first.setMaterialName("old first");
        first.setSurfaceTreatment("old first surface");
        SalesOrderItem second = item(502L, 17L);
        second.setLineNo(20);
        second.setPartNo(first.getPartNo());
        second.setVersion(first.getVersion());
        second.setMaterialName("old second");
        second.setSurfaceTreatment("old second surface");
        fixture.withOrder(header, List.of(first, second));
        byte[] excel = excel(
                List.of("图号", "版本", "材料", "表面处理"),
                List.of(first.getPartNo(), first.getVersion(), "shared material", "shared surface"));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(excel));

        assertThat(preview.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getErrorCount()).isZero();
        assertThat(preview.getRows()).hasSize(2);
        assertThat(preview.getRows()).extracting("salesOrderItemId").containsExactly(501L, 502L);
        assertThat(preview.getRows()).extracting("status").containsOnly(SalesOrderU9ItemDataReplacementRowStatus.CHANGED);

        SalesOrderItemDataReplacementConfirmResp result = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(excel), preview.getPreviewFingerprint());

        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder("materialName", "surfaceTreatment");
        assertThat(first.getMaterialName()).isEqualTo("shared material");
        assertThat(first.getSurfaceTreatment()).isEqualTo("shared surface");
        assertThat(second.getMaterialName()).isEqualTo("shared material");
        assertThat(second.getSurfaceTreatment()).isEqualTo("shared surface");
    }

    @Test
    void dataReplacementLineNoNarrowsSamePartAndVersionMatchesToOneSalesOrderItem() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(18L, "NOT_SYNCED");
        SalesOrderItem first = item(601L, 18L);
        first.setLineNo(10);
        first.setMaterialName("first material");
        SalesOrderItem second = item(602L, 18L);
        second.setLineNo(20);
        second.setPartNo(first.getPartNo());
        second.setVersion(first.getVersion());
        second.setMaterialName("second material");
        fixture.withOrder(header, List.of(first, second));
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "材料"),
                List.of(first.getPartNo(), first.getVersion(), second.getLineNo(), "line material"));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(excel));
        SalesOrderItemDataReplacementConfirmResp result = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(excel), preview.getPreviewFingerprint());

        assertThat(preview.getRows()).hasSize(1);
        assertThat(preview.getRows().get(0).getSalesOrderItemId()).isEqualTo(second.getId());
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(first.getMaterialName()).isEqualTo("first material");
        assertThat(second.getMaterialName()).isEqualTo("line material");
    }

    @Test
    void dataReplacementSkipsErrorRowsAndAppliesValidSalesOrderRows() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(19L, "NOT_SYNCED");
        SalesOrderItem invalid = item(701L, 19L);
        invalid.setLineNo(10);
        invalid.setPartNo("P-ERR");
        invalid.setRemark("old invalid");
        SalesOrderItem valid = item(702L, 19L);
        valid.setLineNo(20);
        valid.setPartNo("P-OK");
        valid.setRemark("old valid");
        fixture.withOrder(header, List.of(invalid, valid));
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "客户交期", "备注"),
                List.of("P-ERR", "A", 10, "not-a-date", "bad remark"),
                List.of("P-OK", "A", 20, "", "good remark"));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.replacementService.preview(
                header.getId(), new ByteArrayInputStream(excel));

        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getSummary().getImportedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getErrorCount()).isEqualTo(1);

        SalesOrderItemDataReplacementConfirmResp result = fixture.replacementService.confirm(
                header.getId(), new ByteArrayInputStream(excel), preview.getPreviewFingerprint());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getSummary().getErrorCount()).isEqualTo(1);
        assertThat(invalid.getRemark()).isEqualTo("old invalid");
        assertThat(valid.getRemark()).isEqualTo("good remark");
        verify(fixture.itemMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
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

    private static void setLineOrderType(SalesOrderItemUpdateLineReq line, String orderType) throws Exception {
        SalesOrderItemUpdateLineReq.class.getMethod("setOrderType", String.class).invoke(line, orderType);
    }

    private static SalesOrderMaintenanceUpdateResp replaceItemField(
            Fixture fixture,
            Long salesOrderId,
            String field,
            String value) throws Exception {
        Class<?> reqType = Class.forName("com.machining.quote.dto.SalesOrderItemFieldReplacementReq");
        Object req = reqType.getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(req, "field", field);
        ReflectionTestUtils.setField(req, "value", value);
        Method method = SalesOrderMaintenanceService.class.getMethod("replaceItemField", Long.class, reqType);
        return (SalesOrderMaintenanceUpdateResp) method.invoke(fixture.maintenanceService, salesOrderId, req);
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

    private static DictPricingMarkupFee pricingFee(
            Long id,
            String pricingScope,
            String feeCategory,
            String feeName,
            String ratePercent,
            boolean enabled,
            int sortNo) {
        DictPricingMarkupFee fee = new DictPricingMarkupFee();
        fee.setId(id);
        fee.setPricingScope(pricingScope);
        fee.setFeeCategory(feeCategory);
        fee.setFeeName(feeName);
        fee.setRatePercent(new BigDecimal(ratePercent));
        fee.setEnabled(enabled);
        fee.setSortNo(sortNo);
        return fee;
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

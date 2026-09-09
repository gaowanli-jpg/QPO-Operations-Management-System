package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderItemDetailExportTest {
    private static final String[] DETAIL_HEADERS = {
            "图号", "行号", "版本", "物料号", "物料号状态", "匹配状态", "匹配说明", "数量", "订单状态",
            "U9/变更", "图纸", "图纸预览", "客户下单抬头", "物料描述", "订单类型", "客户订单号", "客户项目号",
            "内部项目号", "客户交期", "内部交期", "材料", "表面处理", "未税单价", "含税单价", "未税总价", "含税总价", "备注"
    };

    @Test
    void exportWritesAllSalesOrderItemRowsUsingDetailTableColumns() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(900L);
        header.setSalesOrderNo("SOQPO-900");

        SalesOrderItem later = item(902L, 900L, 2);
        later.setPartNo("PN-002");
        later.setVersion("B");
        later.setMaterialNo("MAT-002");
        later.setMaterialNoStatus("待同步");
        later.setMatchStatus("未匹配");
        later.setMatchMessage("未找到历史件");
        later.setQty(5);
        later.setStatus("废弃");
        later.setU9ChangeExcluded(true);
        later.setDescription("第二件描述");
        later.setOrderType("备货订单");
        later.setCustomerOrderNo("PO-002");
        later.setCustomerProjectNo("CP-002");
        later.setInternalProjectNo("IP-002");
        later.setMaterialName("SUS304");
        later.setSurfaceTreatment("钝化");
        later.setUntaxedUnitPrice(new BigDecimal("19.80"));
        later.setTaxIncludedUnitPrice(new BigDecimal("22.37"));
        later.setUntaxedTotalPrice(new BigDecimal("99.00"));
        later.setTaxIncludedTotalPrice(new BigDecimal("111.85"));
        later.setRemark("第二件备注");

        SalesOrderItem first = item(901L, 900L, 1);
        first.setPartNo("PN-001");
        first.setVersion("A");
        first.setMaterialNo("MAT-001");
        first.setMaterialNoStatus("已确认");
        first.setMatchStatus("已匹配");
        first.setMatchMessage("历史件复用");
        first.setQty(3);
        first.setStatus("已生成提单");
        first.setU9ChangeExcluded(false);
        first.setDrawingUrl("/files/pn-001.pdf");
        first.setCustomerOrderTitle("客户抬头");
        first.setDescription("首件描述");
        first.setOrderType("正式订单");
        first.setCustomerOrderNo("PO-001");
        first.setCustomerProjectNo("CP-001");
        first.setInternalProjectNo("IP-001");
        first.setCustomerDeliveryDate(LocalDate.of(2026, 9, 20));
        first.setInternalDeliveryDate(LocalDate.of(2026, 9, 18));
        first.setMaterialName("6061-T6");
        first.setSurfaceTreatment("本色阳极");
        first.setUntaxedUnitPrice(new BigDecimal("12.30"));
        first.setTaxIncludedUnitPrice(new BigDecimal("13.90"));
        first.setRemark("首件备注");

        Fixture fixture = fixture(header, List.of(later, first));

        Object exportFile = fixture.serviceClass.getMethod("export", Long.class).invoke(fixture.service, 900L);
        byte[] bytes = (byte[]) exportFile.getClass().getMethod("bytes").invoke(exportFile);
        String fileName = (String) exportFile.getClass().getMethod("fileName").invoke(exportFile);

        assertThat(fileName).isEqualTo("SOQPO-900-零件明细表.xlsx");
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper).selectList(any(QueryWrapper.class));
        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_ITEM_DETAIL_EXPORT"),
                eq("sales_order_header"),
                eq(900L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_ITEM_DETAIL_EXPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("salesOrderId", 900L)
                .containsEntry("salesOrderNo", "SOQPO-900")
                .containsEntry("rowCount", 2);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("零件明细");
            assertThat(sheet).isNotNull();
            assertHeaders(sheet);
            assertThat(text(sheet, 1, 0)).isEqualTo("PN-001");
            assertThat(text(sheet, 1, 1)).isEqualTo("1");
            assertThat(text(sheet, 1, 9)).isEqualTo("正常");
            assertThat(text(sheet, 1, 10)).isEqualTo("已绑定");
            assertThat(text(sheet, 1, 11)).isEqualTo("预览");
            assertThat(text(sheet, 1, 18)).isEqualTo("2026-09-20");
            assertThat(text(sheet, 1, 22)).isEqualTo("¥ 12.30");
            assertThat(text(sheet, 1, 24)).isEqualTo("¥ 36.90");
            assertThat(text(sheet, 1, 25)).isEqualTo("¥ 41.70");

            assertThat(text(sheet, 2, 0)).isEqualTo("PN-002");
            assertThat(text(sheet, 2, 8)).isEqualTo("废弃");
            assertThat(text(sheet, 2, 9)).isEqualTo("已废弃");
            assertThat(text(sheet, 2, 10)).isEqualTo("缺图");
            assertThat(text(sheet, 2, 11)).isEqualTo("无图");
            assertThat(text(sheet, 2, 24)).isEqualTo("¥ 99.00");
            assertThat(text(sheet, 2, 25)).isEqualTo("¥ 111.85");
        }
    }

    private Fixture fixture(SalesOrderHeader header, List<SalesOrderItem> items) throws Exception {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        when(headerMapper.selectById(header.getId())).thenReturn(header);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
        Class<?> serviceClass = Class.forName("com.machining.quote.service.SalesOrderItemDetailExportService");
        Object service = serviceClass.getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return new Fixture(serviceClass, service, itemMapper, dataScopeService, auditService);
    }

    private SalesOrderItem item(Long id, Long salesOrderId, Integer lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        return item;
    }

    private void assertHeaders(Sheet sheet) {
        Row row = sheet.getRow(0);
        assertThat(row).isNotNull();
        for (int i = 0; i < DETAIL_HEADERS.length; i++) {
            assertThat(text(sheet, 0, i)).as("header %s", i + 1).isEqualTo(DETAIL_HEADERS[i]);
        }
    }

    private String text(Sheet sheet, int zeroBasedRow, int zeroBasedColumn) {
        Row row = sheet.getRow(zeroBasedRow);
        if (row == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(row.getCell(zeroBasedColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
    }

    private record Fixture(
            Class<?> serviceClass,
            Object service,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService) {
    }
}

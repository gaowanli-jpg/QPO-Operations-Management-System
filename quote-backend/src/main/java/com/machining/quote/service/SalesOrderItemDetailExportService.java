package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class SalesOrderItemDetailExportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderItemDetailExportService.class);
    private static final String ACTION = "SALES_ORDER_ITEM_DETAIL_EXPORT";
    private static final String SHEET_NAME = "零件明细";

    private static final ExportColumn[] COLUMNS = new ExportColumn[] {
            textColumn("图号", SalesOrderItem::getPartNo),
            integerColumn("行号", SalesOrderItem::getLineNo),
            textColumn("版本", SalesOrderItem::getVersion),
            textColumn("物料号", SalesOrderItem::getMaterialNo),
            textColumn("物料号状态", SalesOrderItem::getMaterialNoStatus),
            textColumn("匹配状态", SalesOrderItem::getMatchStatus),
            textColumn("匹配说明", SalesOrderItem::getMatchMessage),
            integerColumn("数量", SalesOrderItem::getQty),
            textColumn("订单状态", SalesOrderItem::getStatus),
            textColumn("U9/变更", item -> Boolean.TRUE.equals(item.getU9ChangeExcluded()) ? "已废弃" : "正常"),
            textColumn("图纸", item -> isBlank(item.getDrawingUrl()) ? "缺图" : "已绑定"),
            textColumn("图纸预览", item -> isBlank(item.getDrawingUrl()) ? "无图" : "预览"),
            textColumn("客户下单抬头", SalesOrderItem::getCustomerOrderTitle),
            textColumn("物料描述", SalesOrderItem::getDescription),
            textColumn("订单类型", SalesOrderItem::getOrderType),
            textColumn("客户订单号", SalesOrderItem::getCustomerOrderNo),
            textColumn("客户项目号", SalesOrderItem::getCustomerProjectNo),
            textColumn("内部项目号", SalesOrderItem::getInternalProjectNo),
            textColumn("客户交期", item -> dateText(item.getCustomerDeliveryDate())),
            textColumn("内部交期", item -> dateText(item.getInternalDeliveryDate())),
            textColumn("材料", SalesOrderItem::getMaterialName),
            textColumn("表面处理", SalesOrderItem::getSurfaceTreatment),
            moneyColumn("未税单价", SalesOrderItem::getUntaxedUnitPrice),
            moneyColumn("含税单价", SalesOrderItem::getTaxIncludedUnitPrice),
            moneyColumn("未税总价", SalesOrderItemDetailExportService::lineUntaxedTotal),
            moneyColumn("含税总价", SalesOrderItemDetailExportService::lineTaxIncludedTotal),
            textColumn("备注", SalesOrderItem::getRemark)
    };

    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;

    public ExportFile export(Long salesOrderId) throws IOException {
        long startedAt = System.currentTimeMillis();
        log.info("Sales order item detail export started salesOrderId={}", salesOrderId);
        try {
            SalesOrderHeader order = requireSalesOrder(salesOrderId);
            List<SalesOrderItem> items = listItems(salesOrderId);
            byte[] bytes = renderWorkbook(items);
            recordAudit(order, items.size());
            log.info("Sales order item detail export completed salesOrderId={} salesOrderNo={} rowCount={} durationMs={}",
                    order.getId(), firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()), items.size(), System.currentTimeMillis() - startedAt);
            return new ExportFile(fileName(order), bytes);
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Sales order item detail export rejected salesOrderId={} reason={}", salesOrderId, safeLogMessage(e));
            } else {
                log.error("Sales order item detail export failed salesOrderId={} durationMs={}", salesOrderId, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
        } catch (IOException e) {
            log.error("Sales order item detail export failed salesOrderId={} durationMs={}", salesOrderId, System.currentTimeMillis() - startedAt, e);
            throw e;
        }
    }

    private SalesOrderHeader requireSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader order = salesOrderHeaderMapper.selectById(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
        return order;
    }

    private List<SalesOrderItem> listItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .orderByAsc("line_no")
                .orderByAsc("id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator
                        .comparing(SalesOrderItem::getLineNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SalesOrderItem::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private byte[] renderWorkbook(List<SalesOrderItem> items) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            ExportStyles styles = createStyles(workbook);
            writeHeader(sheet, styles.header());
            writeItems(sheet, items == null ? List.of() : items, styles);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, COLUMNS.length - 1));
            applyColumnWidths(sheet);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(22F);
        for (int i = 0; i < COLUMNS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(COLUMNS[i].label());
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeItems(Sheet sheet, List<SalesOrderItem> items, ExportStyles styles) {
        for (int i = 0; i < items.size(); i++) {
            Row row = sheet.createRow(i + 1);
            SalesOrderItem item = items.get(i);
            for (int columnIndex = 0; columnIndex < COLUMNS.length; columnIndex++) {
                ExportColumn column = COLUMNS[columnIndex];
                Cell cell = row.createCell(columnIndex);
                writeCell(cell, column, item, styles);
            }
        }
    }

    private void writeCell(Cell cell, ExportColumn column, SalesOrderItem item, ExportStyles styles) {
        Object value = column.value(item);
        switch (column.type()) {
            case INTEGER -> {
                cell.setCellStyle(styles.integer());
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else {
                    cell.setCellValue(textValue(value));
                }
            }
            case MONEY -> {
                cell.setCellStyle(styles.money());
                BigDecimal decimal = decimalValue(value);
                if (decimal == null) {
                    cell.setBlank();
                } else {
                    cell.setCellValue(decimal.doubleValue());
                }
            }
            case TEXT -> {
                cell.setCellStyle(styles.text());
                cell.setCellValue(textValue(value));
            }
        }
    }

    private ExportStyles createStyles(Workbook workbook) {
        CellStyle header = borderedStyle(workbook);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        header.setFont(headerFont);
        header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle text = borderedStyle(workbook);
        text.setAlignment(HorizontalAlignment.LEFT);
        text.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle integer = borderedStyle(workbook);
        integer.setAlignment(HorizontalAlignment.RIGHT);
        integer.setVerticalAlignment(VerticalAlignment.CENTER);
        integer.setDataFormat(workbook.createDataFormat().getFormat("0"));

        CellStyle money = borderedStyle(workbook);
        money.setAlignment(HorizontalAlignment.RIGHT);
        money.setVerticalAlignment(VerticalAlignment.CENTER);
        money.setDataFormat(workbook.createDataFormat().getFormat("¥ #,##0.00"));
        return new ExportStyles(header, text, integer, money);
    }

    private CellStyle borderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void applyColumnWidths(Sheet sheet) {
        int[] widths = {
                18, 8, 10, 16, 14, 14, 24, 10, 14,
                12, 10, 12, 20, 28, 14, 18, 18, 18,
                14, 14, 18, 18, 14, 14, 14, 14, 24
        };
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void recordAudit(SalesOrderHeader order, int rowCount) {
        if (auditService == null || order == null) {
            return;
        }
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("salesOrderId", order.getId());
        afterData.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        afterData.put("rowCount", rowCount);
        auditService.record(
                ACTION,
                "sales_order_header",
                order.getId(),
                null,
                afterData,
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction(ACTION),
                null
        );
    }

    private static ExportColumn textColumn(String label, Function<SalesOrderItem, Object> value) {
        return new ExportColumn(label, ColumnType.TEXT, value);
    }

    private static ExportColumn integerColumn(String label, Function<SalesOrderItem, Object> value) {
        return new ExportColumn(label, ColumnType.INTEGER, value);
    }

    private static ExportColumn moneyColumn(String label, Function<SalesOrderItem, Object> value) {
        return new ExportColumn(label, ColumnType.MONEY, value);
    }

    private static BigDecimal lineUntaxedTotal(SalesOrderItem item) {
        if (item == null) {
            return null;
        }
        if (item.getUntaxedTotalPrice() != null) {
            return item.getUntaxedTotalPrice();
        }
        return multiply(item.getQty(), item.getUntaxedUnitPrice());
    }

    private static BigDecimal lineTaxIncludedTotal(SalesOrderItem item) {
        if (item == null) {
            return null;
        }
        if (item.getTaxIncludedTotalPrice() != null) {
            return item.getTaxIncludedTotalPrice();
        }
        return multiply(item.getQty(), item.getTaxIncludedUnitPrice());
    }

    private static BigDecimal multiply(Integer qty, BigDecimal unitPrice) {
        if (qty == null || unitPrice == null) {
            return null;
        }
        return BigDecimal.valueOf(qty.longValue()).multiply(unitPrice);
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String textValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return String.valueOf(value);
    }

    private String fileName(SalesOrderHeader order) {
        String prefix = firstNonBlank(order == null ? null : order.getSalesOrderNo(), order == null ? null : order.getDocumentNo(), order == null ? null : String.valueOf(order.getId()));
        return safeFileName(prefix) + "-零件明细表.xlsx";
    }

    private static String dateText(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeFileName(String value) {
        String text = firstNonBlank(value, "sales-order");
        return text.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean isBusinessFailure(RuntimeException e) {
        return e != null && e.getClass().equals(RuntimeException.class) && e.getCause() == null && !safeLogMessage(e).isBlank();
    }

    private String safeLogMessage(RuntimeException e) {
        String message = e == null ? "" : e.getMessage();
        if (message == null || message.isBlank()) {
            return e == null ? "unknown" : e.getClass().getSimpleName();
        }
        return message.replaceAll("[\\r\\n\\t]+", " ");
    }

    public record ExportFile(String fileName, byte[] bytes) {
    }

    private enum ColumnType {
        TEXT,
        INTEGER,
        MONEY
    }

    private record ExportColumn(String label, ColumnType type, Function<SalesOrderItem, Object> valueResolver) {
        private Object value(SalesOrderItem item) {
            return valueResolver.apply(item);
        }
    }

    private record ExportStyles(CellStyle header, CellStyle text, CellStyle integer, CellStyle money) {
    }
}

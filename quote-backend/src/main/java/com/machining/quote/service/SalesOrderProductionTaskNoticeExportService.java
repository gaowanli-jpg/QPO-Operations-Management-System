package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderProductionTaskNoticeExportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderProductionTaskNoticeExportService.class);
    private static final String TEMPLATE_PATH = "/templates/production-task-notice-template.xlsx";
    private static final String SHEET_NAME = "StandardDocPrintTemplate";
    private static final int DATA_START_ROW = 11;
    private static final int TEMPLATE_DATA_ROW_COUNT = 51;
    private static final int TEMPLATE_DATA_END_ROW = DATA_START_ROW + TEMPLATE_DATA_ROW_COUNT - 1;
    private static final int FIRST_ROW_AFTER_TEMPLATE_DATA = TEMPLATE_DATA_END_ROW + 1;
    private static final int TEMPLATE_FORM_NO_ROW = 66;
    private static final int FIRST_DATA_COLUMN = 1;
    private static final int LAST_DATA_COLUMN = 34;

    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;

    public byte[] export(Long salesOrderId) throws IOException {
        long startedAt = System.currentTimeMillis();
        log.info("Sales order production task notice export started salesOrderId={}", salesOrderId);
        try {
            SalesOrderHeader order = requireSalesOrder(salesOrderId);
            List<SalesOrderItem> items = listItems(salesOrderId);
            byte[] bytes = renderWorkbook(order, items);
            recordAudit(order, items.size());
            log.info("Sales order production task notice export completed salesOrderId={} salesOrderNo={} rowCount={} durationMs={}",
                    order.getId(), firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()), items.size(), System.currentTimeMillis() - startedAt);
            return bytes;
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Sales order production task notice export rejected salesOrderId={} reason={}", salesOrderId, safeLogMessage(e));
            } else {
                log.error("Sales order production task notice export failed salesOrderId={} durationMs={}",
                        salesOrderId, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
        } catch (IOException e) {
            log.error("Sales order production task notice export failed salesOrderId={} durationMs={}",
                    salesOrderId, System.currentTimeMillis() - startedAt, e);
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

    private byte[] renderWorkbook(SalesOrderHeader order, List<SalesOrderItem> items) throws IOException {
        try (InputStream inputStream = templateStream();
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new RuntimeException("生产任务通知单模板缺少工作表");
            }
            prepareDataRows(sheet, items.size());
            writeHeader(sheet, order, items.size());
            writeItems(sheet, items);
            clearUnusedTemplateRows(sheet, items.size());
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private InputStream templateStream() {
        InputStream inputStream = SalesOrderProductionTaskNoticeExportService.class.getResourceAsStream(TEMPLATE_PATH);
        if (inputStream == null) {
            throw new RuntimeException("生产任务通知单模板不存在");
        }
        return inputStream;
    }

    private void writeHeader(Sheet sheet, SalesOrderHeader order, int itemCount) {
        setText(sheet, "M3", "常熟市兆恒众力精密机械有限公司");
        setText(sheet, "Q5", "生产任务通知单");
        setText(sheet, "AA8", "下单日期：");
        setText(sheet, "AD8", dateText(order.getSalesOrderDate()));
        setText(sheet, TEMPLATE_FORM_NO_ROW + extraTemplateRows(itemCount), "AB",
                "表单编号：" + firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId())));
    }

    private void writeItems(Sheet sheet, List<SalesOrderItem> items) {
        for (int i = 0; i < items.size(); i++) {
            SalesOrderItem item = items.get(i);
            int rowIndex = DATA_START_ROW + i;
            int displayLineNo = (i + 1) * 10;
            setNumber(sheet, rowIndex, "B", displayLineNo);
            setText(sheet, rowIndex, "E", isRepeated(item) ? "重复" : "新件");
            setText(sheet, rowIndex, "H", item.getInternalProjectNo());
            setText(sheet, rowIndex, "J", item.getCustomerOrderNo());
            setText(sheet, rowIndex, "L", item.getCustomerProjectNo());
            setText(sheet, rowIndex, "P", item.getPartNo());
            setNumber(sheet, rowIndex, "T", item.getQty() == null ? 0 : item.getQty());
            setText(sheet, rowIndex, "U", item.getVersion());
            setText(sheet, rowIndex, "V", firstNonBlank(item.getEngineeringMaterialName(), item.getMaterialName()));
            setText(sheet, rowIndex, "Y", item.getSurfaceTreatment());
            setText(sheet, rowIndex, "AC", dateText(item.getSalesOrderDate() == null ? null : item.getSalesOrderDate()));
            setText(sheet, rowIndex, "AF", dateText(item.getInternalDeliveryDate()));
            setText(sheet, rowIndex, "AG", item.getRemark());
        }
    }

    private boolean isRepeated(SalesOrderItem item) {
        String status = item == null ? null : item.getMatchStatus();
        return "已匹配".equals(status) || "重复".equals(status);
    }

    private void prepareDataRows(Sheet sheet, int itemCount) {
        if (itemCount <= TEMPLATE_DATA_ROW_COUNT) {
            return;
        }
        int extraRows = extraTemplateRows(itemCount);
        Row templateRow = sheet.getRow(TEMPLATE_DATA_END_ROW);
        List<CellRangeAddress> templateMergedRegions = mergedRegionsForRow(sheet, TEMPLATE_DATA_END_ROW);
        sheet.shiftRows(FIRST_ROW_AFTER_TEMPLATE_DATA, sheet.getLastRowNum(), extraRows, true, false);
        for (int i = 0; i < extraRows; i++) {
            int rowIndex = FIRST_ROW_AFTER_TEMPLATE_DATA + i;
            copyBlankRow(sheet, templateRow, rowIndex);
            copyMergedRegions(sheet, templateMergedRegions, TEMPLATE_DATA_END_ROW, rowIndex);
        }
    }

    private int extraTemplateRows(int itemCount) {
        return Math.max(0, itemCount - TEMPLATE_DATA_ROW_COUNT);
    }

    private void clearUnusedTemplateRows(Sheet sheet, int itemCount) {
        int firstUnusedRow = DATA_START_ROW + Math.max(itemCount, 0);
        int lastTemplateRow = DATA_START_ROW + TEMPLATE_DATA_ROW_COUNT - 1;
        for (int rowIndex = firstUnusedRow; rowIndex <= lastTemplateRow; rowIndex++) {
            clearRowValues(getOrCreateRow(sheet, rowIndex));
        }
    }

    private void copyBlankRow(Sheet sheet, Row templateRow, int targetRowIndex) {
        Row row = getOrCreateRow(sheet, targetRowIndex);
        if (templateRow == null) {
            return;
        }
        row.setHeight(templateRow.getHeight());
        for (int col = FIRST_DATA_COLUMN; col <= LAST_DATA_COLUMN; col++) {
            Cell sourceCell = templateRow.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Cell targetCell = row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (sourceCell != null) {
                targetCell.setCellStyle(sourceCell.getCellStyle());
            }
            targetCell.setBlank();
        }
    }

    private List<CellRangeAddress> mergedRegionsForRow(Sheet sheet, int rowIndex) {
        List<CellRangeAddress> regions = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == rowIndex && region.getLastRow() == rowIndex) {
                regions.add(region.copy());
            }
        }
        return regions;
    }

    private void copyMergedRegions(Sheet sheet, List<CellRangeAddress> templateRegions, int sourceRowIndex, int targetRowIndex) {
        for (CellRangeAddress source : templateRegions) {
            int rowOffset = targetRowIndex - sourceRowIndex;
            CellRangeAddress target = new CellRangeAddress(
                    source.getFirstRow() + rowOffset,
                    source.getLastRow() + rowOffset,
                    source.getFirstColumn(),
                    source.getLastColumn());
            if (!mergedRegionExists(sheet, target)) {
                sheet.addMergedRegion(target);
            }
        }
    }

    private boolean mergedRegionExists(Sheet sheet, CellRangeAddress target) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress existing = sheet.getMergedRegion(i);
            if (existing.getFirstRow() == target.getFirstRow()
                    && existing.getLastRow() == target.getLastRow()
                    && existing.getFirstColumn() == target.getFirstColumn()
                    && existing.getLastColumn() == target.getLastColumn()) {
                return true;
            }
        }
        return false;
    }

    private void clearRowValues(Row row) {
        for (int col = FIRST_DATA_COLUMN; col <= LAST_DATA_COLUMN; col++) {
            Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    private void setText(Sheet sheet, String address, String value) {
        setText(sheet, rowIndex(address), columnAddress(address), value);
    }

    private void setText(Sheet sheet, int rowIndex, String column, String value) {
        Cell cell = getOrCreateRow(sheet, rowIndex).getCell(columnIndex(column), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(value == null ? "" : value);
    }

    private void setNumber(Sheet sheet, int rowIndex, String column, int value) {
        Cell cell = getOrCreateRow(sheet, rowIndex).getCell(columnIndex(column), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(value);
    }

    private int rowIndex(String address) {
        String digits = address.replaceAll("[^0-9]", "");
        return Integer.parseInt(digits) - 1;
    }

    private String columnAddress(String address) {
        return address.replaceAll("[^A-Z]", "");
    }

    private int columnIndex(String column) {
        int result = 0;
        for (int i = 0; i < column.length(); i++) {
            result = result * 26 + (column.charAt(i) - 'A' + 1);
        }
        return result - 1;
    }

    private String dateText(LocalDate value) {
        return value == null ? "" : value.toString();
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
                "SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT",
                "sales_order_header",
                order.getId(),
                null,
                afterData,
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT"),
                null
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
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
}

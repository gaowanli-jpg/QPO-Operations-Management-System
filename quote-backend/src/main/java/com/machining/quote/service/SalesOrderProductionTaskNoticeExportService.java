package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
public class SalesOrderProductionTaskNoticeExportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderProductionTaskNoticeExportService.class);
    private static final String TEMPLATE_PATH = "/templates/production-task-notice-template.xlsx";
    private static final String SHEET_NAME = "StandardDocPrintTemplate";
    private static final int DATA_START_ROW = 11;
    private static final int TEMPLATE_DATA_ROW_COUNT = 51;
    private static final int TEMPLATE_BODY_ROW_COUNT = TEMPLATE_DATA_ROW_COUNT + 1;
    private static final int TEMPLATE_DATA_END_ROW = DATA_START_ROW + TEMPLATE_DATA_ROW_COUNT - 1;
    private static final int FIRST_ROW_AFTER_TEMPLATE_DATA = TEMPLATE_DATA_END_ROW + 1;
    private static final int TEMPLATE_FORM_NO_ROW = 66;
    private static final int FIRST_DATA_COLUMN = 1;
    private static final int LAST_DATA_COLUMN = 34;
    private static final int COLUMN_WIDTH_UNIT = 256;
    private static final int MAX_TOTAL_ADAPTIVE_GROWTH = 36 * COLUMN_WIDTH_UNIT;
    private static final float DEFAULT_DATA_ROW_HEIGHT_POINTS = 14.4F;
    private static final List<AdaptiveTextField> ADAPTIVE_TEXT_FIELDS = List.of(
            new AdaptiveTextField("H", "I", 22, SalesOrderItem::getInternalProjectNo),
            new AdaptiveTextField("J", "K", 24, SalesOrderItem::getCustomerOrderNo),
            new AdaptiveTextField("L", "O", 22, SalesOrderItem::getCustomerProjectNo),
            new AdaptiveTextField("P", "S", 26, SalesOrderItem::getPartNo),
            new AdaptiveTextField("U", "U", 14, SalesOrderItem::getVersion),
            new AdaptiveTextField("V", "X", 26, item -> firstNonBlankStatic(item.getEngineeringMaterialName(), item.getMaterialName())),
            new AdaptiveTextField("Y", "AB", 24, SalesOrderItem::getSurfaceTreatment),
            new AdaptiveTextField("AG", "AI", 36, SalesOrderItem::getRemark)
    );

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
                .and(wrapper -> wrapper.ne("status", QuoteStatusWorkflow.DISCARDED).or().isNull("status"))
                .and(wrapper -> wrapper.eq("u9_change_excluded", false).or().isNull("u9_change_excluded"))
                .orderByAsc("line_no")
                .orderByAsc("id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> !QuoteStatusWorkflow.DISCARDED.equals(item.getStatus()))
                .filter(item -> !Boolean.TRUE.equals(item.getU9ChangeExcluded()))
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
            resizeTemplateDataRows(sheet, items.size());
            writeHeader(sheet, order, items.size());
            writeItems(sheet, items);
            applyAdaptiveLayout(workbook, sheet, items);
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
        setFormNo(sheet, itemCount, firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId())));
    }

    private void setFormNo(Sheet sheet, int itemCount, String salesOrderNo) {
        String value = "表单编号：" + salesOrderNo;
        Optional<Cell> templateCell = findTextCell(sheet, "表单编号：");
        if (templateCell.isPresent()) {
            templateCell.get().setCellValue(value);
            return;
        }
        setText(sheet, TEMPLATE_FORM_NO_ROW + dataRowDelta(itemCount), "AB", value);
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

    private void applyAdaptiveLayout(Workbook workbook, Sheet sheet, List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<AdaptiveTextField, Integer> currentWidths = new LinkedHashMap<>();
        Map<AdaptiveTextField, Integer> requestedGrowth = new LinkedHashMap<>();
        int totalRequestedGrowth = 0;
        for (AdaptiveTextField field : ADAPTIVE_TEXT_FIELDS) {
            int currentWidth = columnWidth(sheet, field.startColumn(), field.endColumn());
            currentWidths.put(field, currentWidth);
            int requiredWidth = requiredColumnWidth(field, items);
            int growth = Math.max(0, requiredWidth - currentWidth);
            requestedGrowth.put(field, growth);
            totalRequestedGrowth += growth;
        }

        double growthScale = totalRequestedGrowth == 0
                ? 0D
                : Math.min(1D, (double) MAX_TOTAL_ADAPTIVE_GROWTH / totalRequestedGrowth);
        for (AdaptiveTextField field : ADAPTIVE_TEXT_FIELDS) {
            int growth = (int) Math.floor(requestedGrowth.get(field) * growthScale);
            if (growth > 0) {
                resizeColumnGroup(sheet, field.startColumn(), field.endColumn(), currentWidths.get(field) + growth);
            }
        }

        Map<Short, CellStyle> wrappedStyles = new HashMap<>();
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            Row row = getOrCreateRow(sheet, DATA_START_ROW + itemIndex);
            float baseHeight = row.getHeightInPoints();
            if (baseHeight <= 0) {
                baseHeight = DEFAULT_DATA_ROW_HEIGHT_POINTS;
            }
            int maxLines = 1;
            SalesOrderItem item = items.get(itemIndex);
            for (AdaptiveTextField field : ADAPTIVE_TEXT_FIELDS) {
                Cell cell = row.getCell(columnIndex(field.startColumn()), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(wrappedStyle(workbook, cell, wrappedStyles));
                maxLines = Math.max(maxLines, wrappedLineCount(field.value(item), columnWidth(sheet, field.startColumn(), field.endColumn())));
            }
            if (maxLines > 1) {
                row.setHeightInPoints(baseHeight * maxLines);
            }
        }
    }

    private int requiredColumnWidth(AdaptiveTextField field, List<SalesOrderItem> items) {
        double maxDisplayWidth = 0D;
        for (SalesOrderItem item : items) {
            maxDisplayWidth = Math.max(maxDisplayWidth, displayWidth(field.value(item)));
        }
        int requiredWidthInCharacters = (int) Math.ceil(maxDisplayWidth + 2D);
        return Math.min(field.maxWidthInCharacters() * COLUMN_WIDTH_UNIT, requiredWidthInCharacters * COLUMN_WIDTH_UNIT);
    }

    private int wrappedLineCount(String value, int widthInUnits) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        double availableWidth = Math.max(1D, (double) widthInUnits / COLUMN_WIDTH_UNIT - 1D);
        int lines = 0;
        for (String segment : value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            lines += Math.max(1, (int) Math.ceil(displayWidth(segment) / availableWidth));
        }
        return lines;
    }

    private double displayWidth(String value) {
        if (value == null || value.isEmpty()) {
            return 0D;
        }
        double width = 0D;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (codePoint == '\t') {
                width += 4D;
            } else if (isWideCodePoint(codePoint)) {
                width += 2D;
            } else {
                width += 1D;
            }
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private boolean isWideCodePoint(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF01 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);
    }

    private CellStyle wrappedStyle(Workbook workbook, Cell cell, Map<Short, CellStyle> wrappedStyles) {
        short sourceStyleIndex = cell.getCellStyle().getIndex();
        return wrappedStyles.computeIfAbsent(sourceStyleIndex, styleIndex -> {
            CellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(cell.getCellStyle());
            style.setWrapText(true);
            return style;
        });
    }

    private int columnWidth(Sheet sheet, String startColumn, String endColumn) {
        int total = 0;
        for (int index = columnIndex(startColumn); index <= columnIndex(endColumn); index++) {
            total += sheet.getColumnWidth(index);
        }
        return total;
    }

    private void resizeColumnGroup(Sheet sheet, String startColumn, String endColumn, int targetWidth) {
        int startIndex = columnIndex(startColumn);
        int endIndex = columnIndex(endColumn);
        int currentWidth = columnWidth(sheet, startColumn, endColumn);
        int extraWidth = Math.max(0, targetWidth - currentWidth);
        int columnCount = endIndex - startIndex + 1;
        int baseGrowth = extraWidth / columnCount;
        int remainder = extraWidth % columnCount;
        for (int index = startIndex; index <= endIndex; index++) {
            int growth = baseGrowth + (index - startIndex < remainder ? 1 : 0);
            int width = Math.min(255 * COLUMN_WIDTH_UNIT, sheet.getColumnWidth(index) + growth);
            sheet.setColumnWidth(index, width);
        }
    }

    private boolean isRepeated(SalesOrderItem item) {
        String status = item == null ? null : item.getMatchStatus();
        return "已匹配".equals(status) || "重复".equals(status);
    }

    private void resizeTemplateDataRows(Sheet sheet, int itemCount) {
        if (itemCount < TEMPLATE_DATA_ROW_COUNT) {
            removeTemplateRows(sheet, DATA_START_ROW + Math.max(itemCount, 0), FIRST_ROW_AFTER_TEMPLATE_DATA);
            return;
        }
        appendExtraTemplateRows(sheet, itemCount);
        removeTemplateRows(sheet, DATA_START_ROW + Math.max(itemCount, 0), DATA_START_ROW + Math.max(itemCount, 0));
    }

    private void appendExtraTemplateRows(Sheet sheet, int itemCount) {
        if (itemCount <= TEMPLATE_DATA_ROW_COUNT) {
            return;
        }
        int extraRows = dataRowDelta(itemCount);
        if (extraRows <= 0) {
            return;
        }
        Row templateRow = sheet.getRow(TEMPLATE_DATA_END_ROW);
        List<CellRangeAddress> templateMergedRegions = mergedRegionsForRow(sheet, TEMPLATE_DATA_END_ROW);
        sheet.shiftRows(FIRST_ROW_AFTER_TEMPLATE_DATA, sheet.getLastRowNum(), extraRows, true, false);
        for (int i = 0; i < extraRows; i++) {
            int rowIndex = FIRST_ROW_AFTER_TEMPLATE_DATA + i;
            copyMergedRegions(sheet, templateMergedRegions, TEMPLATE_DATA_END_ROW, rowIndex);
            copyBlankRow(sheet, templateRow, rowIndex);
        }
    }

    private int dataRowDelta(int itemCount) {
        return Math.max(0, itemCount) - TEMPLATE_BODY_ROW_COUNT;
    }

    private void removeTemplateRows(Sheet sheet, int firstRow, int lastRow) {
        if (firstRow > lastRow) {
            return;
        }
        int rowsToDelete = lastRow - firstRow + 1;
        removeMergedRegionsWithin(sheet, firstRow, lastRow);
        int lastRowNum = sheet.getLastRowNum();
        if (lastRow < lastRowNum) {
            sheet.shiftRows(lastRow + 1, lastRowNum, -rowsToDelete, true, false);
            hideRows(sheet, lastRowNum - rowsToDelete + 1, lastRowNum);
        }
    }

    private void hideRows(Sheet sheet, int firstRow, int lastRow) {
        if (firstRow > lastRow) {
            return;
        }
        removeMergedRegionsWithin(sheet, firstRow, lastRow);
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                row.setZeroHeight(true);
            }
        }
    }

    private void copyBlankRow(Sheet sheet, Row templateRow, int targetRowIndex) {
        Row row = getOrCreateRow(sheet, targetRowIndex);
        if (templateRow == null) {
            return;
        }
        row.setHeight(templateRow.getHeight());
        for (int col = FIRST_DATA_COLUMN; col <= LAST_DATA_COLUMN; col++) {
            Cell sourceCell = templateRow.getCell(col, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
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
            sheet.addMergedRegionUnsafe(target);
        }
    }

    private void removeMergedRegionsWithin(Sheet sheet, int firstRow, int lastRow) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= firstRow && region.getLastRow() <= lastRow) {
                sheet.removeMergedRegion(i);
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

    private Optional<Cell> findTextCell(Sheet sheet, String prefix) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && cell.getStringCellValue() != null
                        && cell.getStringCellValue().startsWith(prefix)) {
                    return Optional.of(cell);
                }
            }
        }
        return Optional.empty();
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
        return firstNonBlankStatic(values);
    }

    private static String firstNonBlankStatic(String... values) {
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

    private record AdaptiveTextField(
            String startColumn,
            String endColumn,
            int maxWidthInCharacters,
            Function<SalesOrderItem, String> valueExtractor) {

        private String value(SalesOrderItem item) {
            return item == null || valueExtractor == null ? "" : valueExtractor.apply(item);
        }
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

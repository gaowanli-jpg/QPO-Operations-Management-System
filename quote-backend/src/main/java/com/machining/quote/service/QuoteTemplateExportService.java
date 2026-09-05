package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuoteTemplateExportService {
    private static final int PROCESS_BLOCK_WIDTH = 4;
    private static final int FEE_BLOCK_WIDTH = 2;
    private static final List<String> DETAIL_BASIC_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位"
    );
    private static final List<String> DETAIL_MATERIAL_COST_HEADERS = List.of("重量(KG)", "材料费");
    private static final List<String> DETAIL_TOTAL_HEADERS = List.of(
            "基础成本", "百分比费用合计", "含税单价", "含税总价", "交货周期", "备注"
    );
    private static final List<String> SIMPLE_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "材料编码", "材料类型", "材质分类", "表面处理", "版本", "数量", "单位",
            "含税单价", "含税总价", "交货周期", "备注"
    );
    private static final List<String> PROCESS_HEADERS = QuoteProcessExportSupport.QUOTE_PROCESS_VALUE_HEADERS;

    @Autowired
    private QuoteTemplateService quoteTemplateService;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private QuoteProcessService quoteProcessService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataFormatter dataFormatter = new DataFormatter();

    public void exportTemplateQuoteDetail(Integer rfqId, HttpServletResponse response) throws IOException {
        quoteProcessService.assertQuoteExportable(rfqId);
        assertOrderDataScope(rfqId);
        try (Workbook workbook = quoteTemplateService.openTemplateWorkbook()) {
            QuoteTemplateService.TemplateLayout layout = quoteTemplateService.templateLayout();
            writeTemplateQuote(workbook, rfqId, false, layout);
            write(response, workbook, buildFileName(rfqId, "客户模板详细报价单"));
        }
    }

    public void exportOptimizedTemplateQuoteDetail(Integer rfqId, HttpServletResponse response) throws IOException {
        quoteProcessService.assertQuoteExportable(rfqId);
        assertOrderDataScope(rfqId);
        try (Workbook workbook = quoteTemplateService.openTemplateWorkbook()) {
            QuoteTemplateService.TemplateLayout layout = quoteTemplateService.templateLayout();
            writeTemplateQuote(workbook, rfqId, false, layout, true);
            write(response, workbook, buildFileName(rfqId, "优化客户模板详细报价单"));
        }
    }

    public void exportTemplateQuoteSimple(Integer rfqId, HttpServletResponse response) throws IOException {
        quoteProcessService.assertQuoteExportable(rfqId);
        assertOrderDataScope(rfqId);
        try (Workbook workbook = quoteTemplateService.openTemplateWorkbook()) {
            QuoteTemplateService.TemplateLayout layout = quoteTemplateService.templateLayout();
            writeTemplateQuote(workbook, rfqId, true, layout);
            mergeSimpleTitleAndFooter(workbook.getSheetAt(0), layout);
            write(response, workbook, buildFileName(rfqId, "客户模板简化报价单"));
        }
    }

    private void assertOrderDataScope(Integer rfqId) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderRfqMapper);
        }
    }
    private void writeTemplateQuote(Workbook workbook, Integer rfqId, boolean simpleMode, QuoteTemplateService.TemplateLayout layout) {
        writeTemplateQuote(workbook, rfqId, simpleMode, layout, false);
    }

    private void writeTemplateQuote(Workbook workbook, Integer rfqId, boolean simpleMode,
                                    QuoteTemplateService.TemplateLayout layout, boolean optimized) {
        Sheet sheet = workbook.getSheetAt(0);
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<QuoteOptimizedExportCalculator.QuoteExportRowData> optimizedRows = optimized ? optimizedRows(items) : List.of();

        TemplateColumns columns = simpleMode
                ? rebuildSimpleTableColumns(sheet, layout)
                : configureDetailColumns(sheet, items, layout, optimizedRows);
        prepareDetailRows(sheet, items.size(), layout);

        for (int i = 0; i < items.size(); i++) {
            Row row = getOrCreateRow(sheet, layout.detailStartRowIndex() + i);
            clearRowValues(row, maxColumn(sheet));
            writeQuoteRow(row, items.get(i), i + 1, columns, simpleMode,
                    optimized ? optimizedRows.get(i) : null);
        }
    }

    private TemplateColumns configureDetailColumns(Sheet sheet, List<ItemQuoteVersion> items, QuoteTemplateService.TemplateLayout layout) {
        return configureDetailColumns(sheet, items, layout, List.of());
    }

    private TemplateColumns configureDetailColumns(Sheet sheet, List<ItemQuoteVersion> items,
                                                   QuoteTemplateService.TemplateLayout layout,
                                                   List<QuoteOptimizedExportCalculator.QuoteExportRowData> optimizedRows) {
        boolean optimized = optimizedRows != null && !optimizedRows.isEmpty();
        List<String> processNames = optimized ? collectOptimizedProcessNames(optimizedRows) : collectQuoteProcessNames(items);
        int requiredProcessSlots = Math.max(processNames.size(), 1);
        List<String> feeNames = optimized ? collectOptimizedFeeNames(optimizedRows) : collectQuoteFeeNames(items);
        int requiredFeeSlots = Math.max(feeNames.size(), 1);

        return rebuildDetailTableColumns(sheet, requiredProcessSlots, processNames, requiredFeeSlots, feeNames, layout);
    }

    private TemplateColumns rebuildSimpleTableColumns(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        removeDetailTableMergedRegions(sheet, layout);
        int maxCol = Math.max(maxColumn(sheet), SIMPLE_HEADERS.size() - 1);
        Row groupRow = getOrCreateRow(sheet, layout.groupRowIndex());
        Row headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
        clearCells(groupRow, 0, maxCol + 1);
        clearCells(headerRow, 0, maxCol + 1);

        writeGroupHeader(sheet, 0, SIMPLE_HEADERS.size() - 4, "基础信息", layout);
        writeHeaders(headerRow, 0, SIMPLE_HEADERS.subList(0, SIMPLE_HEADERS.size() - 4));
        writeGroupHeader(sheet, SIMPLE_HEADERS.size() - 4, 4, "合计", layout);
        writeHeaders(headerRow, SIMPLE_HEADERS.size() - 4, SIMPLE_HEADERS.subList(SIMPLE_HEADERS.size() - 4, SIMPLE_HEADERS.size()));
        return new TemplateColumns(headerMap(sheet, layout), -1, 0, List.of(), PROCESS_BLOCK_WIDTH, -1, 0, List.of());
    }

    private TemplateColumns rebuildDetailTableColumns(Sheet sheet,
                                                      int processSlots,
                                                      List<String> processNames,
                                                      int feeSlots,
                                                      List<String> feeNames,
                                                      QuoteTemplateService.TemplateLayout layout) {
        int processStartCol = DETAIL_BASIC_HEADERS.size() + DETAIL_MATERIAL_COST_HEADERS.size();
        int feeStartCol = processStartCol + processSlots * PROCESS_BLOCK_WIDTH;
        int totalStartCol = feeStartCol + feeSlots * FEE_BLOCK_WIDTH;
        int requiredColumnCount = totalStartCol + DETAIL_TOTAL_HEADERS.size();

        removeDetailTableMergedRegions(sheet, layout);
        int maxCol = Math.max(maxColumn(sheet), requiredColumnCount - 1);
        Row groupRow = getOrCreateRow(sheet, layout.groupRowIndex());
        Row headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
        clearCells(groupRow, 0, maxCol + 1);
        clearCells(headerRow, 0, maxCol + 1);

        writeGroupHeader(sheet, 0, DETAIL_BASIC_HEADERS.size(), "基础信息", layout);
        writeHeaders(headerRow, 0, DETAIL_BASIC_HEADERS);
        writeGroupHeader(sheet, DETAIL_BASIC_HEADERS.size(), DETAIL_MATERIAL_COST_HEADERS.size(), "材料费", layout);
        writeHeaders(headerRow, DETAIL_BASIC_HEADERS.size(), DETAIL_MATERIAL_COST_HEADERS);
        writeProcessHeaders(sheet, processStartCol, processSlots, processNames, PROCESS_BLOCK_WIDTH, layout);
        writeFeeHeaders(sheet, feeStartCol, feeSlots, feeNames, layout);
        writeGroupHeader(sheet, totalStartCol, DETAIL_TOTAL_HEADERS.size(), "合计", layout);
        writeHeaders(headerRow, totalStartCol, DETAIL_TOTAL_HEADERS);

        return new TemplateColumns(headerMap(sheet, layout), processStartCol, processSlots, processNames, PROCESS_BLOCK_WIDTH,
                feeStartCol, feeSlots, feeNames);
    }

    private void writeHeaders(Row headerRow, int startCol, List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            setCell(headerRow, startCol + i, headers.get(i));
        }
    }

    private void removeDetailTableMergedRegions(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        int footerStart = findFooterStartRow(sheet, layout);
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() <= footerStart - 1 && region.getLastRow() >= layout.groupRowIndex()) {
                sheet.removeMergedRegion(i);
            }
        }
    }

    private void writeQuoteRow(Row row, ItemQuoteVersion item, int index, TemplateColumns columns, boolean simpleMode,
                               QuoteOptimizedExportCalculator.QuoteExportRowData optimizedData) {
        Map<String, Object> materialData = normalizeMap(item.getMaterialData());
        List<Map<String, Object>> processRoutes = optimizedData == null
                ? normalizeProcessRoute(item.getProcessRoute())
                : optimizedData.processRoutes();
        List<Map<String, Object>> feeRows = optimizedData == null
                ? normalizeProcessRoute(item.getFeeData())
                : optimizedData.feeRows();

        double materialCost = firstNonNullDouble(materialData.get("cost"), 0.0);
        double processCost = sumProcessCost(processRoutes);
        double markupCost = optimizedData == null ? sumCost(feeRows) : optimizedData.markupCost();
        double baseCost = optimizedData == null
                ? (item.getBaseCost() == null ? materialCost + processCost : item.getBaseCost())
                : optimizedData.baseCost();
        double unitPrice = optimizedData == null
                ? (item.getTaxIncludedUnitPrice() == null ? materialCost + processCost + markupCost : item.getTaxIncludedUnitPrice())
                : optimizedData.taxIncludedUnitPrice();
        double totalPrice = optimizedData == null
                ? (item.getTaxIncludedTotalPrice() == null ? unitPrice * safeInt(item.getQty()) : item.getTaxIncludedTotalPrice())
                : optimizedData.taxIncludedTotalPrice();
        boolean missingDrawing = isMissingDrawing(item);

        setByHeader(row, columns.headers(), "序号", index);
        setByHeader(row, columns.headers(), "图号", item.getPartNo());
        setByHeader(row, columns.headers(), "物料描述", item.getDescription());
        setByHeader(row, columns.headers(), "图纸状态", resolveDrawingStatus(item));
        setByHeader(row, columns.headers(), "材料名称", item.getMaterialName());
        setByHeader(row, columns.headers(), "材料编码", item.getEngineeringMaterialCode());
        setByHeader(row, columns.headers(), "材料类型", item.getEngineeringMaterialTypeName());
        setByHeader(row, columns.headers(), "材质分类", item.getEngineeringMaterialCategory());
        setByHeader(row, columns.headers(), "表面处理", item.getSurfaceTreatment());
        setByHeader(row, columns.headers(), "版本", item.getVersion());
        setByHeader(row, columns.headers(), "数量", safeInt(item.getQty()));
        setByHeader(row, columns.headers(), "单位", item.getUnit());

        if (!simpleMode) {
            setByHeader(row, columns.headers(), "重量(KG)", missingDrawing ? "" : firstNonNullDouble(materialData.get("weight"), 0.0));
            setByHeader(row, columns.headers(), "材料费", missingDrawing ? "" : materialCost);

            Map<String, QuoteProcessExportSupport.MergedProcess> processMap = QuoteProcessExportSupport.mergeProcessRoutes(processRoutes);
            for (int i = 0; i < columns.processSlots(); i++) {
                int startCol = columns.processStartCol() + i * columns.processBlockWidth();
                String processName = i < columns.processNames().size() ? columns.processNames().get(i) : "";
                QuoteProcessExportSupport.MergedProcess process = processMap.get(processName);
                if (process != null && !missingDrawing) {
                    setCell(row, startCol, process.calcType());
                    setCell(row, startCol + 1, process.inputVal());
                    setCell(row, startCol + 2, process.weightedUnitRate());
                    setCell(row, startCol + 3, process.lineCost());
                    clearCells(row, startCol + PROCESS_HEADERS.size(), columns.processBlockWidth() - PROCESS_HEADERS.size());
                } else {
                    clearCells(row, startCol, columns.processBlockWidth());
                }
            }

            Map<String, Map<String, Object>> feeMap = mapByName(feeRows, "feeName");
            for (int i = 0; i < columns.feeSlots(); i++) {
                int startCol = columns.feeStartCol() + i * FEE_BLOCK_WIDTH;
                String feeName = i < columns.feeNames().size() ? columns.feeNames().get(i) : "";
                Map<String, Object> fee = feeMap.get(feeName);
                setCell(row, startCol, missingDrawing || fee == null ? "" : firstNonNullDouble(fee.get("ratePercent"), 0.0));
                setCell(row, startCol + 1, missingDrawing || fee == null ? "" : firstNonNullDouble(fee.get("cost"), 0.0));
            }

            setByHeader(row, columns.headers(), "基础成本", missingDrawing ? "" : baseCost);
            setByHeader(row, columns.headers(), "百分比费用合计", missingDrawing ? "" : markupCost);
        }

        setByHeader(row, columns.headers(), "含税单价", missingDrawing ? "" : unitPrice);
        setByHeader(row, columns.headers(), "含税总价", missingDrawing ? "" : totalPrice);
        setByHeader(row, columns.headers(), "交货周期", resolveQuoteExportLeadTime(item));
        setByHeader(row, columns.headers(), "备注", missingDrawing ? appendRemark(item.getRemark(), "无图，未核价") : item.getRemark());
    }

    private void mergeSimpleTitleAndFooter(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        int lastCol = SIMPLE_HEADERS.size() - 1;
        if (!isBlank(dataFormatter.formatCellValue(getOrCreateRow(sheet, 0).getCell(0)))) {
            mergeReplacing(sheet, 0, 0, 0, lastCol);
        }
        int footerRowIndex = findFooterStartRow(sheet, layout);
        Row footerRow = sheet.getRow(footerRowIndex);
        if (footerRow != null && !isBlank(dataFormatter.formatCellValue(footerRow.getCell(0)))) {
            mergeReplacing(sheet, footerRowIndex, footerRowIndex, 0, lastCol);
        }
    }

    private void prepareDetailRows(Sheet sheet, int itemCount, QuoteTemplateService.TemplateLayout layout) {
        int detailStartRowIndex = layout.detailStartRowIndex();
        int footerStart = findFooterStartRow(sheet, layout);
        int currentRows = Math.max(footerStart - detailStartRowIndex, 1);
        if (itemCount > currentRows) {
            sheet.shiftRows(footerStart, sheet.getLastRowNum(), itemCount - currentRows, true, false);
        } else if (itemCount < currentRows) {
            int removeCount = currentRows - itemCount;
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = detailStartRowIndex + itemCount; rowIndex < footerStart; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }
            sheet.shiftRows(footerStart, lastRow, -removeCount, true, false);
        }

        Row templateRow = getOrCreateRow(sheet, detailStartRowIndex);
        int maxCol = maxColumn(sheet);
        for (int i = 0; i < itemCount; i++) {
            Row row = getOrCreateRow(sheet, detailStartRowIndex + i);
            copyRowStyle(templateRow, row, maxCol);
            clearRowValues(row, maxCol);
        }
    }

    private void writeProcessHeaders(Sheet sheet, int processStartCol, int processSlots, List<String> processNames,
                                     int processBlockWidth, QuoteTemplateService.TemplateLayout layout) {
        if (processStartCol < 0) {
            return;
        }
        Row groupRow = getOrCreateRow(sheet, layout.groupRowIndex());
        Row headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
        for (int i = 0; i < processSlots; i++) {
            int startCol = processStartCol + i * processBlockWidth;
            String processName = i < processNames.size() ? processNames.get(i) : "";
            writeGroupHeader(sheet, startCol, processBlockWidth, processName, layout);
            for (int offset = 0; offset < PROCESS_HEADERS.size(); offset++) {
                setCell(headerRow, startCol + offset, PROCESS_HEADERS.get(offset));
            }
            clearCells(headerRow, startCol + PROCESS_HEADERS.size(), processBlockWidth - PROCESS_HEADERS.size());
            clearCells(groupRow, startCol + 1, processBlockWidth - 1);
        }
    }

    private void writeFeeHeaders(Sheet sheet, int feeStartCol, int feeSlots, List<String> feeNames, QuoteTemplateService.TemplateLayout layout) {
        if (feeStartCol < 0 || feeSlots <= 0) {
            return;
        }
        writeGroupHeader(sheet, feeStartCol, feeSlots * FEE_BLOCK_WIDTH, "百分比费用", layout);
        Row headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
        for (int i = 0; i < feeSlots; i++) {
            int startCol = feeStartCol + i * FEE_BLOCK_WIDTH;
            String feeName = i < feeNames.size() ? feeNames.get(i) : "";
            if (feeName.isEmpty() && feeNames.isEmpty()) {
                setCell(headerRow, startCol, "费率(%)");
                setCell(headerRow, startCol + 1, "金额");
            } else {
                setCell(headerRow, startCol, feeName.isEmpty() ? "" : feeName + "费率(%)");
                setCell(headerRow, startCol + 1, feeName.isEmpty() ? "" : feeName + "金额");
            }
        }
    }

    private void writeGroupHeader(Sheet sheet, int startCol, int width, String title, QuoteTemplateService.TemplateLayout layout) {
        if (startCol < 0 || width <= 0) {
            return;
        }
        Row row = getOrCreateRow(sheet, layout.groupRowIndex());
        setCell(row, startCol, title);
        clearCells(row, startCol + 1, Math.max(width - 1, 0));
        if (width > 1) {
            mergeReplacing(sheet, layout.groupRowIndex(), layout.groupRowIndex(), startCol, startCol + width - 1);
        }
    }

    private void mergeReplacing(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.intersects(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol))) {
                sheet.removeMergedRegion(i);
            }
        }
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId)
                .and(wrapper -> wrapper
                        .ne("status", QuoteStatusWorkflow.DISCARDED)
                        .or()
                        .isNull("status"))
                .orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private Map<String, Integer> headerMap(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        return headerMap(sheet.getRow(layout.headerRowIndex()));
    }

    private Map<String, Integer> headerMap(Row row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (row == null) {
            return result;
        }
        for (Cell cell : row) {
            String header = normalizeHeader(dataFormatter.formatCellValue(cell));
            if (!header.isEmpty()) {
                result.put(header, cell.getColumnIndex());
            }
        }
        return result;
    }

    private int findFooterStartRow(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        for (int rowIndex = layout.detailStartRowIndex(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String firstCell = row == null ? "" : dataFormatter.formatCellValue(row.getCell(0)).trim();
            if (!firstCell.isEmpty() && !firstCell.matches("\\d+(?:\\.0+)?")) {
                return rowIndex;
            }
        }
        return layout.detailStartRowIndex() + 1;
    }

    private void copyRowStyle(Row source, Row target, int maxCol) {
        if (source == null || target == null) {
            return;
        }
        target.setHeight(source.getHeight());
        for (int col = 0; col <= maxCol; col++) {
            Cell sourceCell = source.getCell(col);
            Cell targetCell = getOrCreateCell(target, col);
            if (sourceCell != null) {
                targetCell.setCellStyle(sourceCell.getCellStyle());
            }
        }
    }

    private void clearRowValues(Row row, int maxCol) {
        for (int col = 0; col <= maxCol; col++) {
            Cell cell = row.getCell(col);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    private void clearCells(Row row, int startCol, int count) {
        for (int i = 0; i < count; i++) {
            getOrCreateCell(row, startCol + i).setBlank();
        }
    }

    private void setByHeader(Row row, Map<String, Integer> headers, String header, Object value) {
        Integer col = headers.get(normalizeHeader(header));
        if (col != null) {
            setCell(row, col, value);
        }
    }

    private void setCell(Row row, int col, Object value) {
        setCell(getOrCreateCell(row, col), value);
    }

    private void setCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        cell.setCellValue(text);
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    private Cell getOrCreateCell(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? row.createCell(col) : cell;
    }

    private int maxColumn(Sheet sheet) {
        int max = 0;
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getLastCellNum() > 0) {
                max = Math.max(max, row.getLastCellNum() - 1);
            }
        }
        return max;
    }

    private List<String> collectQuoteProcessNames(List<ItemQuoteVersion> items) {
        return QuoteProcessExportSupport.collectProcessNames(items, this::normalizeProcessRoute);
    }

    private List<QuoteOptimizedExportCalculator.QuoteExportRowData> optimizedRows(List<ItemQuoteVersion> items) {
        List<QuoteOptimizedExportCalculator.QuoteExportRowData> rows = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            rows.add(QuoteOptimizedExportCalculator.calculate(
                    item,
                    normalizeMap(item.getMaterialData()),
                    normalizeProcessRoute(item.getProcessRoute()),
                    normalizeProcessRoute(item.getFeeData())
            ));
        }
        return rows;
    }

    private List<String> collectOptimizedProcessNames(List<QuoteOptimizedExportCalculator.QuoteExportRowData> rows) {
        List<String> hourlyNames = new ArrayList<>();
        List<String> nonHourlyNames = new ArrayList<>();
        for (QuoteOptimizedExportCalculator.QuoteExportRowData row : rows) {
            for (QuoteProcessExportSupport.MergedProcess process : QuoteProcessExportSupport.mergeProcessRoutes(row.processRoutes()).values()) {
                String name = process.processName();
                if (hourlyNames.contains(name) || nonHourlyNames.contains(name)) {
                    continue;
                }
                if (process.nonHourly()) {
                    nonHourlyNames.add(name);
                } else {
                    hourlyNames.add(name);
                }
            }
        }
        hourlyNames.addAll(nonHourlyNames);
        return hourlyNames;
    }

    private List<String> collectOptimizedFeeNames(List<QuoteOptimizedExportCalculator.QuoteExportRowData> rows) {
        List<String> names = new ArrayList<>();
        for (QuoteOptimizedExportCalculator.QuoteExportRowData row : rows) {
            for (Map<String, Object> fee : row.feeRows()) {
                String feeName = safe(fee.get("feeName"));
                if (!feeName.isEmpty() && !names.contains(feeName)) {
                    names.add(feeName);
                }
            }
        }
        return names;
    }

    private List<String> collectQuoteFeeNames(List<ItemQuoteVersion> items) {
        List<String> names = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            for (Map<String, Object> fee : normalizeProcessRoute(item.getFeeData())) {
                String feeName = safe(fee.get("feeName"));
                if (!feeName.isEmpty() && !names.contains(feeName)) {
                    names.add(feeName);
                }
            }
        }
        return names;
    }

    private List<Map<String, Object>> normalizeProcessRoute(Object routeObject) {
        if (routeObject == null) {
            return List.of();
        }
        if (routeObject instanceof List<?> rawList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    result.add(normalized);
                }
            }
            sortProcessRoute(result);
            return result;
        }
        if (routeObject instanceof String text && !text.trim().isEmpty()) {
            try {
                List<Map<String, Object>> result = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
                sortProcessRoute(result);
                return result;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private void sortProcessRoute(List<Map<String, Object>> routes) {
        boolean hasSortNo = routes.stream().anyMatch(route -> intValue(route.get("sortNo")) != null);
        if (!hasSortNo) {
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).putIfAbsent("__index", i);
        }
        routes.sort((left, right) -> {
            Integer leftSort = intValue(left.get("sortNo"));
            Integer rightSort = intValue(right.get("sortNo"));
            if (leftSort != null && rightSort != null && !leftSort.equals(rightSort)) {
                return leftSort.compareTo(rightSort);
            }
            if (leftSort != null && rightSort == null) {
                return -1;
            }
            if (leftSort == null && rightSort != null) {
                return 1;
            }
            return intValue(left.get("__index")).compareTo(intValue(right.get("__index")));
        });
        routes.forEach(route -> route.remove("__index"));
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
            return normalized;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Map<String, Map<String, Object>> mapByName(List<Map<String, Object>> rows, String nameKey) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = safe(row.get(nameKey));
            if (!name.isEmpty()) {
                result.put(name, row);
            }
        }
        return result;
    }

    private double sumProcessCost(List<Map<String, Object>> routes) {
        double total = 0.0;
        for (Map<String, Object> route : routes) {
            total += processLineCost(route);
        }
        return total;
    }

    private double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = firstNonNullDouble(route.get("inputVal"), 0.0);
        Double unitRate = doubleValue(route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private double sumCost(List<Map<String, Object>> rows) {
        double total = 0.0;
        for (Map<String, Object> row : rows) {
            total += firstNonNullDouble(row.get("cost"), 0.0);
        }
        return total;
    }

    private String resolveQuoteExportLeadTime(ItemQuoteVersion item) {
        return leadTimeService.effectiveLeadTime(item);
    }

    private String buildFileName(Integer rfqId, String suffix) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        String rfqNo = order == null ? "" : safe(order.getRfqNo());
        String prefix = rfqNo.isEmpty() ? "RFQ-" + rfqId : rfqNo;
        return prefix + "-" + suffix;
    }

    private void write(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName + ".xlsx");
        workbook.write(response.getOutputStream());
    }

    private double firstNonNullDouble(Object primary, Object fallback) {
        Double value = doubleValue(primary);
        if (value != null) {
            return value;
        }
        value = doubleValue(fallback);
        return value == null ? 0.0 : value;
    }

    private Object safeNumber(Object value) {
        Double number = doubleValue(value);
        return number == null ? "" : number;
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        Double number = doubleValue(value);
        return number == null ? null : number.intValue();
    }

    private boolean isMissingDrawing(ItemQuoteVersion item) {
        return isBlank(item.getDrawingUrl());
    }

    private String resolveDrawingStatus(ItemQuoteVersion item) {
        return isBlank(item.getDrawingUrl()) ? "无图" : "有图";
    }

    private String appendRemark(String original, String extra) {
        if (isBlank(original)) {
            return extra;
        }
        return original + "；" + extra;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replace('\uFEFF', ' ')
                .replace('（', '(')
                .replace('）', ')')
                .replace('％', '%')
                .replaceAll("[\\s\\u200B\\u200C\\u200D]+", "")
                .trim();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record TemplateColumns(Map<String, Integer> headers, int processStartCol, int processSlots,
                                   List<String> processNames, int processBlockWidth,
                                   int feeStartCol, int feeSlots, List<String> feeNames) {
    }
}

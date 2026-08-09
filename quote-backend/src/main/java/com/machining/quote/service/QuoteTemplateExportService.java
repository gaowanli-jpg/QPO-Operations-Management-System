package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuoteTemplateExportService {
    private static final int PROCESS_BLOCK_WIDTH = 6;
    private static final int LEGACY_PROCESS_BLOCK_WIDTH = 5;
    private static final int FEE_BLOCK_WIDTH = 2;
    private static final List<String> MATERIAL_IDENTITY_HEADERS = List.of("材料编码", "材料类型", "材质分类");
    private static final List<String> SIMPLE_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "材料编码", "材料类型", "材质分类", "表面处理", "版本", "数量", "单位",
            "含税单价", "含税总价", "交货周期", "备注"
    );
    private static final List<String> OLD_PROCESS_HEADERS = List.of("工艺名称", "类型", "工时/金额", "单价", "费用");
    private static final List<String> PROCESS_HEADERS = QuoteProcessExportSupport.PROCESS_VALUE_HEADERS;

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

    public void exportTemplateQuoteSimple(Integer rfqId, HttpServletResponse response) throws IOException {
        quoteProcessService.assertQuoteExportable(rfqId);
        assertOrderDataScope(rfqId);
        try (Workbook workbook = quoteTemplateService.openTemplateWorkbook()) {
            QuoteTemplateService.TemplateLayout layout = quoteTemplateService.templateLayout();
            simplifyTemplateSheet(workbook.getSheetAt(0), layout);
            writeTemplateQuote(workbook, rfqId, true, layout);
            write(response, workbook, buildFileName(rfqId, "客户模板简化报价单"));
        }
    }

    private void assertOrderDataScope(Integer rfqId) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderRfqMapper);
        }
    }
    private void writeTemplateQuote(Workbook workbook, Integer rfqId, boolean simpleMode, QuoteTemplateService.TemplateLayout layout) {
        Sheet sheet = workbook.getSheetAt(0);
        List<ItemQuoteVersion> items = listItems(rfqId);

        ensureMaterialIdentityColumns(sheet, layout);

        TemplateColumns columns = simpleMode
                ? simpleColumns(sheet, layout)
                : configureDetailColumns(sheet, items, layout);
        prepareDetailRows(sheet, items.size(), layout);

        for (int i = 0; i < items.size(); i++) {
            Row row = getOrCreateRow(sheet, layout.detailStartRowIndex() + i);
            clearRowValues(row, maxColumn(sheet));
            writeQuoteRow(row, items.get(i), i + 1, columns, simpleMode);
        }
    }

    private TemplateColumns configureDetailColumns(Sheet sheet, List<ItemQuoteVersion> items, QuoteTemplateService.TemplateLayout layout) {
        List<String> processNames = collectQuoteProcessNames(items);
        int requiredProcessSlots = Math.max(processNames.size(), 1);
        List<String> feeNames = collectQuoteFeeNames(items);
        int requiredFeeSlots = Math.max(feeNames.size(), 1);

        ColumnLayout columnLayout = ensureDetailLayout(sheet, requiredProcessSlots, requiredFeeSlots, layout);
        if (requiredProcessSlots > columnLayout.processSlots()) {
            int extraBlocks = requiredProcessSlots - columnLayout.processSlots();
            int insertAt = columnLayout.feeStartCol() >= 0 ? columnLayout.feeStartCol() : columnLayout.baseCostCol();
            for (int i = 0; i < extraBlocks; i++) {
                int blockWidth = columnLayout.processBlockWidth();
                int sourceStart = columnLayout.processStartCol() + Math.max(columnLayout.processSlots() - 1, 0) * blockWidth;
                insertColumns(sheet, insertAt + i * blockWidth, blockWidth, sourceStart, blockWidth);
            }
        }

        columnLayout = detectColumnLayout(sheet, layout);
        int feeSlots = columnLayout.feeSlots();
        if (requiredFeeSlots > feeSlots) {
            int extraBlocks = requiredFeeSlots - feeSlots;
            int sourceStart = columnLayout.feeStartCol() + Math.max(feeSlots - 1, 0) * FEE_BLOCK_WIDTH;
            for (int i = 0; i < extraBlocks; i++) {
                insertColumns(sheet, columnLayout.baseCostCol() + i * FEE_BLOCK_WIDTH, FEE_BLOCK_WIDTH, sourceStart, FEE_BLOCK_WIDTH);
            }
        }

        columnLayout = detectColumnLayout(sheet, layout);
        int processSlots = Math.max(columnLayout.processSlots(), requiredProcessSlots);
        writeProcessHeaders(sheet, columnLayout.processStartCol(), processSlots, processNames, columnLayout.processBlockWidth(), layout);

        int finalFeeSlots = Math.max(Math.max(columnLayout.feeSlots(), feeNames.size()), 1);
        writeFeeHeaders(sheet, columnLayout.feeStartCol(), finalFeeSlots, feeNames, layout);
        writeGroupHeader(sheet, columnLayout.baseCostCol(), 6, "合计", layout);

        Map<String, Integer> headers = headerMap(sheet, layout);
        return new TemplateColumns(headers, columnLayout.processStartCol(), processSlots, processNames, columnLayout.processBlockWidth(),
                columnLayout.feeStartCol(), finalFeeSlots, feeNames);
    }

    private TemplateColumns simpleColumns(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        return new TemplateColumns(headerMap(sheet, layout), -1, 0, List.of(), PROCESS_BLOCK_WIDTH, -1, 0, List.of());
    }

    private void writeQuoteRow(Row row, ItemQuoteVersion item, int index, TemplateColumns columns, boolean simpleMode) {
        Map<String, Object> materialData = normalizeMap(item.getMaterialData());
        List<Map<String, Object>> processRoutes = normalizeProcessRoute(item.getProcessRoute());
        List<Map<String, Object>> feeRows = normalizeProcessRoute(item.getFeeData());

        double materialCost = firstNonNullDouble(materialData.get("cost"), 0.0);
        double processCost = sumProcessCost(processRoutes);
        double markupCost = sumCost(feeRows);
        double baseCost = item.getBaseCost() == null ? materialCost + processCost : item.getBaseCost();
        double unitPrice = item.getTaxIncludedUnitPrice() == null ? materialCost + processCost + markupCost : item.getTaxIncludedUnitPrice();
        double totalPrice = item.getTaxIncludedTotalPrice() == null ? unitPrice * safeInt(item.getQty()) : item.getTaxIncludedTotalPrice();
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
                    setCell(row, startCol, process.processCode());
                    setCell(row, startCol + 1, process.categoryName());
                    setCell(row, startCol + 2, process.detailName());
                    setCell(row, startCol + 3, process.inputVal());
                    setCell(row, startCol + 4, process.weightedUnitRate());
                    setCell(row, startCol + 5, process.lineCost());
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

    private void simplifyTemplateSheet(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        Map<String, Integer> sourceHeaders = headerMap(sheet, layout);
        List<Integer> keepColumns = new ArrayList<>();
        for (String header : SIMPLE_HEADERS) {
            Integer column = sourceHeaders.get(normalizeHeader(header));
            if (column == null || column < 0) {
                throw new RuntimeException("报价单模板缺少简化报价单必要表头");
            }
            keepColumns.add(column);
        }
        keepColumns.sort(Comparator.naturalOrder());
        if (new HashSet<>(keepColumns).size() != SIMPLE_HEADERS.size()) {
            throw new RuntimeException("报价单模板缺少简化报价单必要表头");
        }

        Map<Integer, Integer> newColByOldCol = new LinkedHashMap<>();
        for (int i = 0; i < keepColumns.size(); i++) {
            newColByOldCol.put(keepColumns.get(i), i);
        }

        List<CellRangeAddress> adjustedMergedRegions = adjustedMergedRegions(sheet, newColByOldCol);
        removeAllMergedRegions(sheet);
        compactColumns(sheet, keepColumns, newColByOldCol);
        addMergedRegions(sheet, adjustedMergedRegions);

        writeGroupHeader(sheet, 0, SIMPLE_HEADERS.size() - 4, "基础信息", layout);
        writeGroupHeader(sheet, SIMPLE_HEADERS.size() - 4, 4, "合计", layout);
    }

    private void ensureMaterialIdentityColumns(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        Row headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
        int materialNameCol = findHeader(headerRow, "材料名称", 0);
        if (materialNameCol < 0) {
            return;
        }
        int insertCursor = materialNameCol + 1;
        for (String header : MATERIAL_IDENTITY_HEADERS) {
            int existingCol = findHeader(headerRow, header, 0);
            if (existingCol >= 0) {
                insertCursor = Math.max(insertCursor, existingCol + 1);
                continue;
            }
            insertColumnsLike(sheet, insertCursor, 1);
            headerRow = getOrCreateRow(sheet, layout.headerRowIndex());
            setCell(headerRow, insertCursor, header);
            Row groupRow = getOrCreateRow(sheet, layout.groupRowIndex());
            setCell(groupRow, insertCursor, "");
            insertCursor++;
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

    private List<CellRangeAddress> adjustedMergedRegions(Sheet sheet, Map<Integer, Integer> newColByOldCol) {
        List<CellRangeAddress> result = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            Integer firstCol = null;
            Integer lastCol = null;
            for (Map.Entry<Integer, Integer> entry : newColByOldCol.entrySet()) {
                int oldCol = entry.getKey();
                if (oldCol >= region.getFirstColumn() && oldCol <= region.getLastColumn()) {
                    int newCol = entry.getValue();
                    firstCol = firstCol == null ? newCol : Math.min(firstCol, newCol);
                    lastCol = lastCol == null ? newCol : Math.max(lastCol, newCol);
                }
            }
            if (firstCol == null || lastCol == null) {
                continue;
            }
            if (region.getFirstRow() == region.getLastRow() && firstCol.equals(lastCol)) {
                continue;
            }
            CellRangeAddress adjusted = new CellRangeAddress(region.getFirstRow(), region.getLastRow(), firstCol, lastCol);
            if (result.stream().noneMatch(existing -> existing.intersects(adjusted))) {
                result.add(adjusted);
            }
        }
        return result;
    }

    private void removeAllMergedRegions(Sheet sheet) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            sheet.removeMergedRegion(i);
        }
    }

    private void addMergedRegions(Sheet sheet, List<CellRangeAddress> regions) {
        for (CellRangeAddress region : regions) {
            if (sheet.getNumMergedRegions() == 0 || !hasMergedRegionConflict(sheet, region)) {
                sheet.addMergedRegion(region);
            }
        }
    }

    private boolean hasMergedRegionConflict(Sheet sheet, CellRangeAddress candidate) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).intersects(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void compactColumns(Sheet sheet, List<Integer> keepColumns, Map<Integer, Integer> newColByOldCol) {
        int maxCol = maxColumn(sheet);
        List<Integer> widths = keepColumns.stream().map(sheet::getColumnWidth).toList();
        List<Boolean> hidden = keepColumns.stream().map(sheet::isColumnHidden).toList();
        Set<Integer> keepSet = new HashSet<>(keepColumns);

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Integer oldCol : keepColumns) {
                int newCol = newColByOldCol.get(oldCol);
                Cell sourceCell = row.getCell(oldCol);
                Cell targetCell = row.getCell(newCol);
                if (sourceCell == null) {
                    if (targetCell != null && !keepSet.contains(newCol)) {
                        row.removeCell(targetCell);
                    } else if (targetCell != null) {
                        targetCell.setBlank();
                    }
                    continue;
                }
                if (targetCell == null) {
                    targetCell = row.createCell(newCol);
                }
                copyCellWithinWorkbook(sourceCell, targetCell);
            }
            for (int col = keepColumns.size(); col <= maxCol; col++) {
                Cell cell = row.getCell(col);
                if (cell != null) {
                    row.removeCell(cell);
                }
            }
        }

        for (int col = 0; col < widths.size(); col++) {
            sheet.setColumnWidth(col, widths.get(col));
            sheet.setColumnHidden(col, hidden.get(col));
        }
    }

    private void insertColumns(Sheet sheet, int insertAt, int count, int sourceStart, int sourceWidth) {
        int lastCol = maxColumn(sheet);
        sheet.shiftColumns(insertAt, lastCol, count);
        for (int offset = 0; offset < count; offset++) {
            int sourceCol = sourceStart + (offset % sourceWidth);
            int targetCol = insertAt + offset;
            sheet.setColumnWidth(targetCol, sheet.getColumnWidth(sourceCol));
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Cell sourceCell = row.getCell(sourceCol);
                Cell targetCell = getOrCreateCell(row, targetCol);
                if (sourceCell != null) {
                    targetCell.setCellStyle(sourceCell.getCellStyle());
                }
                targetCell.setBlank();
            }
        }
    }

    private ColumnLayout detectColumnLayout(Sheet sheet, QuoteTemplateService.TemplateLayout layout) {
        Row headerRow = sheet.getRow(layout.headerRowIndex());
        int processStart = findNewProcessStart(headerRow);
        int processBlockWidth = PROCESS_BLOCK_WIDTH;
        if (processStart < 0) {
            processStart = findHeader(headerRow, "工艺名称", 0);
            processBlockWidth = processStart >= 0 ? LEGACY_PROCESS_BLOCK_WIDTH : PROCESS_BLOCK_WIDTH;
        }
        int processSlots = countProcessSlots(headerRow, processStart, processBlockWidth);
        int feeStart = findHeaderContaining(headerRow, "费率(%)",
                processStart < 0 ? 0 : processStart + processSlots * processBlockWidth);
        int baseCost = findHeader(headerRow, "基础成本", 0);
        int feeSlots = feeStart >= 0 && baseCost > feeStart ? (baseCost - feeStart) / FEE_BLOCK_WIDTH : 0;
        return new ColumnLayout(processStart, processSlots, processBlockWidth, feeStart, feeSlots, baseCost);
    }

    private ColumnLayout ensureDetailLayout(Sheet sheet, int processSlots, int feeSlots, QuoteTemplateService.TemplateLayout templateLayout) {
        ColumnLayout layout = detectColumnLayout(sheet, templateLayout);
        if (layout.processStartCol() >= 0 && layout.processBlockWidth() == LEGACY_PROCESS_BLOCK_WIDTH) {
            convertLegacyProcessBlocks(sheet, layout, templateLayout);
            layout = detectColumnLayout(sheet, templateLayout);
        }
        if (layout.baseCostCol() < 0) {
            int insertAt = findFirstHeader(sheet.getRow(templateLayout.headerRowIndex()), List.of("含税单价", "含税总价", "交货周期", "备注"));
            if (insertAt < 0) {
                insertAt = maxColumn(sheet) + 1;
            }
            insertColumnsLike(sheet, insertAt, 2);
            Row headerRow = getOrCreateRow(sheet, templateLayout.headerRowIndex());
            setCell(headerRow, insertAt, "基础成本");
            setCell(headerRow, insertAt + 1, "百分比费用合计");
            layout = detectColumnLayout(sheet, templateLayout);
        }

        if (layout.feeStartCol() < 0 || layout.feeSlots() <= 0) {
            int insertAt = layout.baseCostCol() >= 0
                    ? layout.baseCostCol()
                    : firstPositive(findFirstHeader(sheet.getRow(templateLayout.headerRowIndex()), List.of("含税单价", "含税总价", "交货周期", "备注")), maxColumn(sheet) + 1);
            insertColumnsLike(sheet, insertAt, feeSlots * FEE_BLOCK_WIDTH);
            writeFeeHeaders(sheet, insertAt, feeSlots, List.of(), templateLayout);
            layout = detectColumnLayout(sheet, templateLayout);
        }

        if (layout.processStartCol() >= 0 && layout.processSlots() <= 0) {
            int inferredSlots = inferProcessSlots(sheet, layout, templateLayout);
            writeProcessHeaders(sheet, layout.processStartCol(), Math.max(inferredSlots, 1), List.of(),
                    layout.processBlockWidth(), templateLayout);
            layout = detectColumnLayout(sheet, templateLayout);
        }

        if (layout.processStartCol() < 0 || layout.processSlots() <= 0) {
            int insertAt = firstPositive(layout.feeStartCol(), layout.baseCostCol(),
                    findFirstHeader(sheet.getRow(templateLayout.headerRowIndex()), List.of("含税单价", "含税总价", "交货周期", "备注")),
                    maxColumn(sheet) + 1);
            insertColumnsLike(sheet, insertAt, processSlots * PROCESS_BLOCK_WIDTH);
            writeProcessHeaders(sheet, insertAt, processSlots, List.of(), PROCESS_BLOCK_WIDTH, templateLayout);
            layout = detectColumnLayout(sheet, templateLayout);
        }

        return layout;
    }

    private void convertLegacyProcessBlocks(Sheet sheet, ColumnLayout layout, QuoteTemplateService.TemplateLayout templateLayout) {
        int slots = Math.max(layout.processSlots(), 1);
        for (int i = slots - 1; i >= 0; i--) {
            int insertAt = layout.processStartCol() + i * LEGACY_PROCESS_BLOCK_WIDTH + LEGACY_PROCESS_BLOCK_WIDTH;
            insertColumns(sheet, insertAt, PROCESS_BLOCK_WIDTH - LEGACY_PROCESS_BLOCK_WIDTH, insertAt - 1, 1);
        }
        writeProcessHeaders(sheet, layout.processStartCol(), slots, List.of(), PROCESS_BLOCK_WIDTH, templateLayout);
    }

    private int inferProcessSlots(Sheet sheet, ColumnLayout layout, QuoteTemplateService.TemplateLayout templateLayout) {
        Row headerRow = sheet.getRow(templateLayout.headerRowIndex());
        if (headerRow == null || layout.processStartCol() < 0) {
            return 1;
        }
        int boundary = firstPositive(layout.feeStartCol(), layout.baseCostCol(),
                findFirstHeader(headerRow, List.of("含税单价", "含税总价", "交货周期", "备注")),
                maxColumn(sheet) + 1);
        int slots = 0;
        for (int col = layout.processStartCol(); col < boundary; col += layout.processBlockWidth()) {
            if (!matchesProcessBlock(headerRow, col, layout.processBlockWidth())) {
                break;
            }
            slots++;
        }
        return Math.max(slots, 1);
    }

    private int findFirstHeader(Row row, List<String> headers) {
        int result = -1;
        for (String header : headers) {
            int col = findHeader(row, header, 0);
            if (col >= 0) {
                result = result < 0 ? col : Math.min(result, col);
            }
        }
        return result;
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value >= 0) {
                return value;
            }
        }
        return 0;
    }

    private void insertColumnsLike(Sheet sheet, int insertAt, int count) {
        int sourceCol = insertAt > 0 ? insertAt - 1 : Math.min(1, maxColumn(sheet));
        insertColumns(sheet, insertAt, count, sourceCol, 1);
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
        qw.eq("rfq_id", rfqId).orderByAsc("id");
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

    private int findHeader(Row row, String header, int fromCol) {
        String normalizedHeader = normalizeHeader(header);
        if (row == null) {
            return -1;
        }
        for (int col = Math.max(fromCol, 0); col <= row.getLastCellNum(); col++) {
            if (normalizedHeader.equals(normalizeHeader(dataFormatter.formatCellValue(row.getCell(col))))) {
                return col;
            }
        }
        return -1;
    }

    private int findNewProcessStart(Row headerRow) {
        if (headerRow == null) {
            return -1;
        }
        for (int col = 0; col <= headerRow.getLastCellNum(); col++) {
            if (matchesNewProcessBlock(headerRow, col)) {
                return col;
            }
        }
        return -1;
    }

    private int countProcessSlots(Row headerRow, int processStart, int processBlockWidth) {
        int processSlots = 0;
        if (processStart < 0) {
            return processSlots;
        }
        int col = processStart;
        while (matchesProcessBlock(headerRow, col, processBlockWidth)) {
            processSlots++;
            col += processBlockWidth;
        }
        return processSlots;
    }

    private int findHeaderContaining(Row row, String text, int fromCol) {
        String normalized = normalizeHeader(text);
        if (row == null) {
            return -1;
        }
        for (int col = Math.max(fromCol, 0); col <= row.getLastCellNum(); col++) {
            if (normalizeHeader(dataFormatter.formatCellValue(row.getCell(col))).contains(normalized)) {
                return col;
            }
        }
        return -1;
    }

    private boolean matchesProcessBlock(Row row, int startCol, int processBlockWidth) {
        if (processBlockWidth == LEGACY_PROCESS_BLOCK_WIDTH) {
            return matchesOldProcessBlock(row, startCol) || matchesNewProcessBlock(row, startCol);
        }
        return matchesNewProcessBlock(row, startCol);
    }

    private boolean matchesOldProcessBlock(Row row, int startCol) {
        if (row == null || startCol < 0) {
            return false;
        }
        for (int i = 0; i < OLD_PROCESS_HEADERS.size(); i++) {
            String header = normalizeHeader(dataFormatter.formatCellValue(row.getCell(startCol + i)));
            if (!normalizeHeader(OLD_PROCESS_HEADERS.get(i)).equals(header)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesNewProcessBlock(Row row, int startCol) {
        if (row == null || startCol < 0) {
            return false;
        }
        for (int i = 0; i < PROCESS_HEADERS.size(); i++) {
            String header = normalizeHeader(dataFormatter.formatCellValue(row.getCell(startCol + i)));
            if (!normalizeHeader(PROCESS_HEADERS.get(i)).equals(header)) {
                return false;
            }
        }
        return true;
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

    private void copyCellWithinWorkbook(Cell sourceCell, Cell targetCell) {
        if (sourceCell == targetCell) {
            return;
        }
        if (sourceCell.getCellStyle() != null) {
            targetCell.setCellStyle(sourceCell.getCellStyle());
        }
        CellType cellType = sourceCell.getCellType();
        if (cellType == CellType.FORMULA) {
            targetCell.setCellFormula(sourceCell.getCellFormula());
        } else if (cellType == CellType.NUMERIC) {
            targetCell.setCellValue(sourceCell.getNumericCellValue());
        } else if (cellType == CellType.BOOLEAN) {
            targetCell.setCellValue(sourceCell.getBooleanCellValue());
        } else if (cellType == CellType.STRING) {
            targetCell.setCellValue(sourceCell.getStringCellValue());
        } else if (cellType == CellType.ERROR) {
            targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
        } else {
            targetCell.setBlank();
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

    private record ColumnLayout(int processStartCol, int processSlots, int processBlockWidth,
                                int feeStartCol, int feeSlots, int baseCostCol) {
    }

    private record TemplateColumns(Map<String, Integer> headers, int processStartCol, int processSlots,
                                   List<String> processNames, int processBlockWidth,
                                   int feeStartCol, int feeSlots, List<String> feeNames) {
    }
}

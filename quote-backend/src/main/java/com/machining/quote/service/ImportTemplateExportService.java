package com.machining.quote.service;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ImportTemplateExportService {
    private static final List<TemplateField> RFQ_FIELDS = List.of(
            required("图号", "PN-001", "用于识别零件和匹配图纸，不能为空。"),
            optional("物料描述", "连接板", "零件描述，导入后进入零件基础信息。"),
            optional("材料名称", "6061-T6", "客户提供的材料名称，后续工程可调整。"),
            optional("表面处理", "阳极氧化", "表面处理要求。"),
            optional("版本", "A", "为空时按空版本导入，后续可在提单前维护。"),
            required("数量", "100", "必须为整数数量。"),
            optional("单位", "PCS", "计量单位。"),
            optional("交货周期", "2026-03-08", "支持日期、7天、Excel 日期序列号等格式。"),
            optional("备注", "客户备注", "补充说明。")
    );

    private static final List<TemplateField> SALES_ORDER_FIELDS = List.of(
            required("客户编码", "C0001", "U9 客户编码。"),
            required("单据类型编码", "SO01", "U9 销售订单单据类型编码。"),
            required("客户订单号", "PO-20260724-001", "同一客户订单号的多行会聚合为一张 U9 销售订单。"),
            required("业务日期", "2026-07-24", "销售订单业务日期。"),
            required("U9料号", "ITEM-001", "U9 料品编码。"),
            required("客户料号", "C-PN-001", "客户侧料号。"),
            required("客户品名", "连接板", "客户侧品名。"),
            required("数量", "100", "销售数量，必须为数字。"),
            required("销售单位", "PCS", "U9 销售单位。"),
            required("计价单位", "PCS", "U9 计价单位。"),
            required("单价", "12.50", "销售单价，必须为数字。"),
            required("交期", "2026-08-15", "计划行交期。"),
            optional("业务员编码", "S001", "U9 业务员。"),
            optional("部门编码", "D001", "U9 销售部门。"),
            optional("交易币编码", "CNY", "交易币编码。"),
            optional("核币编码", "CNY", "核币编码。"),
            optional("收货位置编码", "SHIP01", "收货位置，同时用于行和计划行。"),
            optional("立账位置编码", "BILL01", "立账位置。"),
            optional("付款客户编码", "C0001", "付款客户编码。"),
            optional("付款位置编码", "PAY01", "付款位置。"),
            optional("备注", "同步备注", "订单头、计划行备注。"),
            optional("料品版本", "A", "U9 料品版本。"),
            optional("项目编码", "PRJ001", "U9 项目编码。"),
            optional("任务编码", "TASK001", "U9 任务编码。"),
            optional("税组合编码", "TAX13", "U9 税组合。"),
            optional("存储地点编码", "WH01", "U9 存储地点。")
    );

    public void exportRfqImportTemplate(HttpServletResponse response) throws IOException {
        exportTemplate(response, "客户询价表导入模板", "询价单导入", RFQ_FIELDS);
    }

    public void exportSalesOrderImportTemplate(HttpServletResponse response) throws IOException {
        exportTemplate(response, "销售订单导入模板", "销售订单导入", SALES_ORDER_FIELDS);
    }

    private void exportTemplate(HttpServletResponse response, String fileName, String dataSheetName,
                                List<TemplateField> fields) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle requiredStyle = headerStyle(workbook, IndexedColors.LIGHT_ORANGE);
            CellStyle optionalStyle = headerStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE);
            CellStyle helpHeaderStyle = helpHeaderStyle(workbook);
            CellStyle helpCellStyle = helpCellStyle(workbook);

            Sheet dataSheet = workbook.createSheet(dataSheetName);
            writeDataHeader(dataSheet, fields, requiredStyle, optionalStyle);

            Sheet helpSheet = workbook.createSheet("字段说明");
            writeHelpSheet(helpSheet, fields, helpHeaderStyle, helpCellStyle);

            write(response, workbook, fileName);
        }
    }

    private void writeDataHeader(Sheet sheet, List<TemplateField> fields, CellStyle requiredStyle, CellStyle optionalStyle) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(28);
        for (int i = 0; i < fields.size(); i++) {
            TemplateField field = fields.get(i);
            Cell cell = header.createCell(i);
            cell.setCellValue(field.name());
            cell.setCellStyle(field.required() ? requiredStyle : optionalStyle);
            sheet.setColumnWidth(i, Math.max(14, Math.min(28, field.name().length() * 2 + 10)) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private void writeHelpSheet(Sheet sheet, List<TemplateField> fields, CellStyle headerStyle, CellStyle cellStyle) {
        Row header = sheet.createRow(0);
        List<String> titles = List.of("字段名", "属性", "示例", "说明");
        for (int i = 0; i < titles.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(titles.get(i));
            cell.setCellStyle(headerStyle);
        }

        for (int rowIndex = 0; rowIndex < fields.size(); rowIndex++) {
            TemplateField field = fields.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            List<String> values = List.of(
                    field.name(),
                    field.required() ? "必填" : "选填",
                    field.example(),
                    field.description()
            );
            for (int col = 0; col < values.size(); col++) {
                Cell cell = row.createCell(col);
                cell.setCellValue(values.get(col));
                cell.setCellStyle(cellStyle);
            }
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        sheet.setColumnWidth(3, 56 * 256);
    }

    private CellStyle headerStyle(Workbook workbook, IndexedColors fillColor) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(fillColor.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorders(style);

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle helpHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorders(style);

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle helpCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setThinBorders(style);
        return style;
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private void write(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName + ".xlsx");
        workbook.write(response.getOutputStream());
    }

    private static TemplateField required(String name, String example, String description) {
        return new TemplateField(name, true, example, description);
    }

    private static TemplateField optional(String name, String example, String description) {
        return new TemplateField(name, false, example, description);
    }

    private record TemplateField(String name, boolean required, String example, String description) {
    }
}

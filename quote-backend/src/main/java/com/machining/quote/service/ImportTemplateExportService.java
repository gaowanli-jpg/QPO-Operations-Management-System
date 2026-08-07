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
            optional("序号", "1", "市场原始行号，用于导入结果定位。"),
            optional("下单日期", "2026-07-24", "零件级下单日期；为空时继承页面订单默认信息。"),
            optional("客户订单号", "PO-20260724-001", "零件级客户订单号；为空时继承页面订单默认信息。"),
            optional("客户项目号", "CP-001", "零件级客户项目号；为空时继承页面订单默认信息。"),
            optional("客户下单抬头", "GSQ1", "零件级客户下单抬头；为空时继承页面订单默认信息。"),
            optional("订单类型", "正式订单", "零件级订单类型，可填预测订单、备货订单或正式订单；为空时继承页面订单默认信息。"),
            optional("内部项目号", "IP-001", "零件级内部项目号；为空时继承页面订单默认信息。"),
            optional("客户交期", "2026-08-01", "客户要求交期。"),
            optional("内部交期(给生产的交期)", "2026-08-05", "给生产的内部交期快照。"),
            required("图号", "P-001", "零件图号，重复匹配主键之一。"),
            required("订单数量", "100", "订单数量，必须为大于 0 的整数。"),
            optional("描述", "连接板", "零件描述。"),
            optional("材料", "6061-T6", "市场模板材料信息，历史匹配可继续补齐材料快照。"),
            optional("表面处理", "阳极氧化", "零件表面处理要求。"),
            required("版本", "A", "零件版本，重复匹配主键之一。"),
            optional("VAT单价-含税", "", "含税单价，允许为空；U9 同步前必须补齐可用价格。"),
            optional("未税单价(CNY)", "", "未税单价，允许为空；U9 同步前必须补齐可用价格。"),
            optional("（未税）金额", "", "未税总价；有未税单价且本列为空时按未税单价乘订单数量计算。"),
            optional("备注", "首批", "补充说明，导入到零件备注。")
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

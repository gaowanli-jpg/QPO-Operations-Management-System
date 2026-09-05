package com.machining.quote;

import com.machining.quote.service.QuoteTemplateService;
import com.machining.quote.dto.QuoteTemplateConfigReq;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteTemplateServiceTests {
    private static final List<String> VALID_TEMPLATE_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
            "工艺名称", "类型", "工时/金额", "单价", "费用",
            "运输费率(%)", "运输金额", "基础成本",
            "含税单价", "含税总价", "交货周期", "备注"
    );

    @TempDir
    private Path tempDir;

    @Test
    void uploadTemplateRejectsNonXlsxFile() {
        QuoteTemplateService service = service();
        MockMultipartFile file = new MockMultipartFile("templateFile", "quote.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.uploadTemplate(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void uploadTemplateAcceptsMissingDetailHeadersBecauseMiddleTableIsGeneratedBySystem() throws Exception {
        QuoteTemplateService service = service();
        byte[] bytes = workbookBytes(List.of("序号", "图号", "数量"));
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "quote.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
        assertThat(status).containsEntry("fileName", "quote.xlsx");
    }

    @Test
    void templateConfigPersistsHeaderRowNumberAndDerivedLayout() {
        QuoteTemplateService service = service();
        QuoteTemplateConfigReq req = new QuoteTemplateConfigReq();
        req.setHeaderRowNumber(10);

        var status = service.updateConfig(req);

        assertThat(status).containsEntry("headerRowNumber", 10);
        assertThat(status).containsEntry("groupHeaderRowNumber", 9);
        assertThat(status).containsEntry("detailStartRowNumber", 11);
        assertThat(service.templateLayout().headerRowIndex()).isEqualTo(9);
        assertThat(service.templateLayout().groupRowIndex()).isEqualTo(8);
        assertThat(service.templateLayout().detailStartRowIndex()).isEqualTo(10);
    }

    @Test
    void uploadTemplateKeepsConfiguredHeaderRowNumberWithoutRequiringHeadersOnThatRow() throws Exception {
        QuoteTemplateService service = service();
        QuoteTemplateConfigReq req = new QuoteTemplateConfigReq();
        req.setHeaderRowNumber(10);
        service.updateConfig(req);

        MockMultipartFile templateWithoutConfiguredHeaderRow = new MockMultipartFile(
                "templateFile",
                "客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(VALID_TEMPLATE_HEADERS, 8)
        );

        var status = service.uploadTemplate(templateWithoutConfiguredHeaderRow);

        assertThat(status).containsEntry("exists", true);
        assertThat(status).containsEntry("headerRowNumber", 10);
    }

    @Test
    void templateConfigRejectsInvalidHeaderRowNumber() {
        QuoteTemplateService service = service();
        QuoteTemplateConfigReq req = new QuoteTemplateConfigReq();
        req.setHeaderRowNumber(1);

        assertThatThrownBy(() -> service.updateConfig(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("表头校验行必须大于等于 2");
    }

    @Test
    void uploadTemplateSavesValidTemplateAndReturnsStatus() throws Exception {
        QuoteTemplateService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(VALID_TEMPLATE_HEADERS)
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
        assertThat(status).containsEntry("fileName", "客户模板.xlsx");
        assertThat(service.templatePath()).exists();
    }

    @Test
    void uploadTemplateAcceptsFullWidthFeeRateHeader() throws Exception {
        QuoteTemplateService service = service();
        List<String> headers = VALID_TEMPLATE_HEADERS.stream()
                .map(header -> "运输费率(%)".equals(header) ? "运输费率（％）" : header)
                .toList();
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(headers)
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
    }

    @Test
    void uploadTemplateAcceptsProcessNamePromotedDetailHeaders() throws Exception {
        QuoteTemplateService service = service();
        List<String> headers = List.of(
                "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                "类型", "工时/金额", "单价", "费用",
                "运输费率(%)", "运输金额", "基础成本",
                "含税单价", "含税总价", "交货周期", "备注"
        );
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "新版客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(headers)
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
    }

    @Test
    void uploadTemplateAcceptsStandardProcessIdentityHeaders() throws Exception {
        QuoteTemplateService service = service();
        List<String> headers = List.of(
                "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                "大类编码", "大类名称", "具体工艺", "工时", "小时价格", "金额",
                "运输费率(%)", "运输金额", "基础成本",
                "含税单价", "含税总价", "交货周期", "备注"
        );
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "标准工艺客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(headers)
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
    }

    @Test
    void requireTemplateShowsClearMissingTemplateError() {
        QuoteTemplateService service = service();

        assertThatThrownBy(service::requireTemplate)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请先由管理员上传报价单模板");
    }

    private QuoteTemplateService service() {
        QuoteTemplateService service = new QuoteTemplateService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }

    private byte[] workbookBytes(List<String> headers) throws Exception {
        return workbookBytes(headers, 8);
    }

    private byte[] workbookBytes(List<String> headers, int headerRowNumber) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Row row = workbook.createSheet("第2份").createRow(headerRowNumber - 1);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(headers.get(i));
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}

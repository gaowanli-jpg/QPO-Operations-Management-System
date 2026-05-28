package com.machining.quote;

import com.machining.quote.service.QuoteTemplateService;
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
    private static final List<String> REQUIRED_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
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
    void uploadTemplateRejectsMissingRequiredHeaders() throws Exception {
        QuoteTemplateService service = service();
        byte[] bytes = workbookBytes(List.of("序号", "图号", "数量"));
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "quote.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        assertThatThrownBy(() -> service.uploadTemplate(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("缺少必要表头");
    }

    @Test
    void uploadTemplateSavesValidTemplateAndReturnsStatus() throws Exception {
        QuoteTemplateService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "templateFile",
                "客户模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(REQUIRED_HEADERS)
        );

        var status = service.uploadTemplate(file);

        assertThat(status).containsEntry("exists", true);
        assertThat(status).containsEntry("fileName", "客户模板.xlsx");
        assertThat(service.templatePath()).exists();
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
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Row row = workbook.createSheet("第2份").createRow(7);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(headers.get(i));
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}

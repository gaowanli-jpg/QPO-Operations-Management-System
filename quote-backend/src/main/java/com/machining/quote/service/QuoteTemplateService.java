package com.machining.quote.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

@Service
public class QuoteTemplateService {
    private static final Logger log = LoggerFactory.getLogger(QuoteTemplateService.class);
    private static final String TEMPLATE_FILENAME = "quote-template.xlsx";
    private static final String META_FILENAME = "quote-template.properties";
    private static final int HEADER_ROW_INDEX = 7;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
            "含税单价", "含税总价", "交货周期", "备注"
    );
    private static final List<String> DETAIL_REQUIRED_HEADERS = List.of("费率(%)", "基础成本");
    private static final List<String> PROMOTED_PROCESS_HEADERS = List.of("类型", "工时/金额", "单价", "费用");

    @Value("${erp.upload-dir}")
    private String uploadDir;

    @Autowired
    private AuditService auditService;

    public Path templatePath() {
        return templateDir().resolve(TEMPLATE_FILENAME).normalize();
    }

    public Map<String, Object> status() {
        Path templatePath = templatePath();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", Files.exists(templatePath));
        if (!Files.exists(templatePath)) {
            return result;
        }

        Properties properties = loadMeta();
        result.put("fileName", properties.getProperty("fileName", TEMPLATE_FILENAME));
        try {
            result.put("updatedAt", Files.getLastModifiedTime(templatePath).toInstant().toString());
            result.put("size", Files.size(templatePath));
        } catch (IOException ignored) {
            result.put("updatedAt", properties.getProperty("updatedAt", ""));
        }
        return result;
    }

    public Map<String, Object> uploadTemplate(MultipartFile file) {
        String originalFilename = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        long fileSize = file == null ? 0L : file.getSize();
        long startedAt = System.currentTimeMillis();
        log.info("Quote template upload started fileName={} fileSize={}", originalFilename, fileSize);
        if (file == null || file.isEmpty()) {
            log.warn("Quote template validation failed fileName={} fileSize={} reason={}",
                    originalFilename, fileSize, "empty_file");
            throw new RuntimeException("请上传报价单模板文件");
        }
        if (!originalFilename.toLowerCase().endsWith(".xlsx")) {
            log.warn("Quote template validation failed fileName={} fileSize={} reason={}",
                    originalFilename, fileSize, "invalid_extension");
            throw new RuntimeException("报价单模板必须是 .xlsx 文件");
        }

        try {
            byte[] bytes = file.getBytes();
            try {
                validateTemplate(new ByteArrayInputStream(bytes));
            } catch (RuntimeException e) {
                log.warn("Quote template validation failed fileName={} fileSize={} reason={}",
                        originalFilename, fileSize, e.getMessage());
                throw e;
            }
            Files.createDirectories(templateDir());
            Path tmpPath = templateDir().resolve(TEMPLATE_FILENAME + ".tmp");
            Files.write(tmpPath, bytes);
            moveTemplateFile(tmpPath, templatePath());
            saveMeta(originalFilename);
            Map<String, Object> templateStatus = status();
            recordTemplateUploadAudit(originalFilename, templateStatus);
            log.info("Quote template upload completed fileName={} fileSize={} targetPath={} durationMs={}",
                    originalFilename, fileSize, templatePath(), System.currentTimeMillis() - startedAt);
            return templateStatus;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Quote template upload failed fileName={} fileSize={} durationMs={}",
                    originalFilename, fileSize, System.currentTimeMillis() - startedAt, e);
            throw new RuntimeException("报价单模板上传失败：" + e.getMessage(), e);
        }
    }

    public void requireTemplate() {
        if (!Files.exists(templatePath())) {
            throw new RuntimeException("请先由管理员上传报价单模板");
        }
    }

    public Workbook openTemplateWorkbook() throws IOException {
        requireTemplate();
        return WorkbookFactory.create(templatePath().toFile());
    }

    private void validateTemplate(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("报价单模板必须至少包含一个工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            if (headerRow == null) {
                throw new RuntimeException("报价单模板第 8 行必须包含表头");
            }

            DataFormatter formatter = new DataFormatter();
            Set<String> headers = new HashSet<>();
            for (Cell cell : headerRow) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                if (!header.isEmpty()) {
                    headers.add(header);
                }
            }
            List<String> missing = REQUIRED_HEADERS.stream()
                    .filter(header -> !headers.contains(normalizeHeader(header)))
                    .toList();
            if (!missing.isEmpty()) {
                throw new RuntimeException("报价单模板缺少必要表头：" + String.join("、", missing));
            }
            List<String> missingDetailHeaders = DETAIL_REQUIRED_HEADERS.stream()
                    .filter(header -> !hasDetailHeader(headers, header))
                    .toList();
            if (!missingDetailHeaders.isEmpty()) {
                throw new RuntimeException("报价单模板缺少详细报价单必要表头：" + String.join("、", missingDetailHeaders));
            }
            if (!hasDetailHeader(headers, "工艺名称") && !hasPromotedProcessHeaders(headers)) {
                throw new RuntimeException("报价单模板缺少详细报价单必要表头：工艺名称或类型、工时/金额、单价、费用");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("报价单模板无法读取或格式不正确：" + e.getMessage(), e);
        }
    }

    private void saveMeta(String originalFilename) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("fileName", originalFilename);
        properties.setProperty("updatedAt", LocalDateTime.now().toString());
        try (OutputStream outputStream = Files.newOutputStream(metaPath())) {
            properties.store(outputStream, "quote template metadata");
        }
    }

    private void recordTemplateUploadAudit(String originalFilename, Map<String, Object> templateStatus) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>(templateStatus);
        summary.put("fileName", originalFilename);
        auditService.record(
                "QUOTE_TEMPLATE_UPLOAD",
                "quote_template",
                originalFilename,
                null,
                summary,
                "SYSTEM",
                "SUCCESS",
                "QUOTE_TEMPLATE_UPLOAD " + originalFilename,
                null
        );
    }

    private void moveTemplateFile(Path tmpPath, Path targetPath) throws IOException {
        try {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Properties loadMeta() {
        Properties properties = new Properties();
        Path metaPath = metaPath();
        if (!Files.exists(metaPath)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(metaPath)) {
            properties.load(inputStream);
        } catch (IOException ignored) {
            return new Properties();
        }
        return properties;
    }

    private Path templateDir() {
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("templates").normalize();
    }

    private Path metaPath() {
        return templateDir().resolve(META_FILENAME).normalize();
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

    private boolean hasDetailHeader(Set<String> headers, String requiredHeader) {
        String normalized = normalizeHeader(requiredHeader);
        if ("费率(%)".equals(requiredHeader)) {
            return headers.stream().anyMatch(header -> header.contains(normalized));
        }
        return headers.contains(normalized);
    }

    private boolean hasPromotedProcessHeaders(Set<String> headers) {
        return PROMOTED_PROCESS_HEADERS.stream()
                .allMatch(header -> headers.contains(normalizeHeader(header)));
    }
}

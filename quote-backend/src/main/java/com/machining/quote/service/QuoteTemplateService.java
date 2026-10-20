package com.machining.quote.service;

import com.machining.quote.dto.QuoteTemplateConfigReq;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.util.Map;
import java.util.Properties;

@Service
public class QuoteTemplateService {
    private static final Logger log = LoggerFactory.getLogger(QuoteTemplateService.class);
    private static final String TEMPLATE_FILENAME = "quote-template.xlsx";
    private static final String META_FILENAME = "quote-template.properties";
    private static final String HEADER_ROW_NUMBER_KEY = "headerRowNumber";
    private static final int DEFAULT_HEADER_ROW_NUMBER = 8;

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
        TemplateLayout layout = templateLayout();
        result.put("headerRowNumber", layout.headerRowNumber());
        result.put("groupHeaderRowNumber", layout.headerRowNumber() - 1);
        result.put("detailStartRowNumber", layout.headerRowNumber() + 1);
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

    public Map<String, Object> updateConfig(QuoteTemplateConfigReq req) {
        int headerRowNumber = normalizeHeaderRowNumber(req == null ? null : req.getHeaderRowNumber());
        try {
            Files.createDirectories(templateDir());
            Properties properties = loadMeta();
            properties.setProperty(HEADER_ROW_NUMBER_KEY, String.valueOf(headerRowNumber));
            properties.setProperty("configUpdatedAt", LocalDateTime.now().toString());
            saveProperties(properties);
            Map<String, Object> templateStatus = status();
            recordTemplateConfigAudit(headerRowNumber, templateStatus);
            log.info("Quote template config updated headerRowNumber={} detailStartRowNumber={}",
                    headerRowNumber, templateStatus.get("detailStartRowNumber"));
            return templateStatus;
        } catch (IOException e) {
            log.error("Quote template config update failed headerRowNumber={}", headerRowNumber, e);
            throw new RuntimeException("报价单模板配置保存失败：" + e.getMessage(), e);
        }
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

    public TemplateLayout templateLayout() {
        return layoutFor(headerRowNumber());
    }

    private void validateTemplate(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("报价单模板必须至少包含一个工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("报价单模板必须至少包含一个工作表");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("报价单模板无法读取或格式不正确：" + e.getMessage(), e);
        }
    }

    private void saveMeta(String originalFilename) throws IOException {
        Properties properties = loadMeta();
        properties.setProperty("fileName", originalFilename);
        properties.setProperty("updatedAt", LocalDateTime.now().toString());
        properties.putIfAbsent(HEADER_ROW_NUMBER_KEY, String.valueOf(DEFAULT_HEADER_ROW_NUMBER));
        saveProperties(properties);
    }

    private void saveProperties(Properties properties) throws IOException {
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

    private void recordTemplateConfigAudit(int headerRowNumber, Map<String, Object> templateStatus) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>(templateStatus);
        summary.put("headerRowNumber", headerRowNumber);
        auditService.record(
                "QUOTE_TEMPLATE_CONFIG_UPDATE",
                "quote_template",
                "headerRowNumber",
                null,
                summary,
                "SYSTEM",
                "SUCCESS",
                "QUOTE_TEMPLATE_CONFIG_UPDATE headerRowNumber=" + headerRowNumber,
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

    private int headerRowNumber() {
        return normalizeHeaderRowNumber(loadMeta().getProperty(HEADER_ROW_NUMBER_KEY));
    }

    private int normalizeHeaderRowNumber(Object rawValue) {
        int value;
        if (rawValue == null) {
            value = DEFAULT_HEADER_ROW_NUMBER;
        } else {
            try {
                value = Integer.parseInt(rawValue.toString().trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("表头校验行必须是数字");
            }
        }
        if (value < 2) {
            throw new RuntimeException("表头校验行必须大于等于 2");
        }
        return value;
    }

    private TemplateLayout layoutFor(int headerRowNumber) {
        return new TemplateLayout(headerRowNumber, headerRowNumber - 2, headerRowNumber - 1, headerRowNumber);
    }

    private Path templateDir() {
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("templates").normalize();
    }

    private Path metaPath() {
        return templateDir().resolve(META_FILENAME).normalize();
    }

    public record TemplateLayout(int headerRowNumber, int groupRowIndex, int headerRowIndex, int detailStartRowIndex) {
    }
}

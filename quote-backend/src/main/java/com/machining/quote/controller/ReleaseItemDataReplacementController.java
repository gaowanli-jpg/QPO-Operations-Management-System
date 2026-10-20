package com.machining.quote.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.ReleaseItemDataReplacementConfirmDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementPreviewDTO;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/market/release/item-data-replacement")
public class ReleaseItemDataReplacementController {
    private final ReleaseItemDataReplacementService replacementService;
    private final ObjectMapper objectMapper;

    public ReleaseItemDataReplacementController(
            ReleaseItemDataReplacementService replacementService,
            ObjectMapper objectMapper) {
        this.replacementService = replacementService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:release:create")
    public ReleaseItemDataReplacementPreviewDTO preview(
            @RequestParam("sources") String sources,
            @RequestParam("excelFile") MultipartFile excelFile) {
        List<ReleaseOrderSource> parsedSources = parseSources(sources);
        validateExcelFile(excelFile);
        try (InputStream inputStream = excelFile.getInputStream()) {
            return replacementService.preview(parsedSources, inputStream);
        } catch (IOException e) {
            throw new RuntimeException("读取议价提单表格数据替换文件失败", e);
        }
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:release:create")
    public ReleaseItemDataReplacementConfirmDTO confirm(
            @RequestParam("sources") String sources,
            @RequestParam("previewFingerprint") String previewFingerprint,
            @RequestParam("excelFile") MultipartFile excelFile) {
        if (previewFingerprint == null || previewFingerprint.isBlank()) {
            throw new IllegalArgumentException("预览指纹不能为空");
        }
        List<ReleaseOrderSource> parsedSources = parseSources(sources);
        validateExcelFile(excelFile);
        try (InputStream inputStream = excelFile.getInputStream()) {
            return replacementService.confirm(parsedSources, inputStream, previewFingerprint.trim());
        } catch (IOException e) {
            throw new RuntimeException("读取议价提单表格数据替换文件失败", e);
        }
    }

    private List<ReleaseOrderSource> parseSources(String sources) {
        if (sources == null || sources.isBlank()) {
            throw new IllegalArgumentException("询价单来源参数不能为空");
        }
        try {
            return objectMapper.readValue(sources, new TypeReference<List<ReleaseOrderSource>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("询价单来源参数格式不正确", e);
        }
    }

    private void validateExcelFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("表格数据替换文件不能为空");
        }
        String fileName = excelFile.getOriginalFilename();
        String lowerName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException("表格数据替换仅支持 .xlsx 或 .xls 文件");
        }
    }
}

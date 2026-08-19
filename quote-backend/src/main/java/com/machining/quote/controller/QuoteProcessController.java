package com.machining.quote.controller;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeBatchUpdateReq;
import com.machining.quote.dto.QuoteLeadTimeReplacementConfirmDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementPreviewDTO;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeShiftReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.service.PartSelectionImportService;
import com.machining.quote.service.QuoteProcessService;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/market/quote-process")
public class QuoteProcessController {
    @Autowired
    private QuoteProcessService quoteProcessService;
    @Autowired
    private PartSelectionImportService partSelectionImportService;

    @GetMapping("/{rfqId}")
    @RequirePermission("market:quote-process:view")
    public Map<String, Object> getQuoteProcess(@PathVariable Integer rfqId) {
        return quoteProcessService.getQuoteProcess(rfqId);
    }

    @GetMapping("/{rfqId}/summary")
    @RequirePermission("market:quote-process:view")
    public Map<String, Object> getQuoteProcessSummary(@PathVariable Integer rfqId) {
        return quoteProcessService.getQuoteProcessSummary(rfqId);
    }

    @GetMapping("/{rfqId}/items")
    @RequirePermission("market:quote-process:view")
    public PageResult<Map<String, Object>> listQuoteProcessItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "partNo", required = false) String partNo,
            @RequestParam(value = "version", required = false) String version) {
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setKeyword(keyword);
        req.setStatus(status);
        req.setPartNo(partNo);
        req.setVersion(version);
        return quoteProcessService.listQuoteProcessItems(req);
    }

    @PostMapping("/{rfqId}/parse-part-selection")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> parsePartSelection(
            @PathVariable Integer rfqId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        return quoteProcessService.matchPartSelection(rfqId, partSelectionImportService.readPartSelectionRows(excelFile));
    }

    @PostMapping("/lead-time/shift")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> shiftLeadTimes(@RequestBody QuoteLeadTimeShiftReq req) {
        return quoteProcessService.shiftLeadTimes(req);
    }

    @PostMapping("/lead-time/set")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> setLeadTimes(@RequestBody QuoteLeadTimeSetReq req) {
        return quoteProcessService.setLeadTimes(req);
    }

    @PostMapping("/lead-time/update")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> updateLeadTime(@RequestBody QuoteLeadTimeUpdateReq req) {
        return quoteProcessService.updateLeadTime(req);
    }

    @PostMapping("/lead-time/batch-update")
    @RequirePermission("market:quote-process:update-lead-time")
    public Map<String, Object> batchUpdateLeadTimes(@RequestBody QuoteLeadTimeBatchUpdateReq req) {
        return quoteProcessService.batchUpdateLeadTimes(req);
    }

    @PostMapping(value = "/{rfqId}/lead-time-replacement/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:quote-process:update-lead-time")
    public QuoteLeadTimeReplacementPreviewDTO previewLeadTimeReplacement(
            @PathVariable Integer rfqId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        validateExcelFile(excelFile);
        try (InputStream inputStream = excelFile.getInputStream()) {
            return quoteProcessService.previewLeadTimeReplacement(rfqId, inputStream);
        } catch (IOException e) {
            throw new RuntimeException("读取报价处理表格数据替换文件失败", e);
        }
    }

    @PostMapping(value = "/{rfqId}/lead-time-replacement/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("market:quote-process:update-lead-time")
    public QuoteLeadTimeReplacementConfirmDTO confirmLeadTimeReplacement(
            @PathVariable Integer rfqId,
            @RequestParam("previewFingerprint") String previewFingerprint,
            @RequestParam("excelFile") MultipartFile excelFile) {
        validateExcelFile(excelFile);
        try (InputStream inputStream = excelFile.getInputStream()) {
            return quoteProcessService.confirmLeadTimeReplacement(rfqId, inputStream, previewFingerprint);
        } catch (IOException e) {
            throw new RuntimeException("读取报价处理表格数据替换文件失败", e);
        }
    }

    @PostMapping("/{rfqId}/save-quote")
    @RequirePermission("market:quote-process:save-quote")
    public Map<String, Object> saveQuoteSheet(@PathVariable Integer rfqId) {
        return quoteProcessService.saveQuoteSheet(rfqId);
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
